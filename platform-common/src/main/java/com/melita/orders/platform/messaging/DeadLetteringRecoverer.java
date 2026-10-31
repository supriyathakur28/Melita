package com.melita.orders.platform.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

import java.util.List;

/**
 * Runs the failure callbacks, then rejects without requeue so RabbitMQ moves the message to the DLQ.
 * <p>
 * Logs only the message ID and type. Spring's {@code RejectAndDontRequeueRecoverer} is not used because it
 * logs the whole message, and an {@code order.accepted} body contains personal data.
 */
@Slf4j
class DeadLetteringRecoverer implements MessageRecoverer {

    private final List<ListenerFailureCallback> callbacks;

    DeadLetteringRecoverer(final List<ListenerFailureCallback> callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public void recover(final Message message, final Throwable cause) {
        log.error("Dead-lettering message {} ({}): {}", message.getMessageProperties().getMessageId(),
                message.getMessageProperties().getType(), ListenerFailureCallback.rootMessage(cause));
        callbacks.forEach(callback -> notify(callback, message, cause));
        throw new AmqpRejectAndDontRequeueException("Retries exhausted or poison message", cause);
    }

    /** A failing callback must not stop the message from being dead-lettered. */
    private static void notify(final ListenerFailureCallback callback, final Message message, final Throwable cause) {
        try {
            callback.onDeadLetter(message, cause);
        } catch (RuntimeException e) {
            log.warn("Dead-letter callback failed", e);
        }
    }
}
