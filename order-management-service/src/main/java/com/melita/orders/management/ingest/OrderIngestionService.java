package com.melita.orders.management.ingest;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderAccepted;
import com.melita.orders.events.OrderApprovalRequested;
import com.melita.orders.events.OrderEvents;
import com.melita.orders.management.approval.ApprovalPolicy;
import com.melita.orders.management.order.Address;
import com.melita.orders.management.order.CustomerDetails;
import com.melita.orders.management.order.CustomerOrder;
import com.melita.orders.management.order.CustomerOrderRepository;
import com.melita.orders.management.order.Installation;
import com.melita.orders.platform.messaging.InboxGuard;
import com.melita.orders.platform.messaging.OutboxWriter;
import com.melita.orders.platform.web.RequestIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Stores an accepted order. One transaction: inbox row, order, and (if held) the approval-requested event. */
@Service
public class OrderIngestionService {

    private static final Logger log = LoggerFactory.getLogger(OrderIngestionService.class);

    private final InboxGuard inbox;
    private final CustomerOrderRepository orders;
    private final ApprovalPolicy approvalPolicy;
    private final OutboxWriter outbox;
    private final Clock clock;

    public OrderIngestionService(InboxGuard inbox, CustomerOrderRepository orders, ApprovalPolicy approvalPolicy,
                                 OutboxWriter outbox, Clock clock) {
        this.inbox = inbox;
        this.orders = orders;
        this.approvalPolicy = approvalPolicy;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void ingest(EventEnvelope<OrderAccepted> event) {
        OrderAccepted accepted = event.data();
        if (!inbox.firstDelivery(event.eventId().toString()) || orders.existsById(accepted.orderId())) {
            log.info("Duplicate delivery of event {} for order {}, skipped", event.eventId(), accepted.orderId());
            return;
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<String> reasons = approvalPolicy.reasonsFor(accepted.items());
        CustomerOrder order = CustomerOrder.receive(accepted.orderId(), accepted.partnerId(),
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

    private static CustomerDetails customer(OrderAccepted.Customer c) {
        return new CustomerDetails(c.firstName(), c.lastName(), c.dateOfBirth(), c.email(), c.mobileNumber(),
                c.identityDocument().type(), c.identityDocument().number(), address(c.address()));
    }

    private static Installation installation(OrderAccepted.Installation i) {
        return i == null ? null : new Installation(i.preferredDate(), i.timeSlot(), address(i.address()));
    }

    private static Address address(OrderAccepted.Address a) {
        return new Address(a.line1(), a.line2(), a.locality(), a.postcode());
    }
}
