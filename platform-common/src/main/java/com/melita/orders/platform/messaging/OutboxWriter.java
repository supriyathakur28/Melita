package com.melita.orders.platform.messaging;

import com.melita.orders.events.EventEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;

/**
 * Transactional outbox, write side. Called inside the business transaction, so the event row commits or
 * rolls back together with the business change. Nothing is sent to the broker here.
 */
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    public OutboxWriter(final JdbcTemplate jdbc, final JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(final EventEnvelope<?> event) {
        jdbc.update("""
                        insert into outbox_event (event_id, event_type, routing_key, correlation_id, payload, created_at)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                event.eventId().toString(), event.eventType(), event.eventType(), event.correlationId(),
                jsonMapper.writeValueAsString(event), Timestamp.from(event.occurredAt()));
    }
}
