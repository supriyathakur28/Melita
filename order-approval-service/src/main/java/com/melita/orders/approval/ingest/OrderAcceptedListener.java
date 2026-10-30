package com.melita.orders.approval.ingest;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderAccepted;
import com.melita.orders.platform.messaging.PoisonMessageException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes {@code order.accepted}. A payload that cannot be read or lacks the essentials is poison and goes
 * straight to the DLQ; any other failure is retried, then dead-lettered (see platform-common).
 */
@Component
class OrderAcceptedListener {

    static final String QUEUE = "order-approval.order-accepted";

    private final OrderIngestionService ingestion;
    private final JsonMapper jsonMapper;

    OrderAcceptedListener(final OrderIngestionService ingestion, final JsonMapper jsonMapper) {
        this.ingestion = ingestion;
        this.jsonMapper = jsonMapper;
    }

    @RabbitListener(queues = QUEUE)
    void onOrderAccepted(final Message message) {
        ingestion.ingest(read(message));
    }

    private EventEnvelope<OrderAccepted> read(final Message message) {
        final EventEnvelope<OrderAccepted> event = parse(message);
        final OrderAccepted order = event.data();
        if (event.schemaVersion() != EventEnvelope.CURRENT_SCHEMA_VERSION || order.orderId() == null
                || order.customer() == null || order.customer().identityDocument() == null
                || order.items() == null || order.items().isEmpty()) {
            throw new PoisonMessageException("order.accepted event " + event.eventId() + " is incomplete");
        }
        return event;
    }

    private EventEnvelope<OrderAccepted> parse(final Message message) {
        try {
            return jsonMapper.readValue(message.getBody(), new TypeReference<>() { });
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            throw new PoisonMessageException("Unreadable order.accepted payload", e);
        }
    }
}
