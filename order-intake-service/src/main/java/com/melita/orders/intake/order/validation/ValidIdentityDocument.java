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
import java.util.regex.Pattern;

/** Number format depends on the document type: Maltese ID card ({@code 123456M}) or passport. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidIdentityDocument.Validator.class)
public @interface ValidIdentityDocument {

    String message() default "is not a valid number for this document type";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidIdentityDocument, PlaceOrderRequest.IdentityDocumentRequest> {

        private static final Pattern ID_CARD = Pattern.compile("\\d{1,7}[MGAPLHBZ]");
        private static final Pattern PASSPORT = Pattern.compile("[A-Z0-9]{6,20}");

        @Override
        public boolean isValid(PlaceOrderRequest.IdentityDocumentRequest document, ConstraintValidatorContext context) {
            if (document == null || document.type() == null || document.number() == null) {
                return true;
            }
            Pattern pattern = document.type() == PlaceOrderRequest.DocumentType.ID_CARD ? ID_CARD : PASSPORT;
            boolean valid = pattern.matcher(document.number()).matches();
            if (!valid) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addPropertyNode("number").addConstraintViolation();
            }
            return valid;
        }
    }
}
