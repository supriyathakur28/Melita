package com.melita.orders.approval.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(@NotBlank @Size(min = 10, max = 500) String reason) {
}
