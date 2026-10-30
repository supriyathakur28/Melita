package com.melita.orders.platform.idempotency;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Format of an {@code Idempotency-Key}: 8-64 letters, digits, '-' or '_'. Put it on the
 * {@code @RequestHeader(IdempotencyKey.HEADER)} parameter: Spring MVC validates it before the controller runs,
 * and a bad value becomes a 400 with the header named in the field errors.
 */
@Pattern(regexp = "[A-Za-z0-9_-]{8,64}")
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@Documented
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotencyKey {

    String HEADER = "Idempotency-Key";

    String message() default "must be 8-64 characters: letters, digits, '-' or '_'";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
