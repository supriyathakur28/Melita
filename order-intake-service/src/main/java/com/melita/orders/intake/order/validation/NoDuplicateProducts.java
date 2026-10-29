package com.melita.orders.intake.order.validation;

import com.melita.orders.intake.order.api.PlaceOrderRequest;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** An order has at most one package per product type. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoDuplicateProducts.Validator.class)
public @interface NoDuplicateProducts {

    String message() default "each product may appear only once";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<NoDuplicateProducts, PlaceOrderRequest> {
        @Override
        public boolean isValid(PlaceOrderRequest request, ConstraintValidatorContext context) {
            if (request == null || request.items() == null) {
                return true;
            }
            Set<PlaceOrderRequest.Product> seen = new HashSet<>();
            boolean valid = request.items().stream().filter(Objects::nonNull).map(PlaceOrderRequest.ItemRequest::product)
                    .filter(Objects::nonNull).allMatch(seen::add);
            if (!valid) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addPropertyNode("items").addConstraintViolation();
            }
            return valid;
        }
    }
}
