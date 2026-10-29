package com.melita.orders.management.order;

import jakarta.persistence.Embeddable;

@Embeddable
public record Address(String line1, String line2, String locality, String postcode) {
}
