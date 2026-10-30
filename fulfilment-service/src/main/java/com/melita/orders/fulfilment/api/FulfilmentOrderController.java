package com.melita.orders.fulfilment.api;

import com.melita.orders.fulfilment.order.FulfilmentOrderService;
import com.melita.orders.fulfilment.order.SubmissionResult;
import com.melita.orders.platform.idempotency.IdempotencyKey;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


/** The Ordering Fulfilment API (contract in the design doc, §8.3). */
@RestController
@RequestMapping("/api/v1/fulfilment-orders")
public class FulfilmentOrderController {

    private final FulfilmentOrderService service;

    FulfilmentOrderController(final FulfilmentOrderService service) {
        this.service = service;
    }

    @Operation(summary = "Submit an approved order", description = "201 creates the fulfilment order; the same "
            + "Idempotency-Key and body return the original with 200.")
    @PostMapping
    ResponseEntity<FulfilmentOrderResponse> submit(
            @RequestHeader(IdempotencyKey.HEADER) @IdempotencyKey final String idempotencyKey,
            @Valid @RequestBody final FulfilmentOrderRequest request) {
        final SubmissionResult result = service.submit(idempotencyKey, request);
        if (result.replayed()) {
            return ResponseEntity.ok(result.response());
        }
        final var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{reference}")
                .buildAndExpand(result.response().fulfilmentReference()).toUri();
        return ResponseEntity.created(location).body(result.response());
    }

    @Operation(summary = "Read a fulfilment order; the ID number is masked")
    @GetMapping("/{fulfilmentReference}")
    FulfilmentOrderView find(@PathVariable final String fulfilmentReference) {
        return service.find(fulfilmentReference);
    }
}
