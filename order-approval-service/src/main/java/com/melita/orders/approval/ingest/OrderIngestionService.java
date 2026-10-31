package com.melita.orders.approval.ingest;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderAccepted;
import com.melita.orders.events.OrderApprovalRequested;
import com.melita.orders.events.OrderEvents;
import com.melita.orders.approval.policy.ApprovalPolicy;
import com.melita.orders.approval.order.Address;
import com.melita.orders.approval.order.CustomerDetails;
import com.melita.orders.approval.order.CustomerOrder;
import com.melita.orders.approval.order.CustomerOrderRepository;
import com.melita.orders.approval.order.Installation;
import com.melita.orders.platform.messaging.InboxGuard;
import com.melita.orders.platform.messaging.OutboxWriter;
import com.melita.orders.platform.web.RequestIds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Stores an accepted order. One transaction: inbox row, order, and (if held) the approval-requested event. */
@Service
@Slf4j
public class OrderIngestionService {

    private final InboxGuard inbox;
    private final CustomerOrderRepository orders;
    private final ApprovalPolicy approvalPolicy;
    private final OutboxWriter outbox;
    private final Clock clock;

    public OrderIngestionService(final InboxGuard inbox, final CustomerOrderRepository orders,
                                 final ApprovalPolicy approvalPolicy, final OutboxWriter outbox, final Clock clock) {
        this.inbox = inbox;
        this.orders = orders;
        this.approvalPolicy = approvalPolicy;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void ingest(final EventEnvelope<OrderAccepted> event) {
        final OrderAccepted accepted = event.data();
        if (!inbox.firstDelivery(event.eventId().toString()) || orders.existsById(accepted.orderId())) {
            log.info("Duplicate delivery of event {} for order {}, skipped", event.eventId(), accepted.orderId());
            return;
        }

        final Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        final List<String> reasons = approvalPolicy.reasonsFor(accepted.items());
        final CustomerOrder order = CustomerOrder.receive(accepted.orderId(), accepted.partnerId(),
                accepted.externalReference(), customer(accepted.customer()), installation(accepted.installation()),
                accepted.items().stream()
                        .map(i -> new CustomerOrder.NewItem(i.product(), i.packageCode(), i.packageName())).toList(),
                reasons, now);
        orders.save(order);

        if (order.isApprovalRequired()) {
            outbox.append(EventEnvelope.of(OrderEvents.ORDER_APPROVAL_REQUESTED, RequestIds.current(), now,
                    new OrderApprovalRequested(order.getId(), order.getExternalReference(), reasons, accepted.items())));
        }
        log.info("Stored order {} as {}", order.getId(), order.getStatus());
    }

    private static CustomerDetails customer(final OrderAccepted.Customer c) {
        return new CustomerDetails(c.firstName(), c.lastName(), c.dateOfBirth(), c.email(), c.mobileNumber(),
                c.identityDocument().type(), c.identityDocument().number(), address(c.address()));
    }

    private static Installation installation(final OrderAccepted.Installation i) {
        return i == null ? null : new Installation(i.preferredDate(), i.timeSlot(), address(i.address()));
    }

    private static Address address(final OrderAccepted.Address a) {
        return new Address(a.line1(), a.line2(), a.locality(), a.postcode());
    }
}
