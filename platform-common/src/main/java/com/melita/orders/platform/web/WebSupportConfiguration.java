package com.melita.orders.platform.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

/** Import from a service with {@code @Import(WebSupportConfiguration.class)}. */
@Configuration(proxyBeanMethods = false)
@Import(GlobalExceptionHandler.class)
public class WebSupportConfiguration {

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        final FilterRegistrationBean<RequestIdFilter> registration =
                new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
