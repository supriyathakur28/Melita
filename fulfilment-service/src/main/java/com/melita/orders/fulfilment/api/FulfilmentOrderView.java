package com.melita.orders.fulfilment.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Detail of a stored fulfilment order. The identity document number is masked. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FulfilmentOrderView(
        String fulfilmentReference,
        UUID orderId,
        String partnerId,
        String externalReference,
        String status,
        String customerName,
        String idDocumentNumberMasked,
        Installation installation,
        List<Item> items,
        Instant receivedAt) {

    public record Installation(LocalDate preferredDate, String timeSlot, String postcode, boolean preferredSlotExpired) {
    }

    public record Item(String product, String packageCode, String packageName) {
    }
}
