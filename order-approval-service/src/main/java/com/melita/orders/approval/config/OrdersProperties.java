package com.melita.orders.approval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/** {@code zone}: where "today" is evaluated for installation dates. */
@ConfigurationProperties("orders")
public record OrdersProperties(ZoneId zone) {

    public OrdersProperties {
        zone = zone == null ? ZoneId.of("Europe/Malta") : zone;
    }
}
