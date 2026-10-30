package com.melita.orders.approval.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DecisionRequests {

    private DecisionRequests() {
    }

    public record Approve(@Size(max = 500) String comment) {
    }

    public record Reject(@NotBlank @Size(min = 10, max = 500) String reason) {
    }
}
