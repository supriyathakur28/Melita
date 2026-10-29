package com.melita.orders.fulfilmentstub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Fulfilment contract we assume (design doc §8.3). Honours {@code Idempotency-Key}: the same key and
 * body get the original reference back (200); the same key with a different body is a 409.
 * {@code X-Stub-Force-Status} makes the stub answer with any status, to exercise failure paths.
 */
@RestController
@RequestMapping("/api/v1/fulfilment-orders")
class FulfilmentOrderController {

    private static final Logger log = LoggerFactory.getLogger(FulfilmentOrderController.class);

    record Accepted(String fulfilmentReference, String status) {
    }

    private record Stored(JsonNode request, Accepted response) {
    }

    private final Map<String, Stored> byIdempotencyKey = new ConcurrentHashMap<>();

    @PostMapping
    ResponseEntity<?> submit(@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                             @RequestHeader(name = "X-Stub-Force-Status", required = false) Integer forcedStatus,
                             @RequestBody JsonNode request) {
        if (forcedStatus != null) {
            log.info("Forcing status {} for key {}", forcedStatus, idempotencyKey);
            HttpStatus status = HttpStatus.valueOf(forcedStatus);
            return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, "Forced by the stub"));
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Idempotency-Key is required"));
        }

        Stored fresh = new Stored(request, new Accepted("FUL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "SCHEDULED"));
        Stored stored = byIdempotencyKey.putIfAbsent(idempotencyKey, fresh);
        if (stored == null) {
            log.info("Scheduled fulfilment {} for key {}", fresh.response().fulfilmentReference(), idempotencyKey);
            return ResponseEntity.status(HttpStatus.CREATED).body(fresh.response());
        }
        if (!stored.request().equals(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "Idempotency-Key reused with a different body"));
        }
        return ResponseEntity.ok(stored.response());
    }
}
