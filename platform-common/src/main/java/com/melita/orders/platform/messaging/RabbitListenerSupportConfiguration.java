package com.melita.orders.platform.messaging;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;

/**
 * The listener container factory used by every consumer. Errors fall into two classes:
 * <ul>
 *   <li><b>poison</b> (unreadable or invalid payload): no retry, straight to the DLQ;</li>
 *   <li><b>everything else</b>: retried in the listener with exponential backoff, then sent to the DLQ.</li>
 * </ul>
 * Import with {@code @Import(RabbitListenerSupportConfiguration.class)} (already included by
 * {@link MessagingSupportConfiguration}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ListenerRetryProperties.class)
public class RabbitListenerSupportConfiguration {

    /** Replaces Boot's default listener factory. */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            final SimpleRabbitListenerContainerFactoryConfigurer configurer, final ConnectionFactory connectionFactory,
            final ListenerRetryProperties retry, final ObjectProvider<ListenerFailureCallback> failureCallbacks) {
        final SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);

        final RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(retry.maxRetries())
                .delay(retry.initialDelay())
                .multiplier(2.0)
                .maxDelay(retry.maxDelay())
                .predicate(failure -> !isPoison(failure))
                .build();
        final Advice retryInterceptor = RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .recoverer(new DeadLetteringRecoverer(failureCallbacks.orderedStream().toList()))
                .build();

        factory.setAdviceChain(new CorrelationIdAdvice(), retryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    static boolean isPoison(final Throwable failure) {
        return ListenerFailureCallback.causeChain(failure)
                .anyMatch(t -> t instanceof PoisonMessageException || t instanceof MessageConversionException);
    }
}
