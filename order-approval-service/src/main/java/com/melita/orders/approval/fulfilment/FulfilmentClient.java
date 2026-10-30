package com.melita.orders.approval.fulfilment;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Calls Fulfilment. Every call carries {@code Idempotency-Key = orderId}, so retrying an unknown outcome
 * cannot create a second order. The circuit breaker counts only temporary failures: a 4xx is about the order,
 * not about Fulfilment's health.
 */
@Component
public class FulfilmentClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public FulfilmentClient(final RestClient fulfilmentRestClient, final CircuitBreaker fulfilmentCircuitBreaker) {
        this.restClient = fulfilmentRestClient;
        this.circuitBreaker = fulfilmentCircuitBreaker;
    }

    public boolean isAvailable() {
        return circuitBreaker.getState() != CircuitBreaker.State.OPEN
                && circuitBreaker.getState() != CircuitBreaker.State.FORCED_OPEN;
    }

    public FulfilmentOutcome submit(final FulfilmentRequest request) {
        try {
            return circuitBreaker.executeSupplier(() -> call(request));
        } catch (CallNotPermittedException open) {
            return new FulfilmentOutcome.NotAttempted();
        } catch (TemporaryFailure failure) {
            return new FulfilmentOutcome.RetryLater(failure.getMessage());
        }
    }

    private FulfilmentOutcome call(final FulfilmentRequest request) {
        try {
            final Map<?, ?> body = restClient.post()
                    .uri("/api/v1/fulfilment-orders")
                    .header("Idempotency-Key", request.orderId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            final Object reference = body == null ? null : body.get("fulfilmentReference");
            if (reference == null) {
                throw new TemporaryFailure("Fulfilment returned no fulfilmentReference");
            }
            return new FulfilmentOutcome.Submitted(reference.toString());
        } catch (RestClientResponseException e) {
            final HttpStatusCode status = e.getStatusCode();
            if (status.is4xxClientError() && status.value() != 429) {
                return new FulfilmentOutcome.Rejected("HTTP " + status.value() + " from Fulfilment");
            }
            throw new TemporaryFailure("HTTP " + status.value() + " from Fulfilment");
        } catch (ResourceAccessException e) {
            throw new TemporaryFailure("Fulfilment unreachable: " + e.getMessage());
        } catch (RestClientException e) {
            // e.g. a 2xx whose body cannot be read: the outcome is unknown, so retry with the same key.
            throw new TemporaryFailure("Unreadable response from Fulfilment: " + e.getClass().getSimpleName());
        }
    }

    /** Recorded by the circuit breaker. */
    public static class TemporaryFailure extends RuntimeException {
        TemporaryFailure(final String message) {
            super(message);
        }
    }
}
