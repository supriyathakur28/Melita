package com.melita.orders.platform.messaging;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Outbox, inbox, named lock and the listener container factory. Import with
 * {@code @Import(MessagingSupportConfiguration.class)}; the service provides a {@link Clock} bean and enables
 * scheduling.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ListenerRetryProperties.class)
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

    /**
     * Replaces Boot's default listener factory. Errors fall into two classes:
     * <ul>
     *   <li><b>poison</b> (unreadable or invalid payload): no retry, straight to the DLQ;</li>
     *   <li><b>everything else</b>: retried in the listener with exponential backoff, then sent to the DLQ.</li>
     * </ul>
     */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            ListenerRetryProperties retry, ObjectProvider<ListenerFailureCallback> failureCallbacks) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);

        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(retry.maxRetries())
                .delay(retry.initialDelay())
                .multiplier(2.0)
                .maxDelay(retry.maxDelay())
                .predicate(failure -> !isPoison(failure))
                .build();
        Advice retryInterceptor = RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .recoverer(new DeadLetteringRecoverer(failureCallbacks.orderedStream().toList()))
                .build();

        factory.setAdviceChain(new CorrelationIdAdvice(), retryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    static boolean isPoison(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof PoisonMessageException || t instanceof MessageConversionException) {
                return true;
            }
        }
        return false;
    }
}
