package com.melita.orders.platform.messaging;

import org.springframework.amqp.core.Message;

import java.util.Objects;
import java.util.stream.Stream;

/** Optional hook called once a message is about to be dead-lettered, e.g. to mark a row as FAILED. */
@FunctionalInterface
public interface ListenerFailureCallback {

    void onDeadLetter(Message message, Throwable cause);

    /** The failure and its causes, outermost first (stops at a self-referencing cause). */
    static Stream<Throwable> causeChain(final Throwable failure) {
        return Stream.iterate(failure, Objects::nonNull, t -> t.getCause() == t ? null : t.getCause());
    }

    /** Type and message of the innermost cause: short, and free of payload data. */
    static String rootMessage(final Throwable failure) {
        final Throwable root = causeChain(failure).reduce((outer, inner) -> inner).orElse(failure);
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
