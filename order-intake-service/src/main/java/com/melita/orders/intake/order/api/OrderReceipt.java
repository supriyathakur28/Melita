package com.melita.orders.intake.order.api;

import java.time.Instant;
import java.util.UUID;

/** What the partner gets back. In this release the status is always ACCEPTED (see the design doc). */
public record OrderReceipt(UUID orderId, String externalReference, String status, Instant acceptedAt) {
}
