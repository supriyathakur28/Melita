package com.melita.orders.platform.web;

import com.melita.orders.platform.security.ProblemAccessDeniedHandler;
import com.melita.orders.platform.security.ProblemAuthenticationEntryPoint;
import com.melita.orders.platform.security.SecuritySupportConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

/** Minimal application used by the web-slice tests of the shared error model and security handlers. */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@Import({WebSupportConfiguration.class, SecuritySupportConfiguration.class, WebTestApplication.TestController.class})
class WebTestApplication {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemAuthenticationEntryPoint entryPoint,
                                            ProblemAccessDeniedHandler deniedHandler) {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/test/scoped").hasAuthority("SCOPE_orders:write")
                        .requestMatchers("/test/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> { })
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return mock(JwtDecoder.class);
    }

    record Payload(@NotBlank String name, @NotBlank @Email String email) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/ok")
        Map<String, String> ok() {
            return Map.of("status", "ok");
        }

        @GetMapping("/test/scoped")
        Map<String, String> scoped() {
            return Map.of("status", "ok");
        }

        @GetMapping("/test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        Map<String, String> admin() {
            return Map.of("status", "ok");
        }

        @GetMapping("/test/not-found")
        void notFound() {
            throw ApiException.notFound("Order");
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw ApiException.conflict("DUPLICATE_EXTERNAL_REFERENCE", "Reference already used",
                    Map.of("existingOrderId", "abc"));
        }

        @GetMapping("/test/rules")
        void rules() {
            throw ApiException.businessRules(List.of(
                    new FieldViolation("items[0].packageCode", "UNKNOWN_PACKAGE", "Unknown package"),
                    new FieldViolation("customer.dateOfBirth", "CUSTOMER_UNDERAGE", "Customer must be 18 or over")));
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @PostMapping("/test/validate")
        Payload validate(@Valid @RequestBody Payload payload) {
            return payload;
        }

        @GetMapping("/test/concurrent")
        void concurrent() {
            throw new org.springframework.dao.OptimisticLockingFailureException("row version changed");
        }

        @GetMapping("/test/deadlock")
        void deadlock() {
            throw new org.springframework.dao.CannotAcquireLockException("Deadlock found when trying to get lock");
        }

        @PostMapping("/test/validate-with-header")
        Payload validateWithHeader(@org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key")
                                   @com.melita.orders.platform.idempotency.IdempotencyKey String key,
                                   @Valid @RequestBody Payload payload) {
            return payload;
        }

        @GetMapping("/test/teapot")
        void teapot() {
            throw new ApiException(HttpStatus.I_AM_A_TEAPOT, "TEAPOT", "short and stout");
        }
    }
}
