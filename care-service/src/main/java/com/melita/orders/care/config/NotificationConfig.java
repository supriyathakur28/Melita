package com.melita.orders.care.config;

import com.melita.orders.events.OrderEvents;
import com.melita.orders.platform.messaging.Queues;
import com.melita.orders.platform.messaging.RabbitListenerSupportConfiguration;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;
import java.util.List;

/** Consumer only: listener support from platform-common, no outbox. */
@Configuration(proxyBeanMethods = false)
@Import(RabbitListenerSupportConfiguration.class)
class NotificationConfig {

    static final String QUEUE = "care.order-events";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Declarables notificationQueue() {
        return Queues.consumerQueue(QUEUE, List.of(OrderEvents.ORDER_ACCEPTED, OrderEvents.ORDER_APPROVAL_REQUESTED,
                OrderEvents.ORDER_FULFILMENT_FAILED));
    }

    /** No API: only health and info are reachable. */
    @Bean
    SecurityFilterChain securityFilterChain(final HttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
