package com.melita.orders.platform.web;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/** The request ID follows one request across services: HTTP header, logs (MDC), events and outbound calls. */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private RequestIds() {
    }

    /** Keeps a caller-supplied ID if it is safe to log and echo; otherwise generates one. */
    public static String acceptOrGenerate(final String supplied) {
        return supplied != null && ACCEPTED.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
