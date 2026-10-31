package com.melita.orders.platform.messaging;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = MessagingTestApplication.class)
@ActiveProfiles("messaging-it")
@Testcontainers
class MessagingSupportIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management");

    @Autowired
    OutboxWriter outboxWriter;
    @Autowired
    OutboxRelay outboxRelay;
    @Autowired
    InboxGuard inboxGuard;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DataSource dataSource;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    MessagingTestApplication.TestListener listener;
    @Autowired
    MessagingTestApplication.RecordingFailureCallback failureCallback;

    @BeforeEach
    void clean() {
        jdbc.update("delete from outbox_event");
        jdbc.update("delete from processed_message");
        while (rabbitTemplate.receive(MessagingTestApplication.RELAY_QUEUE) != null) {
            // drain
        }
    }

    /** No ordering is promised (docs/events.md): with publisher confirms, a batch may go out on several channels. */
    @Test
    void relayPublishesEveryRowMarksItAndClearsPayload() {
        final List<EventEnvelope<Map<String, String>>> events = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            events.add(EventEnvelope.of(OrderEvents.ORDER_ACCEPTED, "req-" + i, Instant.now(), Map.of("n", "" + i)));
        }
        tx.executeWithoutResult(status -> events.forEach(outboxWriter::append));

        assertThat(outboxRelay.publishPending()).isEqualTo(3);

        final Map<String, Message> receivedById = new HashMap<>();
        for (int i = 0; i < events.size(); i++) {
            final Message received = rabbitTemplate.receive(MessagingTestApplication.RELAY_QUEUE, 5_000);
            assertThat(received).isNotNull();
            receivedById.put(received.getMessageProperties().getMessageId(), received);
        }
        for (final EventEnvelope<Map<String, String>> event : events) {
            final Message received = receivedById.get(event.eventId().toString());
            assertThat(received).as("event %s", event.eventId()).isNotNull();
            final MessageProperties properties = received.getMessageProperties();
            assertThat(properties.getMessageId()).isEqualTo(event.eventId().toString());
            assertThat(properties.getType()).isEqualTo(OrderEvents.ORDER_ACCEPTED);
            assertThat((String) properties.getHeader(OrderEvents.CORRELATION_HEADER)).isEqualTo(event.correlationId());
            assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).contains(event.eventId().toString());
        }
        assertThat(outboxRelay.pendingCount()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where payload is not null", Long.class))
                .isZero();
    }

    @Test
    void eventThatNoQueueReceivesIsStillMarkedPublishedSoItCannotBlockTheRelay() {
        tx.executeWithoutResult(status -> outboxWriter.append(
                EventEnvelope.of("nobody.listens", "req-x", Instant.now(), Map.of())));

        assertThat(outboxRelay.publishPending()).isEqualTo(1);
        assertThat(outboxRelay.pendingCount()).isZero();
    }

    @Test
    void outboxWriteRequiresAnEnclosingTransaction() {
        assertThatThrownBy(() -> outboxWriter.append(
                EventEnvelope.of(OrderEvents.ORDER_ACCEPTED, null, Instant.now(), Map.of())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void relaySkipsItsTurnWhileAnotherInstanceHoldsTheLock() throws Exception {
        tx.executeWithoutResult(status -> outboxWriter.append(
                EventEnvelope.of(OrderEvents.ORDER_ACCEPTED, "req", Instant.now(), Map.of())));

        try (Connection otherInstance = dataSource.getConnection()) {
            otherInstance.createStatement().executeQuery("select get_lock('outbox-relay', 0)").close();

            outboxRelay.run();
            assertThat(outboxRelay.pendingCount()).isEqualTo(1);

            otherInstance.createStatement().executeQuery("select release_lock('outbox-relay')").close();
        }

        outboxRelay.run();
        assertThat(outboxRelay.pendingCount()).isZero();
    }

    @Test
    void poisonMessageGoesStraightToTheDeadLetterQueue() {
        send("poison", "req-poison");

        final Message dead = rabbitTemplate.receive(MessagingTestApplication.LISTENER_QUEUE + ".dlq", 10_000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("poison");
        assertThat(listener.invocations.get("poison").get()).isEqualTo(1);
        assertThat(failureCallback.deadLettered).contains("poison");
        assertThat(listener.correlationSeen.get("poison")).isEqualTo("req-poison");
    }

    @Test
    void transientFailureIsRetriedAndSucceedsWithoutDeadLettering() {
        send("flaky-2", "req-flaky");

        await().atMost(Duration.ofSeconds(10)).until(() -> listener.succeeded.contains("flaky-2"));
        assertThat(listener.invocations.get("flaky-2").get()).isEqualTo(3);
        assertThat(failureCallback.deadLettered).doesNotContain("flaky-2");
    }

    @Test
    void persistentFailureIsRetriedThenDeadLettered() {
        send("always-fail", "req-fail");

        final Message dead = rabbitTemplate.receive(MessagingTestApplication.LISTENER_QUEUE + ".dlq", 10_000);
        assertThat(dead).isNotNull();
        assertThat(listener.invocations.get("always-fail").get()).isEqualTo(3);
        assertThat(failureCallback.deadLettered).contains("always-fail");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void deadLetteringNeverLogsTheMessageBody(final CapturedOutput output) {
        final Message message =
                MessageBuilder.withBody("always-fail PII-MARKER-123456M".getBytes(StandardCharsets.UTF_8))
                .setMessageId("msg-pii-test").setContentType("application/json").build();
        rabbitTemplate.send(OrderEvents.EXCHANGE, MessagingTestApplication.LISTENER_ROUTING_KEY, message);

        final Message dead = rabbitTemplate.receive(MessagingTestApplication.LISTENER_QUEUE + ".dlq", 10_000);
        assertThat(dead).isNotNull();
        assertThat(output.getAll()).contains("Dead-lettering message").doesNotContain("PII-MARKER");
    }

    private void send(final String body, final String correlationId) {
        final Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setHeader(OrderEvents.CORRELATION_HEADER, correlationId)
                .setMessageId(body)
                .build();
        rabbitTemplate.send(OrderEvents.EXCHANGE, MessagingTestApplication.LISTENER_ROUTING_KEY, message);
    }
}
