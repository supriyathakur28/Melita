package com.melita.orders.management.fulfilment;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
class FulfilmentClientConfig {

    static final String REGISTRATION_ID = "fulfilment";
    private static final Logger log = LoggerFactory.getLogger(FulfilmentClientConfig.class);

    /** Client credentials outside any HTTP request (the submitter is a scheduled job). Tokens are cached. */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
                                                          OAuth2AuthorizedClientService clients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    RestClient fulfilmentRestClient(RestClient.Builder builder, FulfilmentProperties properties,
                                    OAuth2AuthorizedClientManager authorizedClientManager) {
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());

        var oauth2 = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        oauth2.setClientRegistrationIdResolver(request -> REGISTRATION_ID);

        return builder.baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(oauth2)
                .requestInterceptor((request, body, execution) -> {
                    String requestId = org.slf4j.MDC.get("requestId");
                    if (requestId != null) {
                        request.getHeaders().set("X-Request-Id", requestId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    /** Opens after half of the last 10 calls failed temporarily; 4xx answers are not counted as failures. */
    @Bean
    CircuitBreaker fulfilmentCircuitBreaker(FulfilmentProperties properties) {
        CircuitBreaker breaker = CircuitBreaker.of("fulfilment", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(properties.circuitOpenDuration())
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(FulfilmentClient.TemporaryFailure.class)
                .build());
        breaker.getEventPublisher().onStateTransition(event ->
                log.warn("Fulfilment circuit breaker: {}", event.getStateTransition()));
        return breaker;
    }
}
