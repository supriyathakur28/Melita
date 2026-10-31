package com.melita.orders.events;

import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Exchange and routing keys. The routing key of an event is always its {@code eventType}.
 */
@UtilityClass
public class OrderEvents {

    public static final String EXCHANGE = "orders.events";
    public static final String DEAD_LETTER_EXCHANGE = "orders.events.dlx";

    public static final String ORDER_ACCEPTED = "order.accepted";
    public static final String ORDER_APPROVAL_REQUESTED = "order.approval-requested";
    public static final String ORDER_APPROVED = "order.approved";
    public static final String ORDER_REJECTED = "order.rejected";
    public static final String ORDER_SUBMITTED = "order.submitted";
    public static final String ORDER_FULFILMENT_FAILED = "order.fulfilment-failed";

    public static final List<String> ALL = List.of(
            ORDER_ACCEPTED, ORDER_APPROVAL_REQUESTED, ORDER_APPROVED,
            ORDER_REJECTED, ORDER_SUBMITTED, ORDER_FULFILMENT_FAILED);

    /** AMQP header carrying the request ID of the call that caused the event. */
    public static final String CORRELATION_HEADER = "x-correlation-id";
}
