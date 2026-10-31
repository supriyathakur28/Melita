package com.melita.orders.platform.web;

import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/** Builds RFC 9457 problem details with the properties every response of this platform carries. */
@UtilityClass
public class Problems {

    private static final String TYPE_BASE = "https://api.melita.example/problems/";

    public static ProblemDetail of(final HttpStatusCode status, final String code, final String title,
                                   final String detail) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(title);
        problem.setProperty("code", code);
        final String requestId = RequestIds.current();
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }

    public static ProblemDetail of(final HttpStatusCode status, final String code, final String title,
                                   final String detail, final List<FieldViolation> errors) {
        final ProblemDetail problem = of(status, code, title, detail);
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }
}
