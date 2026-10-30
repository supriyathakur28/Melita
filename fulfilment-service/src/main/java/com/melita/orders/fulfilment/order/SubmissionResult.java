package com.melita.orders.fulfilment.order;

import com.melita.orders.fulfilment.api.FulfilmentOrderResponse;

/** {@code replayed} is true when an earlier submission with the same Idempotency-Key produced this order. */
public record SubmissionResult(FulfilmentOrderResponse response, boolean replayed) {
}
