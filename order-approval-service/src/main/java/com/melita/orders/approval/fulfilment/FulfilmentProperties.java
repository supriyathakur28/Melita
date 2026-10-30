package com.melita.orders.approval.fulfilment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Fulfilment endpoint, timeouts and retry budget, from Spring Cloud Config. */
@ConfigurationProperties("fulfilment")
public record FulfilmentProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Integer maxAttempts,
        Duration initialRetryDelay,
        Duration maxRetryDelay,
        Duration circuitOpenDuration) {

    public FulfilmentProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        maxAttempts = maxAttempts == null ? 5 : maxAttempts;
        initialRetryDelay = initialRetryDelay == null ? Duration.ofSeconds(10) : initialRetryDelay;
        maxRetryDelay = maxRetryDelay == null ? Duration.ofMinutes(5) : maxRetryDelay;
        circuitOpenDuration = circuitOpenDuration == null ? Duration.ofSeconds(30) : circuitOpenDuration;
    }
}
