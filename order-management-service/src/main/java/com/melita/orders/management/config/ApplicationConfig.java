package com.melita.orders.management.config;

import com.melita.orders.events.OrderEvents;
import com.melita.orders.management.pii.PiiCipher;
import com.melita.orders.platform.messaging.MessagingSupportConfiguration;
import com.melita.orders.platform.messaging.OutboxRelay;
import com.melita.orders.platform.messaging.Queues;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.Declarables;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.util.List;

/** Messaging support lives here, not on the application class, so web-slice tests stay light. */
@Configuration(proxyBeanMethods = false)
@Import(MessagingSupportConfiguration.class)
class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** This service's own queue (and DLQ); it also publishes to the events exchange. */
    @Bean
    Declarables orderAcceptedQueue() {
        return Queues.consumerQueue("order-management.order-accepted", List.of(OrderEvents.ORDER_ACCEPTED));
    }

    @Bean
    PiiCipher piiCipher(@Value("${pii.encryption-key}") String key) {
        return new PiiCipher(key);
    }

    @Bean
    Gauge outboxPendingGauge(MeterRegistry registry, OutboxRelay relay) {
        return Gauge.builder("outbox.pending", relay, OutboxRelay::pendingCount)
                .description("Events written to the outbox but not yet confirmed by the broker")
                .register(registry);
    }
}
