package com.melita.orders.intake.order;

import com.melita.orders.intake.order.api.OrderReceipt;

/** {@code replayed} is true when an earlier request with the same Idempotency-Key produced this receipt. */
public record PlacementResult(OrderReceipt receipt, boolean replayed) {
}
