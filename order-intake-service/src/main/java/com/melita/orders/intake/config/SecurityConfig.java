package com.melita.orders.intake.config;

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
 * Public API for partners. Stateless JWT resource server: issuer, expiry and audience
 * ({@code spring.security.oauth2.resourceserver.jwt.audiences}) are validated by Spring Security; access
 * is by OAuth2 scope. Deny by default. CSRF is disabled because there are no cookies or sessions.
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
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders").hasAuthority("SCOPE_orders:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/*").hasAuthority("SCOPE_orders:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalogue/products").hasAuthority("SCOPE_orders:read")
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
