package com.melita.orders.fulfilment.config;

import com.melita.orders.platform.security.ProblemAccessDeniedHandler;
import com.melita.orders.platform.security.ProblemAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Service-to-service API: client-credentials JWTs with audience {@code fulfilment-api}. Submitting needs
 * {@code fulfilment:submit}, reading {@code fulfilment:read}. Deny by default.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(final HttpSecurity http, final ProblemAuthenticationEntryPoint entryPoint,
                                            final ProblemAccessDeniedHandler deniedHandler) {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/fulfilment-orders").hasAuthority("SCOPE_fulfilment:submit")
                        .requestMatchers(HttpMethod.GET, "/api/v1/fulfilment-orders/*").hasAuthority("SCOPE_fulfilment:read")
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> { })
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .build();
    }
}
