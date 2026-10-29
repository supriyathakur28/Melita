package com.melita.orders.platform.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Retry for listener failures that are not poison: 5 retries, 2 s doubling to 30 s, about a minute in total.
 * Long enough to ride out a database restart without filling the dead-letter queue.
 */
@ConfigurationProperties("messaging.listener.retry")
public record ListenerRetryProperties(Integer maxRetries, Duration initialDelay, Duration maxDelay) {

    public ListenerRetryProperties {
        maxRetries = maxRetries == null ? 5 : maxRetries;
        initialDelay = initialDelay == null ? Duration.ofSeconds(2) : initialDelay;
        maxDelay = maxDelay == null ? Duration.ofSeconds(30) : maxDelay;
    }
}
