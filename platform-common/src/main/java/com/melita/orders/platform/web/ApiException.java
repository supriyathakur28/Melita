package com.melita.orders.platform.web;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * Base class for errors that map to a well-defined problem response. {@code code} is the stable,
 * machine-readable identifier clients switch on; the message is for humans.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldViolation> violations;
    private final Map<String, Object> properties;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of(), Map.of());
    }

    public ApiException(HttpStatus status, String code, String message,
                        List<FieldViolation> violations, Map<String, Object> properties) {
        super(message);
        this.status = status;
        this.code = code;
        this.violations = List.copyOf(violations);
        this.properties = Map.copyOf(properties);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<FieldViolation> violations() {
        return violations;
    }

    public Map<String, Object> properties() {
        return properties;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException conflict(String code, String message, Map<String, Object> properties) {
        return new ApiException(HttpStatus.CONFLICT, code, message, List.of(), properties);
    }

    /** 422: the request is well-formed but breaks one or more business rules. */
    public static ApiException businessRules(List<FieldViolation> violations) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_RULE_VIOLATION",
                violations.size() + " business rule(s) violated", violations, Map.of());
    }

    public static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
}
