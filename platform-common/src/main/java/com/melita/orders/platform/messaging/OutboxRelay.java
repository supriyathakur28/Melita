package com.melita.orders.platform.messaging;

import com.melita.orders.events.OrderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Transactional outbox, read side: publishes unpublished rows to RabbitMQ.
 * <ul>
 *   <li>Runs on one instance at a time ({@link NamedLock}).</li>
 *   <li>No database transaction is open while talking to the broker.</li>
 *   <li>A row is marked published only after the broker confirms it. Its payload is cleared at the same time,
 *       so personal data does not linger in the outbox. An event that no queue receives (e.g. nobody has
 *       bound {@code order.submitted} yet) is still published: it is logged as a warning, not retried forever,
 *       which would eventually block the relay.</li>
 *   <li>A row whose confirm does not arrive is retried on the next run. That can produce a duplicate, which
 *       consumers absorb because they are idempotent.</li>
 * </ul>
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String LOCK_NAME = "outbox-relay";
    private static final int BATCH_SIZE = 100;
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbitTemplate;
    private final NamedLock namedLock;
    private final Clock clock;

    public OutboxRelay(JdbcTemplate jdbc, RabbitTemplate rabbitTemplate, NamedLock namedLock, Clock clock) {
        this.jdbc = jdbc;
        this.rabbitTemplate = rabbitTemplate;
        this.namedLock = namedLock;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:500}")
    public void run() {
        namedLock.runIfAvailable(LOCK_NAME, this::publishPending);
    }

    /** Publishes one batch. Package-visible for tests; production calls go through {@link #run()}. */
    int publishPending() {
        List<OutboxRow> rows = jdbc.query("""
                        select id, event_id, event_type, routing_key, correlation_id, payload
                        from outbox_event where published_at is null order by id limit ?
                        """,
                (rs, i) -> new OutboxRow(rs.getLong("id"), rs.getString("event_id"), rs.getString("event_type"),
                        rs.getString("routing_key"), rs.getString("correlation_id"), rs.getString("payload")),
                BATCH_SIZE);
        if (rows.isEmpty()) {
            return 0;
        }

        List<Sent> sent = rows.stream().map(this::send).toList();
        List<Long> confirmed = new ArrayList<>();
        for (Sent s : sent) {
            if (isConfirmed(s)) {
                confirmed.add(s.row().id());
            }
        }

        Timestamp now = Timestamp.from(clock.instant());
        jdbc.batchUpdate("update outbox_event set published_at = ?, payload = null where id = ?",
                confirmed, confirmed.size(), (ps, id) -> {
                    ps.setTimestamp(1, now);
                    ps.setLong(2, id);
                });
        return confirmed.size();
    }

    /** Housekeeping: published rows are only needed for troubleshooting. */
    @Scheduled(cron = "${outbox.purge.cron:0 15 3 * * *}")
    public void purgePublished() {
        Timestamp cutoff = Timestamp.from(clock.instant().minus(Duration.ofDays(7)));
        int deleted = jdbc.update("delete from outbox_event where published_at < ?", cutoff);
        log.info("Purged {} published outbox rows", deleted);
    }

    public long pendingCount() {
        Long count = jdbc.queryForObject("select count(*) from outbox_event where published_at is null", Long.class);
        return count == null ? 0 : count;
    }

    private Sent send(OutboxRow row) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setMessageId(row.eventId());
        properties.setType(row.eventType());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        if (row.correlationId() != null) {
            properties.setHeader(OrderEvents.CORRELATION_HEADER, row.correlationId());
        }
        Message message = MessageBuilder.withBody(row.payload().getBytes(StandardCharsets.UTF_8))
                .andProperties(properties).build();
        CorrelationData correlation = new CorrelationData(row.eventId());
        rabbitTemplate.send(OrderEvents.EXCHANGE, row.routingKey(), message, correlation);
        return new Sent(row, correlation);
    }

    private static boolean isConfirmed(Sent sent) {
        try {
            CorrelationData.Confirm confirm = sent.correlation().getFuture()
                    .get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                log.warn("Broker nacked event {}: {}", sent.row().eventId(), confirm.reason());
                return false;
            }
            if (sent.correlation().getReturned() != null) {
                log.warn("Event {} ({}) was accepted but no queue is bound for it", sent.row().eventId(),
                        sent.row().routingKey());
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("No confirm for event {} within {}: {}", sent.row().eventId(), CONFIRM_TIMEOUT, e.toString());
            return false;
        }
    }

    private record OutboxRow(long id, String eventId, String eventType, String routingKey, String correlationId,
                             String payload) {
    }

    private record Sent(OutboxRow row, CorrelationData correlation) {
    }
}
