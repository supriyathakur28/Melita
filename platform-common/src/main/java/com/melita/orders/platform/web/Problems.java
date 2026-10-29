package com.melita.orders.platform.web;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/** Builds RFC 9457 problem details with the properties every response of this platform carries. */
public final class Problems {

    private static final String TYPE_BASE = "https://api.melita.example/problems/";

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(title);
        problem.setProperty("code", code);
        String requestId = RequestIds.current();
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail,
                                   List<FieldViolation> errors) {
        ProblemDetail problem = of(status, code, title, detail);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }
}
