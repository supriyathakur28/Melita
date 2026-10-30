package com.melita.orders.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Wrapper for every event published on {@link OrderEvents#EXCHANGE}.
 * <p>
 * Schema evolution: fields are only ever added within a {@code schemaVersion}; consumers ignore fields they
 * do not know. A breaking change gets a new {@code schemaVersion}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String correlationId,
        T data) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(data, "data");
    }

    public static <T> EventEnvelope<T> of(final String eventType, final String correlationId, final Instant occurredAt,
                                          final T data) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, CURRENT_SCHEMA_VERSION, occurredAt, correlationId, data);
    }
}
