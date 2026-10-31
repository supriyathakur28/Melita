package com.melita.orders.platform.web;

import lombok.experimental.UtilityClass;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** The request ID follows one request across services: HTTP header, logs (MDC), events and outbound calls. */
@UtilityClass
public class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /** Keeps a caller-supplied ID if it is safe to log and echo; otherwise generates one. */
    public static String acceptOrGenerate(final String supplied) {
        return supplied != null && ACCEPTED.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /**
     * Runs {@code action} as part of request {@code requestId}: its logs and outbound calls carry the ID. For work
     * outside an HTTP request or a listener, such as scheduled jobs. Restores the previous ID afterwards.
     */
    public static <T> T callWith(final String requestId, final Supplier<T> action) {
        final String previous = MDC.get(MDC_KEY);
        put(requestId);
        try {
            return action.get();
        } finally {
            put(previous);
        }
    }

    public static void runWith(final String requestId, final Runnable action) {
        callWith(requestId, () -> {
            action.run();
            return null;
        });
    }

    private static void put(final String requestId) {
        if (requestId == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, requestId);
        }
    }
}
