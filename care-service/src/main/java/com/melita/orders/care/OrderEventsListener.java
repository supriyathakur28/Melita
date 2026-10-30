package com.melita.orders.care;

import com.melita.orders.platform.messaging.ListenerFailureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order events from {@code care.order-events}. Email is a notification, never the source of
 * truth: an order awaiting approval is always visible in the Approval API, whether or not the email arrived.
 */
@Component
class OrderEventsListener implements ListenerFailureCallback {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final EmailComposer composer;
    private final NotificationService notifications;

    OrderEventsListener(EmailComposer composer, NotificationService notifications) {
        this.composer = composer;
        this.notifications = notifications;
    }

    @RabbitListener(queues = "care.order-events")
    void onOrderEvent(Message message) {
        String type = message.getMessageProperties().getType();
        composer.compose(type, message.getBody()).ifPresentOrElse(notifications::send,
                () -> log.debug("No email for event type {}", type));
    }

    /** Retries are exhausted (e.g. SMTP down for over a minute): record it, the message goes to the DLQ. */
    @Override
    public void onDeadLetter(Message message, Throwable cause) {
        String eventId = message.getMessageProperties().getMessageId();
        if (eventId != null) {
            notifications.markFailed(eventId, String.valueOf(rootMessage(cause)));
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
