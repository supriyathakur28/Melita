package com.melita.orders.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderApproved(
        UUID orderId,
        String externalReference,
        String decidedBy,
        Instant decidedAt,
        String comment,
        boolean preferredSlotExpired) {
}
