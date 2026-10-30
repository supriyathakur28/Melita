package com.melita.orders.intake.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** One row per (partner, Idempotency-Key), written in the same transaction as the order it produced. */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord implements Persistable<IdempotencyRecord.Key> {

    @Embeddable
    public record Key(@Column(name = "partner_id") String partnerId,
                      @Column(name = "idempotency_key") String idempotencyKey) implements Serializable {
    }

    @EmbeddedId
    private Key key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "order_id", nullable = false, length = 36)
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(final Key key, final String requestHash, final UUID orderId, final Instant createdAt) {
        this.key = key;
        this.requestHash = requestHash;
        this.orderId = orderId;
        this.createdAt = createdAt;
        this.isNew = true;
    }

    @Override
    public Key getId() {
        return key;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
