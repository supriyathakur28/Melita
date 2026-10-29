package com.melita.orders.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Published by order-intake once an order is accepted. This is the only event that carries personal data:
 * Fulfilment and Care need the full order. Later events refer to the order by ID.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderAccepted(
        UUID orderId,
        String partnerId,
        String externalReference,
        Customer customer,
        Installation installation,
        List<Item> items,
        Instant acceptedAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(
            String firstName,
            String lastName,
            LocalDate dateOfBirth,
            String email,
            String mobileNumber,
            IdentityDocument identityDocument,
            Address address) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IdentityDocument(String type, String number) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Address(String line1, String line2, String locality, String postcode) {
    }

    /** Present only when at least one item needs a technician visit. The address is always resolved. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Installation(Address address, LocalDate preferredDate, String timeSlot) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String product, String packageCode, String packageName) {
    }
}
