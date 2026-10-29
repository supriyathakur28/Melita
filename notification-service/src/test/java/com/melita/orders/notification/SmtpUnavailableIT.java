package com.melita.orders.notification;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderEvents;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** SMTP unreachable: the email is retried, then dead-lettered and recorded as FAILED. Orders are unaffected. */
class SmtpUnavailableIT extends NotificationIntegrationTest {

    @DynamicPropertySource
    static void deadSmtp(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> 1);
    }

    @Autowired
    NotificationRepository notifications;

    @Test
    void failedEmailIsRetriedThenDeadLetteredAndMarkedFailed() {
        var order = EmailComposerTest.order();
        publish(OrderEvents.ORDER_ACCEPTED, EventEnvelope.of(OrderEvents.ORDER_ACCEPTED, "req", Instant.now(), order));

        Message dead = rabbitTemplate.receive("notification.order-events.dlq", 20_000);
        assertThat(dead).isNotNull();
        await().atMost(Duration.ofSeconds(5)).until(() -> notifications.findByOrderId(order.orderId().toString())
                .stream().anyMatch(n -> n.getStatus() == Notification.Status.FAILED));
        Notification failed = notifications.findByOrderId(order.orderId().toString()).getFirst();
        assertThat(failed.getAttempts()).as("first try plus one listener retry").isEqualTo(2);
        assertThat(failed.getLastError()).isNotBlank();
    }
}
