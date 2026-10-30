package com.melita.orders.approval.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the user name of the agent making the request, taken from the JWT ({@code preferred_username},
 * falling back to {@code sub}). The deciding agent always comes from the token, never from the request body.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "claims['preferred_username'] ?: subject")
public @interface CurrentAgent {
}
