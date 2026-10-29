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

/** {@code address} is required exactly when the customer's address is not used. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidInstallationAddress.Validator.class)
public @interface ValidInstallationAddress {

    String message() default "address is required unless useCustomerAddress is true, and must be omitted when it is";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidInstallationAddress, PlaceOrderRequest.InstallationRequest> {
        @Override
        public boolean isValid(PlaceOrderRequest.InstallationRequest installation, ConstraintValidatorContext context) {
            if (installation == null) {
                return true;
            }
            boolean valid = installation.usesCustomerAddress() == (installation.address() == null);
            if (!valid) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addPropertyNode("address").addConstraintViolation();
            }
            return valid;
        }
    }
}
