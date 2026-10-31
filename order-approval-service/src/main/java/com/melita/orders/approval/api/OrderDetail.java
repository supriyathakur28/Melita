package com.melita.orders.approval.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.melita.orders.approval.order.Address;
import com.melita.orders.approval.order.CustomerOrder;
import com.melita.orders.approval.order.OrderStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** An order as agents see it. Entities never leave the service; the ID number is always masked. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderDetail(UUID orderId, String partnerId, String externalReference, OrderStatus status,
                          Customer customer, Installation installation, List<Item> items,
                          List<String> approvalReasons, Decision decision, String fulfilmentReference,
                          Instant receivedAt, List<History> history, List<String> warnings) {

    public record Customer(String firstName, String lastName, LocalDate dateOfBirth, String email,
                           String mobileNumber, String idDocumentType, String idDocumentNumberMasked,
                           Address address) {
    }

    public record Installation(LocalDate preferredDate, String timeSlot, Address address) {
    }

    public record Item(String product, String packageCode, String packageName) {
    }

    public record Decision(String decidedBy, Instant decidedAt, String note) {
    }

    public record History(OrderStatus from, OrderStatus to, String actor, String reason, Instant at) {
    }

    static OrderDetail from(final CustomerOrder order, final List<String> warnings) {
        final var customer = order.getCustomer();
        final var installation = order.getInstallation();
        return new OrderDetail(order.getId(), order.getPartnerId(), order.getExternalReference(), order.getStatus(),
                new Customer(customer.firstName(), customer.lastName(), customer.dateOfBirth(), customer.email(),
                        customer.mobileNumber(), customer.idDocumentType(), customer.maskedIdDocumentNumber(),
                        customer.address()),
                installation == null ? null
                        : new Installation(installation.preferredDate(), installation.timeSlot(), installation.address()),
                order.getItems().stream()
                        .map(item -> new Item(item.getProductCode(), item.getPackageCode(), item.getPackageName()))
                        .toList(),
                order.getApprovalReasons(),
                order.getDecidedBy() == null ? null
                        : new Decision(order.getDecidedBy(), order.getDecidedAt(), order.getDecisionNote()),
                order.getFulfilmentReference(),
                order.getReceivedAt(),
                order.getHistory().stream()
                        .map(change -> new History(change.getFromStatus(), change.getToStatus(), change.getActor(),
                                change.getReason(), change.getOccurredAt()))
                        .toList(),
                warnings.isEmpty() ? null : warnings);
    }
}
