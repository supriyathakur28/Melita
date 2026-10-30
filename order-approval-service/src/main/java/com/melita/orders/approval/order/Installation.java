package com.melita.orders.approval.order;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import java.time.LocalDate;

/** Present only for products that need a technician visit. The address is always the resolved one. */
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
        Address address) {

    /** The preferred date is today or already past, so Fulfilment must agree a new date with the customer. */
    public boolean isExpiredOn(LocalDate today) {
        return !preferredDate.isAfter(today);
    }
}
