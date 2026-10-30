package com.melita.orders.fulfilment.order;

import com.melita.orders.platform.pii.EncryptedStringConverter;
import com.melita.orders.platform.pii.Masking;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

import java.time.LocalDate;

/** The customer, as received. The identity document number is encrypted at rest. */
@Embeddable
public record Customer(
        @Column(name = "customer_first_name") String firstName,
        @Column(name = "customer_last_name") String lastName,
        @Column(name = "customer_date_of_birth") LocalDate dateOfBirth,
        @Column(name = "customer_email") String email,
        @Column(name = "customer_mobile_number") String mobileNumber,
        @Column(name = "id_document_type") String idDocumentType,
        @Convert(converter = EncryptedStringConverter.class)
        @Column(name = "id_document_number") String idDocumentNumber,
        @Embedded
        @AttributeOverrides({
                @AttributeOverride(name = "line1", column = @Column(name = "customer_address_line1")),
                @AttributeOverride(name = "line2", column = @Column(name = "customer_address_line2")),
                @AttributeOverride(name = "locality", column = @Column(name = "customer_address_locality")),
                @AttributeOverride(name = "postcode", column = @Column(name = "customer_address_postcode"))})
        Address address) {

    @Override
    public String toString() {
        return "Customer[" + firstName + " " + lastName + ", idDocument=" + Masking.mask(idDocumentNumber) + "]";
    }
}
