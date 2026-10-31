package com.melita.orders.approval.api;

import jakarta.validation.constraints.Size;

/** The body is optional: an agent may approve without a comment. */
public record ApproveRequest(@Size(max = 500) String comment) {
}
