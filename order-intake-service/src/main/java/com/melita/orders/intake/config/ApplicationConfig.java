package com.melita.orders.intake.config;

import com.melita.orders.platform.messaging.MessagingSupportConfiguration;
import com.melita.orders.platform.messaging.OutboxRelay;
import com.melita.orders.platform.messaging.Queues;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.DeserializationFeature;

import java.time.Clock;

/** Outbox and messaging support live here, not on the application class, so web-slice tests stay light. */
@Configuration(proxyBeanMethods = false)
@Import(MessagingSupportConfiguration.class)
class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Unknown JSON properties are rejected, so a misspelt optional field is an error instead of being dropped. */
    @Bean
    JsonMapperBuilderCustomizer strictJson() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** order-intake only publishes; it declares the exchange and nothing else. */
    @Bean
    TopicExchange eventsExchange() {
        return Queues.eventsExchange();
    }

    @Bean
    Gauge outboxPendingGauge(MeterRegistry registry, OutboxRelay relay) {
        return Gauge.builder("outbox.pending", relay, OutboxRelay::pendingCount)
                .description("Events written to the outbox but not yet confirmed by the broker")
                .register(registry);
    }
}
