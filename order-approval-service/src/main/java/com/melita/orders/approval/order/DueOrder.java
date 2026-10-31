package com.melita.orders.approval.order;

import java.util.UUID;

/** An order the Fulfilment submitter should send now, with the request ID it was placed under. */
public record DueOrder(UUID id, String correlationId) {
}
