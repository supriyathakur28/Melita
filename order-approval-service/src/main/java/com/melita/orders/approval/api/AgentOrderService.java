package com.melita.orders.approval.api;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderApproved;
import com.melita.orders.events.OrderEvents;
import com.melita.orders.events.OrderRejected;
import com.melita.orders.approval.config.OrdersProperties;
import com.melita.orders.approval.order.CustomerOrder;
import com.melita.orders.approval.order.CustomerOrderRepository;
import com.melita.orders.approval.order.CustomerOrderRepository.OrderSummaryView;
import com.melita.orders.approval.order.OrderStatus;
import com.melita.orders.platform.messaging.OutboxWriter;
import com.melita.orders.platform.web.ApiException;
import com.melita.orders.platform.web.RequestIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Agent actions. A decision and its event commit together (outbox). Two agents deciding the same order at
 * once: the row lock makes the second wait, then the state machine rejects it with 409; {@code @Version} stays
 * as a safety net.
 */
@Service
@PreAuthorize("hasRole('AGENT')")
public class AgentOrderService {

    static final String SLOT_EXPIRED = "PREFERRED_SLOT_EXPIRED";
    private static final Logger log = LoggerFactory.getLogger(AgentOrderService.class);

    private final CustomerOrderRepository orders;
    private final OutboxWriter outbox;
    private final OrdersProperties properties;
    private final Clock clock;

    public AgentOrderService(final CustomerOrderRepository orders, final OutboxWriter outbox,
                             final OrdersProperties properties, final Clock clock) {
        this.orders = orders;
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<OrderSummary> list(final OrderStatus status, final Pageable pageable) {
        final Page<OrderSummaryView> page = status == null
                ? orders.findAllProjectedBy(pageable)
                : orders.findByStatus(status, pageable);
        return page.map(v -> new OrderSummary(v.getId(), v.getPartnerId(), v.getExternalReference(),
                v.getStatus(), v.isApprovalRequired(), v.getReceivedAt()));
    }

    @Transactional(readOnly = true)
    public OrderDetail get(final UUID orderId) {
        final CustomerOrder order = load(orderId);
        return OrderDetail.from(order, warnings(order));
    }

    @Transactional
    public OrderDetail approve(final UUID orderId, final String agent, final String comment) {
        final CustomerOrder order = loadForUpdate(orderId);
        final Instant now = now();
        order.approve(agent, comment, now);
        final boolean slotExpired = slotExpired(order);
        outbox.append(EventEnvelope.of(OrderEvents.ORDER_APPROVED, RequestIds.current(), now,
                new OrderApproved(order.getId(), order.getExternalReference(), agent, now, comment, slotExpired)));
        orders.saveAndFlush(order);
        log.info("Order {} approved by {}", orderId, agent);
        return OrderDetail.from(order, warnings(order));
    }

    @Transactional
    public OrderDetail reject(final UUID orderId, final String agent, final String reason) {
        final CustomerOrder order = loadForUpdate(orderId);
        final Instant now = now();
        order.reject(agent, reason, now);
        outbox.append(EventEnvelope.of(OrderEvents.ORDER_REJECTED, RequestIds.current(), now,
                new OrderRejected(order.getId(), order.getExternalReference(), agent, now, reason)));
        orders.saveAndFlush(order);
        log.info("Order {} rejected by {}", orderId, agent);
        return OrderDetail.from(order, List.of());
    }

    /** Locks the order row for the decision, so concurrent decisions queue instead of deadlocking. */
    private CustomerOrder loadForUpdate(final UUID orderId) {
        return orders.findForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order"));
    }

    private CustomerOrder load(final UUID orderId) {
        return orders.findWithItemsById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
    }

    private List<String> warnings(final CustomerOrder order) {
        final boolean open = order.getStatus() == OrderStatus.PENDING_APPROVAL
                || order.getStatus() == OrderStatus.READY_FOR_FULFILMENT;
        return open && slotExpired(order) ? List.of(SLOT_EXPIRED) : List.of();
    }

    private boolean slotExpired(final CustomerOrder order) {
        return order.getInstallation() != null
                && order.getInstallation().isExpiredOn(LocalDate.now(clock.withZone(properties.zone())));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
