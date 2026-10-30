package com.melita.orders.approval.api;

import com.melita.orders.approval.order.OrderStatus;
import com.melita.orders.platform.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/** Private Order Approval API for Melita agents. The deciding agent always comes from the token. */
@RestController
@RequestMapping("/api/v1/orders")
class AgentOrderController {

    private static final Set<String> SORTABLE = Set.of("receivedAt", "externalReference", "status");

    private final AgentOrderService service;

    AgentOrderController(final AgentOrderService service) {
        this.service = service;
    }

    @Operation(summary = "List orders, e.g. those waiting for approval")
    @GetMapping
    PagedModel<OrderViews.Summary> list(@RequestParam(required = false) final OrderStatus status,
                                        @PageableDefault(size = 20, sort = "receivedAt") final Pageable pageable) {
        final boolean unsortable = pageable.getSort().stream()
                .map(Sort.Order::getProperty)
                .anyMatch(property -> !SORTABLE.contains(property));
        if (unsortable) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT",
                    "Sorting is allowed by " + SORTABLE + " only");
        }
        return new PagedModel<>(service.list(status, pageable));
    }

    @Operation(summary = "Order detail with its history; the ID number is masked")
    @GetMapping("/{orderId}")
    OrderViews.Detail get(@PathVariable final UUID orderId) {
        return service.get(orderId);
    }

    @Operation(summary = "Approve an order that is waiting for approval")
    @PostMapping("/{orderId}/approve")
    OrderViews.Detail approve(@PathVariable final UUID orderId,
                              @Valid @RequestBody(required = false) final DecisionRequests.Approve request,
                              @CurrentAgent final String agent) {
        return service.approve(orderId, agent, request == null ? null : request.comment());
    }

    @Operation(summary = "Reject an order that is waiting for approval; a reason is required")
    @PostMapping("/{orderId}/reject")
    OrderViews.Detail reject(@PathVariable final UUID orderId,
                             @Valid @RequestBody final DecisionRequests.Reject request,
                             @CurrentAgent final String agent) {
        return service.reject(orderId, agent, request.reason());
    }
}
