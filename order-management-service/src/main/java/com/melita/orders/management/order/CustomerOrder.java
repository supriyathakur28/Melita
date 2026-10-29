package com.melita.orders.management.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The order aggregate. Status changes only through {@link #transitionTo}, which enforces the state machine
 * and records the audit trail. {@code @Version} makes concurrent decisions on the same order fail instead of
 * silently overwriting each other.
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder implements Persistable<UUID> {

    public static final String SYSTEM = "system";
    static final int ERROR_LENGTH = 255;
    static final int REASON_LENGTH = 500;

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @Column(name = "partner_id", nullable = false)
    private String partnerId;

    @Column(name = "external_reference", nullable = false)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Embedded
    private CustomerDetails customer;

    @Embedded
    private Installation installation;

    @Column(name = "preferred_slot_expired")
    private Boolean preferredSlotExpired;

    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired;

    @Convert(converter = StringListConverter.class)
    @Column(name = "approval_reasons")
    private List<String> approvalReasons = new ArrayList<>();

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "fulfilment_reference")
    private String fulfilmentReference;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Version
    private long version;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<StatusChange> history = new ArrayList<>();

    @Transient
    private boolean isNew;

    protected CustomerOrder() {
    }

    /** Creates a received order in its first status: held for approval, or ready for Fulfilment. */
    public static CustomerOrder receive(UUID id, String partnerId, String externalReference, CustomerDetails customer,
                                        Installation installation, List<NewItem> items, List<String> approvalReasons,
                                        Instant now) {
        var order = new CustomerOrder();
        order.id = id;
        order.partnerId = partnerId;
        order.externalReference = externalReference;
        order.customer = customer;
        order.installation = installation;
        order.approvalRequired = !approvalReasons.isEmpty();
        order.approvalReasons = new ArrayList<>(approvalReasons);
        order.receivedAt = now;
        order.updatedAt = now;
        order.isNew = true;
        items.forEach(i -> order.items.add(new OrderItem(order, i.productCode(), i.packageCode(), i.packageName())));
        OrderStatus initial = order.approvalRequired ? OrderStatus.PENDING_APPROVAL : OrderStatus.READY_FOR_FULFILMENT;
        order.status = initial;
        order.nextAttemptAt = initial == OrderStatus.READY_FOR_FULFILMENT ? now : null;
        String reason = order.approvalRequired ? "approval required: " + String.join(", ", approvalReasons) : null;
        order.history.add(new StatusChange(order, null, initial, SYSTEM, reason, now));
        return order;
    }

    public record NewItem(String productCode, String packageCode, String packageName) {
    }

    public void approve(String agent, String comment, Instant now) {
        transitionTo(OrderStatus.READY_FOR_FULFILMENT, agent, comment, now);
        decidedBy = agent;
        decidedAt = now;
        decisionNote = comment;
        nextAttemptAt = now;
    }

    public void reject(String agent, String reason, Instant now) {
        transitionTo(OrderStatus.REJECTED, agent, reason, now);
        decidedBy = agent;
        decidedAt = now;
        decisionNote = reason;
    }

    public void markSubmitted(String reference, Instant now) {
        transitionTo(OrderStatus.SUBMITTED, SYSTEM, "fulfilment reference " + reference, now);
        fulfilmentReference = reference;
        nextAttemptAt = null;
        lastError = null;
    }

    /** A temporary Fulfilment failure: stay READY and try again later. */
    public void recordFailedAttempt(String error, Instant retryAt, Instant now) {
        if (status != OrderStatus.READY_FOR_FULFILMENT) {
            throw new InvalidTransitionException(status, OrderStatus.READY_FOR_FULFILMENT);
        }
        attempts++;
        lastError = truncate(error, ERROR_LENGTH);
        nextAttemptAt = retryAt;
        updatedAt = now;
    }

    /**
     * Whether the preferred installation date had passed, decided on the first submission attempt and then
     * kept: a retry must send Fulfilment the same body, or its idempotency check sees a different request.
     */
    public boolean preferredSlotExpired(java.time.LocalDate today) {
        if (preferredSlotExpired == null) {
            preferredSlotExpired = installation != null && installation.isExpiredOn(today);
        }
        return preferredSlotExpired;
    }

    public void markFulfilmentFailed(String error, Instant now) {
        transitionTo(OrderStatus.FULFILMENT_FAILED, SYSTEM, error, now);
        attempts++;
        lastError = truncate(error, ERROR_LENGTH);
        nextAttemptAt = null;
    }

    private void transitionTo(OrderStatus target, String actor, String reason, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidTransitionException(status, target);
        }
        history.add(new StatusChange(this, status, target, actor, truncate(reason, REASON_LENGTH), now));
        status = target;
        updatedAt = now;
    }

    private static String truncate(String text, int length) {
        return text == null || text.length() <= length ? text : text.substring(0, length);
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

    public String getPartnerId() {
        return partnerId;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public CustomerDetails getCustomer() {
        return customer;
    }

    public Installation getInstallation() {
        return installation;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public List<String> getApprovalReasons() {
        return List.copyOf(approvalReasons);
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public String getFulfilmentReference() {
        return fulfilmentReference;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public long getVersion() {
        return version;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public List<StatusChange> getHistory() {
        return List.copyOf(history);
    }
}
