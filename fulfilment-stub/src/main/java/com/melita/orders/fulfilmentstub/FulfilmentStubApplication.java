package com.melita.orders.fulfilmentstub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootApplication
public class FulfilmentStubApplication {

    public static void main(String[] args) {
        SpringApplication.run(FulfilmentStubApplication.class, args);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/fulfilment-orders/**").hasAuthority("SCOPE_fulfilment:submit")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> { }))
                .build();
    }
}
