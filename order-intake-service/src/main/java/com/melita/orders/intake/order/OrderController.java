package com.melita.orders.intake.order;

import com.melita.orders.intake.order.api.OrderReceipt;
import com.melita.orders.intake.order.api.PlaceOrderRequest;
import com.melita.orders.platform.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";
    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final OrderIntakeService service;

    OrderController(OrderIntakeService service) {
        this.service = service;
    }

    @Operation(summary = "Place an order", description = "Requires an Idempotency-Key; retrying with the same key "
            + "and body returns the original response.")
    @PostMapping
    ResponseEntity<OrderReceipt> place(
            @Parameter(description = "8-64 characters, letters, digits, '-' or '_'; unique per order attempt")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        if (idempotencyKey == null || !KEY_FORMAT.matcher(idempotencyKey).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID",
                    "The Idempotency-Key header is required: 8-64 characters, letters, digits, '-' or '_'");
        }
        PlacementResult result = service.place(PartnerIds.from(jwt), idempotencyKey, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(result.receipt().orderId()).toUri();
        var response = ResponseEntity.created(location);
        if (result.replayed()) {
            response.header(REPLAYED, "true");
        }
        return response.body(result.receipt());
    }

    @Operation(summary = "Read one of your own orders")
    @GetMapping("/{orderId}")
    OrderReceipt find(@PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
        return service.find(PartnerIds.from(jwt), orderId);
    }
}
