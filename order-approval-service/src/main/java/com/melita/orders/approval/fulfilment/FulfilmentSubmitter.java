package com.melita.orders.approval.fulfilment;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderEvents;
import com.melita.orders.events.OrderFulfilmentFailed;
import com.melita.orders.events.OrderSubmitted;
import com.melita.orders.approval.config.OrdersProperties;
import com.melita.orders.approval.order.CustomerOrder;
import com.melita.orders.approval.order.CustomerOrderRepository;
import com.melita.orders.platform.messaging.NamedLock;
import com.melita.orders.platform.messaging.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Submits READY orders to Fulfilment. Runs on one instance at a time (MySQL named lock). For each order:
 * read it in a short transaction, call Fulfilment with no transaction open, then record the outcome in a
 * second short transaction. Temporary failures are retried with exponential backoff and jitter, up to a limit.
 */
@Component
public class FulfilmentSubmitter {

    private static final Logger log = LoggerFactory.getLogger(FulfilmentSubmitter.class);
    private static final String LOCK_NAME = "fulfilment-submitter";

    private final CustomerOrderRepository orders;
    private final FulfilmentClient client;
    private final OutboxWriter outbox;
    private final NamedLock namedLock;
    private final FulfilmentProperties properties;
    private final OrdersProperties ordersProperties;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    public FulfilmentSubmitter(final CustomerOrderRepository orders, final FulfilmentClient client,
                               final OutboxWriter outbox, final NamedLock namedLock,
                               final FulfilmentProperties properties, final OrdersProperties ordersProperties,
                               final PlatformTransactionManager transactionManager, final Clock clock) {
        this.orders = orders;
        this.client = client;
        this.outbox = outbox;
        this.namedLock = namedLock;
        this.properties = properties;
        this.ordersProperties = ordersProperties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${fulfilment.submitter.interval-ms:2000}")
    public void run() {
        if (!client.isAvailable()) {
            return;
        }
        namedLock.runIfAvailable(LOCK_NAME, this::submitDueOrders);
    }

    void submitDueOrders() {
        final List<UUID> due = readOnly.execute(status -> orders.findDueForSubmission(clock.instant()));
        due.forEach(this::submitIsolated);
    }

    /** One bad order must not block the others. */
    private void submitIsolated(final UUID orderId) {
        try {
            submit(orderId);
        } catch (OptimisticLockingFailureException e) {
            log.info("Order {} changed while submitting; it will be retried on the next run", orderId);
        } catch (RuntimeException e) {
            // Record a failed attempt, so the order backs off and eventually fails, instead of staying first in
            // the queue.
            log.error("Unexpected error submitting order {}", orderId, e);
            recordUnexpected(orderId, e);
        }
    }

    private void recordUnexpected(final UUID orderId, final RuntimeException e) {
        try {
            transaction.executeWithoutResult(status ->
                    record(orderId, new FulfilmentOutcome.RetryLater("unexpected error: " + e.getClass().getSimpleName())));
        } catch (RuntimeException again) {
            log.error("Could not record the failed attempt for order {}", orderId, again);
        }
    }

    private void submit(final UUID orderId) {
        // A write transaction: the first attempt fixes preferredSlotExpired so retries send the same body.
        final FulfilmentRequest request =
                transaction.execute(status -> toRequest(orders.findWithItemsById(orderId).orElseThrow()));
        final FulfilmentOutcome outcome = client.submit(request);
        if (outcome instanceof FulfilmentOutcome.NotAttempted) {
            return;
        }
        transaction.executeWithoutResult(status -> record(orderId, outcome));
    }

    private void record(final UUID orderId, final FulfilmentOutcome outcome) {
        final CustomerOrder order = orders.findForUpdate(orderId).orElseThrow();
        final Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        switch (outcome) {
            case FulfilmentOutcome.Submitted submitted -> {
                order.markSubmitted(submitted.fulfilmentReference(), now);
                outbox.append(EventEnvelope.of(OrderEvents.ORDER_SUBMITTED, null, now, new OrderSubmitted(
                        order.getId(), order.getExternalReference(), submitted.fulfilmentReference(), now)));
                log.info("Order {} submitted to Fulfilment as {}", orderId, submitted.fulfilmentReference());
            }
            case FulfilmentOutcome.RetryLater retry when order.getAttempts() + 1 < properties.maxAttempts() -> {
                final Instant retryAt = now.plus(backoff(order.getAttempts() + 1));
                order.recordFailedAttempt(retry.error(), retryAt, now);
                log.warn("Order {} attempt {} failed ({}); retrying at {}", orderId, order.getAttempts(), retry.error(), retryAt);
            }
            case FulfilmentOutcome.RetryLater retry -> fail(order, "retries exhausted: " + retry.error(), now);
            case FulfilmentOutcome.Rejected rejected -> fail(order, rejected.error(), now);
            case FulfilmentOutcome.NotAttempted notAttempted -> {
                // handled before the transaction
            }
        }
        orders.saveAndFlush(order);
    }

    private void fail(final CustomerOrder order, final String error, final Instant now) {
        order.markFulfilmentFailed(error, now);
        outbox.append(EventEnvelope.of(OrderEvents.ORDER_FULFILMENT_FAILED, null, now, new OrderFulfilmentFailed(
                order.getId(), order.getExternalReference(), order.getAttempts(), error)));
        log.error("Order {} could not be submitted to Fulfilment: {}", order.getId(), error);
    }

    /** Exponential backoff with full jitter: random between half and all of initial * 2^(attempt-1), capped. */
    Duration backoff(final int attempt) {
        final long cap = properties.maxRetryDelay().toMillis();
        final long exponential = Math.min(cap, properties.initialRetryDelay().toMillis() << Math.min(attempt - 1, 20));
        final long jittered = exponential / 2 + ThreadLocalRandom.current().nextLong(exponential / 2 + 1);
        return Duration.ofMillis(jittered);
    }

    private FulfilmentRequest toRequest(final CustomerOrder order) {
        final var c = order.getCustomer();
        final var customer = new FulfilmentRequest.Customer(c.firstName(), c.lastName(), c.dateOfBirth(), c.email(),
                c.mobileNumber(), c.idDocumentType(), c.idDocumentNumber(), address(c.address()));
        FulfilmentRequest.Installation installation = null;
        if (order.getInstallation() != null) {
            final var i = order.getInstallation();
            final LocalDate today = LocalDate.now(clock.withZone(ordersProperties.zone()));
            installation = new FulfilmentRequest.Installation(address(i.address()), i.preferredDate(), i.timeSlot(),
                    order.preferredSlotExpired(today));
        }
        final var items = order.getItems().stream()
                .map(i -> new FulfilmentRequest.Item(i.getProductCode(), i.getPackageCode(), i.getPackageName()))
                .toList();
        return new FulfilmentRequest(order.getId(), order.getExternalReference(), order.getPartnerId(), customer,
                installation, items);
    }

    private static FulfilmentRequest.Address address(final com.melita.orders.approval.order.Address a) {
        return new FulfilmentRequest.Address(a.line1(), a.line2(), a.locality(), a.postcode());
    }
}
