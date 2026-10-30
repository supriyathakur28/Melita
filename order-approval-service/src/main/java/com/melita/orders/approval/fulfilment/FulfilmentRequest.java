package com.melita.orders.approval.fulfilment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Body sent to Fulfilment (contract in the design doc, §8.3). */
public record FulfilmentRequest(
        UUID orderId,
        String externalReference,
        String partnerId,
        Customer customer,
        Installation installation,
        List<Item> items) {

    public record Customer(String firstName, String lastName, LocalDate dateOfBirth, String email, String mobileNumber,
                           String idDocumentType, String idDocumentNumber, Address address) {
        @Override
        public String toString() {
            return "Customer[" + firstName + " " + lastName + ", idDocument=****]";
        }
    }

    public record Address(String line1, String line2, String locality, String postcode) {
    }

    public record Installation(Address address, LocalDate preferredDate, String timeSlot, boolean preferredSlotExpired) {
    }

    public record Item(String product, String packageCode, String packageName) {
    }
}
