package com.melita.orders.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** {@code lastError} is a short code and message, never a raw response body. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderFulfilmentFailed(
        UUID orderId,
        String externalReference,
        int attempts,
        String lastError) {
}
