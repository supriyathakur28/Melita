package com.melita.orders.management.order;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle. The allowed transitions are listed here and nowhere else; {@link CustomerOrder#transitionTo}
 * refuses anything not in this table.
 */
public enum OrderStatus {
    PENDING_APPROVAL,
    READY_FOR_FULFILMENT,
    REJECTED,
    SUBMITTED,
    FULFILMENT_FAILED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDING_APPROVAL, EnumSet.of(READY_FOR_FULFILMENT, REJECTED),
            READY_FOR_FULFILMENT, EnumSet.of(SUBMITTED, FULFILMENT_FAILED),
            REJECTED, EnumSet.noneOf(OrderStatus.class),
            SUBMITTED, EnumSet.noneOf(OrderStatus.class),
            FULFILMENT_FAILED, EnumSet.noneOf(OrderStatus.class));

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
