package com.melita.orders.intake.order;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderEvents;
import com.melita.orders.intake.catalogue.Catalogue;
import com.melita.orders.intake.catalogue.CatalogueService;
import com.melita.orders.intake.order.api.OrderReceipt;
import com.melita.orders.intake.order.api.PlaceOrderRequest;
import com.melita.orders.platform.idempotency.RequestHasher;
import com.melita.orders.platform.messaging.OutboxWriter;
import com.melita.orders.platform.web.ApiException;
import com.melita.orders.platform.web.FieldViolation;
import com.melita.orders.platform.web.RequestIds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Accepts orders. In one local transaction it writes the receipt, the idempotency record and the
 * {@code order.accepted} outbox row, so "order accepted" and "event will be published" cannot diverge. No
 * remote call happens inside the transaction; validation runs before it.
 */
@Service
@Slf4j
public class OrderIntakeService {

    private static final Duration REPLAY_WINDOW = Duration.ofDays(30);

    private final CatalogueService catalogueService;
    private final OrderRules rules;
    private final RequestHasher hasher;
    private final AcceptedOrderRepository orders;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final OutboxWriter outbox;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public OrderIntakeService(final CatalogueService catalogueService, final OrderRules rules,
                              final RequestHasher hasher, final AcceptedOrderRepository orders,
                              final IdempotencyRecordRepository idempotencyRecords, final OutboxWriter outbox,
                              final PlatformTransactionManager transactionManager, final Clock clock) {
        this.catalogueService = catalogueService;
        this.rules = rules;
        this.hasher = hasher;
        this.orders = orders;
        this.idempotencyRecords = idempotencyRecords;
        this.outbox = outbox;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public PlacementResult place(final String partnerId, final String idempotencyKey, final PlaceOrderRequest request) {
        final String requestHash = hasher.hash(request);
        final var key = new IdempotencyRecord.Key(partnerId, idempotencyKey);

        // A retry of a request that already succeeded is answered from the record, even if the catalogue
        // or the date rules have changed since.
        final Optional<PlacementResult> earlier = replay(key, requestHash);
        if (earlier.isPresent()) {
            return earlier.get();
        }

        final Catalogue catalogue = catalogueService.current();
        final List<FieldViolation> violations = rules.check(request, catalogue);
        if (!violations.isEmpty()) {
            throw ApiException.businessRules(violations);
        }

        try {
            final OrderReceipt receipt =
                    transaction.execute(status -> accept(partnerId, key, requestHash, request, catalogue));
            log.info("Accepted order {} ({}) for partner {}", receipt.orderId(), receipt.externalReference(), partnerId);
            return new PlacementResult(receipt, false);
        } catch (DataIntegrityViolationException duplicate) {
            return resolveDuplicate(key, requestHash, request.externalReference(), duplicate);
        }
    }

    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public OrderReceipt find(final String partnerId, final UUID orderId) {
        return orders.findByIdAndPartnerId(orderId, partnerId)
                .map(OrderIntakeService::receipt)
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    @Scheduled(cron = "${idempotency.purge.cron:0 30 3 * * *}")
    public void purgeExpiredIdempotencyRecords() {
        final Instant cutoff = clock.instant().minus(REPLAY_WINDOW);
        final Integer deleted = transaction.execute(status -> idempotencyRecords.deleteOlderThan(cutoff));
        log.info("Purged {} idempotency records older than {}", deleted, cutoff);
    }

    private OrderReceipt accept(final String partnerId, final IdempotencyRecord.Key key, final String requestHash,
                                final PlaceOrderRequest request, final Catalogue catalogue) {
        // DATETIME(6) stores microseconds: truncate so the response and a later replay are identical.
        final Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        final UUID orderId = UUID.randomUUID();
        final AcceptedOrder order = orders.saveAndFlush(
                new AcceptedOrder(orderId, partnerId, request.externalReference(), now));
        idempotencyRecords.saveAndFlush(new IdempotencyRecord(key, requestHash, orderId, now));
        outbox.append(EventEnvelope.of(OrderEvents.ORDER_ACCEPTED, RequestIds.current(), now,
                OrderAcceptedMapper.toEvent(orderId, partnerId, request, catalogue, now)));
        return receipt(order);
    }

    /**
     * A unique key was violated: a concurrent request committed first. Re-read in a new transaction, checking
     * the idempotency record first, so a retried request is always replayed and never answered with a 409.
     */
    private PlacementResult resolveDuplicate(final IdempotencyRecord.Key key, final String requestHash,
                                             final String externalReference,
                                             final DataIntegrityViolationException duplicate) {
        return replay(key, requestHash)
                .or(() -> orders.findByPartnerIdAndExternalReference(key.partnerId(), externalReference)
                        .map(existing -> {
                            throw ApiException.conflict("DUPLICATE_EXTERNAL_REFERENCE",
                                    "An order with externalReference '" + externalReference + "' already exists",
                                    Map.of("existingOrderId", existing.getId().toString()));
                        }))
                .orElseThrow(() -> duplicate);
    }

    private Optional<PlacementResult> replay(final IdempotencyRecord.Key key, final String requestHash) {
        return transaction.execute(status -> idempotencyRecords.findById(key).map(record -> {
            if (!record.getRequestHash().equals(requestHash)) {
                throw ApiException.unprocessable("IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used with a different request body");
            }
            final AcceptedOrder order = orders.findById(record.getOrderId()).orElseThrow();
            return new PlacementResult(receipt(order), true);
        }));
    }

    private static OrderReceipt receipt(final AcceptedOrder order) {
        return new OrderReceipt(order.getId(), order.getExternalReference(), order.getStatus(), order.getAcceptedAt());
    }
}
