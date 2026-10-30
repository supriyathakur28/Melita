package com.melita.orders.platform.messaging;

/**
 * Thrown by a listener when a message can never be processed (unreadable or invalid payload). It is not
 * retried and goes straight to the dead-letter queue.
 */
public class PoisonMessageException extends RuntimeException {

    public PoisonMessageException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public PoisonMessageException(final String message) {
        super(message);
    }
}
