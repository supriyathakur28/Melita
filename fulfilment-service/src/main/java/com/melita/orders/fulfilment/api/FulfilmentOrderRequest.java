package com.melita.orders.fulfilment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An approved order submitted by order-approval-service. Unknown fields are ignored, so the caller can add
 * fields without breaking this service.
 */
public record FulfilmentOrderRequest(
        @NotNull UUID orderId,
        @NotBlank @Size(max = 64) String externalReference,
        @NotBlank @Size(max = 64) String partnerId,
        @NotNull @Valid Customer customer,
        @Valid Installation installation,
        @NotEmpty @Size(max = 4) List<@NotNull @Valid Item> items) {

    public record Customer(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull LocalDate dateOfBirth,
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 16) String mobileNumber,
            @NotBlank @Size(max = 16) String idDocumentType,
            @NotBlank @Size(max = 20) String idDocumentNumber,
            @NotNull @Valid Address address) {

        /** Never print the document number. */
        @Override
        public String toString() {
            return "Customer[" + firstName + " " + lastName + ", idDocument=****]";
        }
    }

    public record Address(
            @NotBlank @Size(max = 100) String line1,
            @Size(max = 100) String line2,
            @NotBlank @Size(max = 100) String locality,
            @NotBlank @Size(max = 8) String postcode) {
    }

    public record Installation(
            @NotNull @Valid Address address,
            @NotNull LocalDate preferredDate,
            @NotBlank @Size(max = 16) String timeSlot,
            boolean preferredSlotExpired) {
    }

    public record Item(
            @NotBlank @Size(max = 32) String product,
            @NotBlank @Size(max = 64) String packageCode,
            @NotBlank @Size(max = 100) String packageName) {
    }
}
