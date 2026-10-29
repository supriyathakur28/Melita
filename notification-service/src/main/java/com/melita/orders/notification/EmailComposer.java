package com.melita.orders.notification;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderAccepted;
import com.melita.orders.events.OrderApprovalRequested;
import com.melita.orders.events.OrderEvents;
import com.melita.orders.events.OrderFulfilmentFailed;
import com.melita.orders.platform.messaging.PoisonMessageException;
import com.melita.orders.platform.pii.Masking;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;

/**
 * Turns an event into an email (Message Translator). Personal data is minimised: the identity document number
 * is masked. An unreadable payload or a template error can never succeed, so both are poison.
 */
@Component
public class EmailComposer {

    private final TemplateEngine templates;
    private final JsonMapper jsonMapper;

    public EmailComposer(TemplateEngine templates, JsonMapper jsonMapper) {
        this.templates = templates;
        this.jsonMapper = jsonMapper;
    }

    /** @return the email for this event type, or empty for event types that need no email. */
    public Optional<Email> compose(String eventType, byte[] payload) {
        try {
            return switch (eventType == null ? "" : eventType) {
                case OrderEvents.ORDER_ACCEPTED -> {
                    EventEnvelope<OrderAccepted> event = read(payload, new TypeReference<>() { });
                    OrderAccepted order = event.data();
                    yield Optional.of(render(event, order.orderId().toString(), "order-accepted",
                            "New order " + order.externalReference(),
                            Map.of("order", order, "maskedIdNumber", Masking.mask(order.customer().identityDocument().number()))));
                }
                case OrderEvents.ORDER_APPROVAL_REQUESTED -> {
                    EventEnvelope<OrderApprovalRequested> event = read(payload, new TypeReference<>() { });
                    yield Optional.of(render(event, event.data().orderId().toString(), "approval-requested",
                            "Approval needed: order " + event.data().externalReference(), Map.of("order", event.data())));
                }
                case OrderEvents.ORDER_FULFILMENT_FAILED -> {
                    EventEnvelope<OrderFulfilmentFailed> event = read(payload, new TypeReference<>() { });
                    yield Optional.of(render(event, event.data().orderId().toString(), "fulfilment-failed",
                            "Fulfilment failed: order " + event.data().externalReference(), Map.of("order", event.data())));
                }
                default -> Optional.empty();
            };
        } catch (TemplateProcessingException | NullPointerException e) {
            throw new PoisonMessageException("Cannot render " + eventType + " email", e);
        }
    }

    private <T> EventEnvelope<T> read(byte[] payload, TypeReference<EventEnvelope<T>> type) {
        try {
            return jsonMapper.readValue(payload, type);
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            throw new PoisonMessageException("Unreadable event payload", e);
        }
    }

    private Email render(EventEnvelope<?> event, String orderId, String template, String subject,
                         Map<String, Object> model) {
        String html = templates.process("mail/" + template, new Context(null, model));
        return new Email(event.eventId().toString(), orderId, template, subject, html);
    }
}
