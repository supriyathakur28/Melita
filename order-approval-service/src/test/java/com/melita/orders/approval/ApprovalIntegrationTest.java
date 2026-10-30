package com.melita.orders.approval;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderAccepted;
import com.melita.orders.events.OrderEvents;
import com.melita.orders.platform.messaging.Queues;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The real service against MySQL, RabbitMQ and a fake Fulfilment/token server, shared by all IT classes. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApprovalIntegrationTest.EventCapture.class)
public abstract class ApprovalIntegrationTest {

    public static final String CAPTURE_QUEUE = "it.approval-events";

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("order_approval");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management");

    protected static final FakeFulfilment FULFILMENT = new FakeFulfilment();

    static {
        Startables.deepStart(MYSQL, RABBIT).join();
    }

    @DynamicPropertySource
    static void fakeFulfilment(DynamicPropertyRegistry registry) {
        registry.add("fulfilment.base-url", FULFILMENT::baseUrl);
        registry.add("spring.security.oauth2.client.provider.fulfilment.token-uri", () -> FULFILMENT.baseUrl() + "/token");
    }

    @TestConfiguration
    static class EventCapture {
        /** Captures everything order-approval publishes (approval-requested, approved, ...). */
        @Bean
        Declarables approvalEventCapture() {
            var queue = QueueBuilder.durable(CAPTURE_QUEUE).build();
            return new Declarables(queue, BindingBuilder.bind(queue).to(Queues.eventsExchange()).with("order.#"));
        }
    }

    @Autowired
    protected RabbitTemplate rabbitTemplate;
    @Autowired
    protected JsonMapper jsonMapper;

    protected EventEnvelope<OrderAccepted> publishAccepted(OrderAccepted order) {
        var event = EventEnvelope.of(OrderEvents.ORDER_ACCEPTED, "req-it", Instant.now(), order);
        publishRaw(event.eventId().toString(), jsonMapper.writeValueAsString(event));
        return event;
    }

    protected void publishRaw(String messageId, String json) {
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setMessageId(messageId).setType(OrderEvents.ORDER_ACCEPTED)
                .setHeader(OrderEvents.CORRELATION_HEADER, "req-it")
                .build();
        rabbitTemplate.send(OrderEvents.EXCHANGE, OrderEvents.ORDER_ACCEPTED, message);
    }

    /** Events of one type for one order seen on the capture queue so far (consumes the queue). */
    protected final List<String> captured = new ArrayList<>();

    protected boolean eventSeen(String eventType, String orderId) {
        Message message;
        while ((message = rabbitTemplate.receive(CAPTURE_QUEUE)) != null) {
            captured.add(message.getMessageProperties().getType() + " " + new String(message.getBody(), StandardCharsets.UTF_8));
        }
        return captured.stream().anyMatch(e -> e.startsWith(eventType + " ") && e.contains(orderId));
    }
}
