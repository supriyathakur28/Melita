package com.melita.orders.platform.web;

import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** The two security problems, shared by the filter-chain handlers and the MVC exception handler. */
@UtilityClass
public class SecurityProblems {

    public static ProblemDetail unauthorized() {
        return Problems.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized",
                "A valid bearer token is required.");
    }

    public static ProblemDetail forbidden() {
        return Problems.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden",
                "The token does not grant access to this resource.");
    }
}
