package com.melita.orders.care;

/** A rendered email for one event. {@code template} also identifies the notification for deduplication. */
public record Email(String eventId, String orderId, String template, String subject, String html) {
}
