package com.melita.orders.platform.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Outbox, inbox, named lock and the listener support. Import with
 * {@code @Import(MessagingSupportConfiguration.class)}; the service provides a {@link Clock} bean and enables
 * scheduling so the relay runs.
 */
@Configuration(proxyBeanMethods = false)
@Import(RabbitListenerSupportConfiguration.class)
public class MessagingSupportConfiguration {

    @Bean
    OutboxWriter outboxWriter(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        return new OutboxWriter(jdbc, jsonMapper);
    }

    @Bean
    NamedLock namedLock(DataSource dataSource) {
        return new NamedLock(dataSource);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcTemplate jdbc, RabbitTemplate rabbitTemplate, NamedLock namedLock, Clock clock) {
        rabbitTemplate.setMandatory(true);
        return new OutboxRelay(jdbc, rabbitTemplate, namedLock, clock);
    }

    @Bean
    InboxGuard inboxGuard(JdbcTemplate jdbc, Clock clock) {
        return new InboxGuard(jdbc, clock);
    }
}
