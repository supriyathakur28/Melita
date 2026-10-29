package com.melita.orders.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderApprovalRequested(
        UUID orderId,
        String externalReference,
        List<String> approvalReasons,
        List<OrderAccepted.Item> items) {
}
