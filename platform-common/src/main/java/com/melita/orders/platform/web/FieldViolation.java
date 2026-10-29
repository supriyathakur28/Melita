package com.melita.orders.platform.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One problem with one field. {@code code} is set for business rules (422) and constraint names (400). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldViolation(String field, String code, String message) {
}
