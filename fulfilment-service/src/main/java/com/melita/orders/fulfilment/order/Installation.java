package com.melita.orders.fulfilment.order;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import java.time.LocalDate;

@Embeddable
public record Installation(
        @Column(name = "installation_date") LocalDate preferredDate,
        @Column(name = "installation_time_slot") String timeSlot,
        @Embedded
        @AttributeOverrides({
                @AttributeOverride(name = "line1", column = @Column(name = "installation_address_line1")),
                @AttributeOverride(name = "line2", column = @Column(name = "installation_address_line2")),
                @AttributeOverride(name = "locality", column = @Column(name = "installation_address_locality")),
                @AttributeOverride(name = "postcode", column = @Column(name = "installation_address_postcode"))})
        Address address,
        @Column(name = "preferred_slot_expired") Boolean preferredSlotExpired) {
}
