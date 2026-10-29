package com.melita.orders.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderRejected(
        UUID orderId,
        String externalReference,
        String decidedBy,
        Instant decidedAt,
        String reason) {
}
