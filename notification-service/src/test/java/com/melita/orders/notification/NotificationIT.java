package com.melita.orders.notification;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderApprovalRequested;
import com.melita.orders.events.OrderEvents;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class NotificationIT extends NotificationIntegrationTest {

    @org.springframework.test.context.DynamicPropertySource
    static void smtp(org.springframework.test.context.DynamicPropertyRegistry registry) {
        useMailpit(registry);
    }

    @Autowired
    NotificationRepository notifications;

    @Test
    void newOrderIsEmailedToTheAgentsMailboxOnce() {
        var order = EmailComposerTest.order();
        var event = EventEnvelope.of(OrderEvents.ORDER_ACCEPTED, "req", Instant.now(), order);

        publish(OrderEvents.ORDER_ACCEPTED, event);
        publish(OrderEvents.ORDER_ACCEPTED, event);

        String expected = "New order ERP-42 -> agents@melita.example";
        await().atMost(Duration.ofSeconds(15)).until(() -> notifications.findByOrderId(order.orderId().toString())
                .stream().anyMatch(n -> n.getStatus() == Notification.Status.SENT));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> true);
        assertThat(notifications.findByOrderId(order.orderId().toString())).hasSize(1);
        assertThat(mailbox().stream().filter(expected::equals).count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void approvalRequestIsEmailed() {
        var data = new OrderApprovalRequested(java.util.UUID.randomUUID(), "ERP-77", List.of("PACKAGE:MOBILE_POSTPAID"),
                EmailComposerTest.order().items());

        publish(OrderEvents.ORDER_APPROVAL_REQUESTED,
                EventEnvelope.of(OrderEvents.ORDER_APPROVAL_REQUESTED, "req", Instant.now(), data));

        await().atMost(Duration.ofSeconds(15)).until(() -> mailbox().contains("Approval needed: order ERP-77 -> agents@melita.example"));
    }

    @Test
    void unreadableEventGoesToTheDeadLetterQueue() {
        rabbitTemplate.send("orders.events", OrderEvents.ORDER_ACCEPTED, MessageBuilder
                .withBody("garbage".getBytes(StandardCharsets.UTF_8)).setType(OrderEvents.ORDER_ACCEPTED).build());

        Message dead = rabbitTemplate.receive("notification.order-events.dlq", 15_000);
        assertThat(dead).isNotNull();
    }
}
