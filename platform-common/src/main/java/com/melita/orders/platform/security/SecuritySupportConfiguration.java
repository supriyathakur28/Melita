package com.melita.orders.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Problem-JSON handlers for 401 and 403. Each service still declares its own {@code SecurityFilterChain},
 * so its access rules stay visible in that service.
 */
@Configuration(proxyBeanMethods = false)
public class SecuritySupportConfiguration {

    @Bean
    ProblemAuthenticationEntryPoint problemAuthenticationEntryPoint(JsonMapper jsonMapper) {
        return new ProblemAuthenticationEntryPoint(jsonMapper);
    }

    @Bean
    ProblemAccessDeniedHandler problemAccessDeniedHandler(JsonMapper jsonMapper) {
        return new ProblemAccessDeniedHandler(jsonMapper);
    }
}
