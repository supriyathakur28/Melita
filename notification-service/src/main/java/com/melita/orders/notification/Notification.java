package com.melita.orders.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One email: PENDING when claimed, SENT once the SMTP server accepted it, FAILED after retries ran out. */
@Entity
@Table(name = "notification")
public class Notification {

    public enum Status { PENDING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String template;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {
    }

    Notification(String eventId, String template, String orderId, String recipient, Instant now) {
        this.eventId = eventId;
        this.template = template;
        this.orderId = orderId;
        this.recipient = recipient;
        this.status = Status.PENDING;
        this.createdAt = now;
    }

    void attempt() {
        attempts++;
    }

    void sent(Instant now) {
        status = Status.SENT;
        sentAt = now;
        lastError = null;
    }

    void failed(String error) {
        if (status != Status.SENT) {
            status = Status.FAILED;
            lastError = error.length() > 255 ? error.substring(0, 255) : error;
        }
    }

    public Long getId() {
        return id;
    }

    public Status getStatus() {
        return status;
    }

    public String getRecipient() {
        return recipient;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
