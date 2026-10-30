package com.melita.orders.fulfilment.api;

import com.melita.orders.fulfilment.order.FulfilmentOrderService;
import com.melita.orders.fulfilment.order.SubmissionResult;
import com.melita.orders.platform.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.regex.Pattern;

/** The Ordering Fulfilment API (contract in the design doc, §8.3). */
@RestController
@RequestMapping("/api/v1/fulfilment-orders")
public class FulfilmentOrderController {

    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final FulfilmentOrderService service;

    FulfilmentOrderController(FulfilmentOrderService service) {
        this.service = service;
    }

    @Operation(summary = "Submit an approved order", description = "201 creates the fulfilment order; the same "
            + "Idempotency-Key and body return the original with 200.")
    @PostMapping
    ResponseEntity<FulfilmentOrderResponse> submit(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody FulfilmentOrderRequest request) {
        if (idempotencyKey == null || !KEY_FORMAT.matcher(idempotencyKey).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID",
                    "The Idempotency-Key header is required: 8-64 characters, letters, digits, '-' or '_'");
        }
        SubmissionResult result = service.submit(idempotencyKey, request);
        if (result.replayed()) {
            return ResponseEntity.ok(result.response());
        }
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{reference}")
                .buildAndExpand(result.response().fulfilmentReference()).toUri();
        return ResponseEntity.created(location).body(result.response());
    }

    @Operation(summary = "Read a fulfilment order; the ID number is masked")
    @GetMapping("/{fulfilmentReference}")
    FulfilmentOrderView find(@PathVariable String fulfilmentReference) {
        return service.find(fulfilmentReference);
    }
}
