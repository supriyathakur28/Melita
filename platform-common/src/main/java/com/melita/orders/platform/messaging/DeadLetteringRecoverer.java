package com.melita.orders.platform.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;

import java.util.List;

/** Runs the failure callbacks, then rejects without requeue so RabbitMQ moves the message to the DLQ. */
class DeadLetteringRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetteringRecoverer.class);

    private final RejectAndDontRequeueRecoverer reject = new RejectAndDontRequeueRecoverer();
    private final List<ListenerFailureCallback> callbacks;

    DeadLetteringRecoverer(List<ListenerFailureCallback> callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        log.error("Dead-lettering message {} ({}): {}", message.getMessageProperties().getMessageId(),
                message.getMessageProperties().getType(), rootMessage(cause));
        for (ListenerFailureCallback callback : callbacks) {
            try {
                callback.onDeadLetter(message, cause);
            } catch (RuntimeException e) {
                log.warn("Dead-letter callback failed", e);
            }
        }
        reject.recover(message, cause);
    }

    static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
