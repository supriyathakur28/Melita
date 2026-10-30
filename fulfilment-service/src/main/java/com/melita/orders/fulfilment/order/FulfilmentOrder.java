package com.melita.orders.fulfilment.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The final order, created when an approved order is submitted. Immutable once stored: a repeated submission
 * with the same Idempotency-Key is answered from this row, never creates a second one.
 */
@Entity
@Table(name = "fulfilment_order")
public class FulfilmentOrder implements Persistable<UUID> {

    public static final String STATUS_SCHEDULED = "SCHEDULED";

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @Column(name = "fulfilment_reference", nullable = false)
    private String fulfilmentReference;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "order_id", nullable = false, length = 36)
    private UUID orderId;

    @Column(name = "partner_id", nullable = false)
    private String partnerId;

    @Column(name = "external_reference", nullable = false)
    private String externalReference;

    @Column(nullable = false)
    private String status;

    @Embedded
    private Customer customer;

    @Embedded
    private Installation installation;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<FulfilmentOrderItem> items = new ArrayList<>();

    @Transient
    private boolean isNew;

    protected FulfilmentOrder() {
    }

    public record NewItem(String productCode, String packageCode, String packageName) {
    }

    public static FulfilmentOrder create(String fulfilmentReference, String idempotencyKey, String requestHash,
                                         UUID orderId, String partnerId, String externalReference, Customer customer,
                                         Installation installation, List<NewItem> items, Instant receivedAt) {
        var order = new FulfilmentOrder();
        order.id = UUID.randomUUID();
        order.fulfilmentReference = fulfilmentReference;
        order.idempotencyKey = idempotencyKey;
        order.requestHash = requestHash;
        order.orderId = orderId;
        order.partnerId = partnerId;
        order.externalReference = externalReference;
        order.status = STATUS_SCHEDULED;
        order.customer = customer;
        order.installation = installation;
        order.receivedAt = receivedAt;
        order.isNew = true;
        items.forEach(i -> order.items.add(new FulfilmentOrderItem(order, i.productCode(), i.packageCode(), i.packageName())));
        return order;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getFulfilmentReference() {
        return fulfilmentReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getOrderId() {
        return orderId;
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

    public Customer getCustomer() {
        return customer;
    }

    public Installation getInstallation() {
        return installation;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public List<FulfilmentOrderItem> getItems() {
        return List.copyOf(items);
    }
}
