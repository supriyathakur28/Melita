package com.melita.orders.approval.api;

import com.melita.orders.approval.order.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/** One row of the agents' order list: no items, no personal data. */
public record OrderSummary(UUID orderId, String partnerId, String externalReference, OrderStatus status,
                           boolean approvalRequired, Instant receivedAt) {
}
