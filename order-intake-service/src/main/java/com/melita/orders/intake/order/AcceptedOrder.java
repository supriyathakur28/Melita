package com.melita.orders.intake.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * The receipt of an accepted order. No personal data is kept here; the full order travels in the
 * {@code order.accepted} event.
 * <p>
 * Implements {@link Persistable} so that saving always INSERTs: with an application-assigned ID, Spring Data
 * would otherwise merge, which turns a duplicate into a silent UPDATE instead of a unique-key violation.
 */
@Entity
@Table(name = "accepted_order")
public class AcceptedOrder implements Persistable<UUID> {

    public static final String STATUS_ACCEPTED = "ACCEPTED";

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @Column(name = "partner_id", nullable = false)
    private String partnerId;

    @Column(name = "external_reference", nullable = false)
    private String externalReference;

    @Column(nullable = false)
    private String status;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Transient
    private boolean isNew;

    protected AcceptedOrder() {
    }

    public AcceptedOrder(final UUID id, final String partnerId, final String externalReference,
                         final Instant acceptedAt) {
        this.id = id;
        this.partnerId = partnerId;
        this.externalReference = externalReference;
        this.status = STATUS_ACCEPTED;
        this.acceptedAt = acceptedAt;
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getStatus() {
        return status;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
