package com.melita.orders.fulfilment.api;

import java.time.Instant;
import java.util.UUID;

/** Returned on submission (201, or 200 for a replay) and by GET. */
public record FulfilmentOrderResponse(String fulfilmentReference, UUID orderId, String status, Instant receivedAt) {
}
