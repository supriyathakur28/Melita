package com.melita.orders.fulfilment.order;

import com.melita.orders.fulfilment.api.FulfilmentOrderRequest;
import com.melita.orders.fulfilment.api.FulfilmentOrderResponse;
import com.melita.orders.fulfilment.api.FulfilmentOrderView;
import com.melita.orders.platform.idempotency.RequestHasher;
import com.melita.orders.platform.pii.Masking;
import com.melita.orders.platform.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Creates the final fulfilment order from an approved order.
 * <p>
 * Idempotency is durable: the Idempotency-Key, with a hash of the request, is stored with the order. The same
 * key and body return the original order (also after a restart); the same key with a different body, or the
 * same platform order under a different key, is a 409. Concurrent duplicates are settled by the unique keys:
 * the loser re-reads and replays.
 */
@Service
public class FulfilmentOrderService {

    private static final Logger log = LoggerFactory.getLogger(FulfilmentOrderService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FulfilmentOrderRepository orders;
    private final RequestHasher hasher;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public FulfilmentOrderService(FulfilmentOrderRepository orders, RequestHasher hasher,
                                  PlatformTransactionManager transactionManager, Clock clock) {
        this.orders = orders;
        this.hasher = hasher;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @PreAuthorize("hasAuthority('SCOPE_fulfilment:submit')")
    public SubmissionResult submit(String idempotencyKey, FulfilmentOrderRequest request) {
        String requestHash = hasher.hash(request);
        Optional<SubmissionResult> earlier = replay(idempotencyKey, requestHash);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        try {
            FulfilmentOrder order = transaction.execute(status -> orders.saveAndFlush(create(idempotencyKey, requestHash, request)));
            log.info("Scheduled fulfilment {} for order {}", order.getFulfilmentReference(), order.getOrderId());
            return new SubmissionResult(response(order), false);
        } catch (DataIntegrityViolationException duplicate) {
            return replay(idempotencyKey, requestHash)
                    .or(() -> orders.findByOrderId(request.orderId()).map(existing -> {
                        throw ApiException.conflict("ORDER_ALREADY_SUBMITTED",
                                "Order " + request.orderId() + " was already submitted under another Idempotency-Key",
                                Map.of("fulfilmentReference", existing.getFulfilmentReference()));
                    }))
                    .orElseThrow(() -> duplicate);
        }
    }

    @PreAuthorize("hasAuthority('SCOPE_fulfilment:read')")
    public FulfilmentOrderView find(String fulfilmentReference) {
        return transaction.execute(status -> orders.findWithItemsByFulfilmentReference(fulfilmentReference)
                .map(FulfilmentOrderService::view)
                .orElseThrow(() -> ApiException.notFound("Fulfilment order")));
    }

    private Optional<SubmissionResult> replay(String idempotencyKey, String requestHash) {
        return transaction.execute(status -> orders.findByIdempotencyKey(idempotencyKey).map(existing -> {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used with a different request body");
            }
            return new SubmissionResult(response(existing), true);
        }));
    }

    private FulfilmentOrder create(String idempotencyKey, String requestHash, FulfilmentOrderRequest request) {
        var c = request.customer();
        var customer = new Customer(c.firstName(), c.lastName(), c.dateOfBirth(), c.email(), c.mobileNumber(),
                c.idDocumentType(), c.idDocumentNumber(), address(c.address()));
        Installation installation = request.installation() == null ? null : new Installation(
                request.installation().preferredDate(), request.installation().timeSlot(),
                address(request.installation().address()), request.installation().preferredSlotExpired());
        var items = request.items().stream()
                .map(i -> new FulfilmentOrder.NewItem(i.product(), i.packageCode(), i.packageName()))
                .toList();
        return FulfilmentOrder.create(newReference(), idempotencyKey, requestHash, request.orderId(),
                request.partnerId(), request.externalReference(), customer, installation, items,
                clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    private static Address address(FulfilmentOrderRequest.Address a) {
        return new Address(a.line1(), a.line2(), a.locality(), a.postcode());
    }

    /** {@code FUL-} plus 10 random hex characters: short enough to read out over the phone. */
    private static String newReference() {
        byte[] bytes = new byte[5];
        RANDOM.nextBytes(bytes);
        return "FUL-" + HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private static FulfilmentOrderResponse response(FulfilmentOrder order) {
        return new FulfilmentOrderResponse(order.getFulfilmentReference(), order.getOrderId(), order.getStatus(),
                order.getReceivedAt());
    }

    private static FulfilmentOrderView view(FulfilmentOrder order) {
        Installation i = order.getInstallation();
        return new FulfilmentOrderView(order.getFulfilmentReference(), order.getOrderId(), order.getPartnerId(),
                order.getExternalReference(), order.getStatus(),
                order.getCustomer().firstName() + " " + order.getCustomer().lastName(),
                Masking.mask(order.getCustomer().idDocumentNumber()),
                i == null ? null : new FulfilmentOrderView.Installation(i.preferredDate(), i.timeSlot(),
                        i.address().postcode(), Boolean.TRUE.equals(i.preferredSlotExpired())),
                order.getItems().stream().map(it -> new FulfilmentOrderView.Item(it.getProductCode(),
                        it.getPackageCode(), it.getPackageName())).toList(),
                order.getReceivedAt());
    }
}
