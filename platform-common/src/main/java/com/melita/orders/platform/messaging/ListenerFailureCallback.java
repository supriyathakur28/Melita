package com.melita.orders.platform.messaging;

import org.springframework.amqp.core.Message;

/** Optional hook called once a message is about to be dead-lettered, e.g. to mark a row as FAILED. */
@FunctionalInterface
public interface ListenerFailureCallback {

    void onDeadLetter(Message message, Throwable cause);
}
