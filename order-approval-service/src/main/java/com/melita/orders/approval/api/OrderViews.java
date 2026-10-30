package com.melita.orders.approval.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.melita.orders.approval.order.Address;
import com.melita.orders.approval.order.CustomerOrder;
import com.melita.orders.approval.order.OrderStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response bodies of the Approval API. Entities never leave the service; the ID number is always masked. */
public final class OrderViews {

    private OrderViews() {
    }

    public record Summary(UUID orderId, String partnerId, String externalReference, OrderStatus status,
                          boolean approvalRequired, Instant receivedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(UUID orderId, String partnerId, String externalReference, OrderStatus status,
                         Customer customer, Installation installation, List<Item> items, List<String> approvalReasons,
                         Decision decision, String fulfilmentReference, Instant receivedAt, List<History> history,
                         List<String> warnings) {
    }

    public record Customer(String firstName, String lastName, LocalDate dateOfBirth, String email,
                           String mobileNumber, String idDocumentType, String idDocumentNumberMasked, Address address) {
    }

    public record Installation(LocalDate preferredDate, String timeSlot, Address address) {
    }

    public record Item(String product, String packageCode, String packageName) {
    }

    public record Decision(String decidedBy, Instant decidedAt, String note) {
    }

    public record History(OrderStatus from, OrderStatus to, String actor, String reason, Instant at) {
    }

    static Detail detail(CustomerOrder order, List<String> warnings) {
        var c = order.getCustomer();
        var i = order.getInstallation();
        return new Detail(order.getId(), order.getPartnerId(), order.getExternalReference(), order.getStatus(),
                new Customer(c.firstName(), c.lastName(), c.dateOfBirth(), c.email(), c.mobileNumber(),
                        c.idDocumentType(), c.maskedIdDocumentNumber(), c.address()),
                i == null ? null : new Installation(i.preferredDate(), i.timeSlot(), i.address()),
                order.getItems().stream().map(it -> new Item(it.getProductCode(), it.getPackageCode(), it.getPackageName())).toList(),
                order.getApprovalReasons(),
                order.getDecidedBy() == null ? null : new Decision(order.getDecidedBy(), order.getDecidedAt(), order.getDecisionNote()),
                order.getFulfilmentReference(), order.getReceivedAt(),
                order.getHistory().stream().map(h -> new History(h.getFromStatus(), h.getToStatus(), h.getActor(),
                        h.getReason(), h.getOccurredAt())).toList(),
                warnings.isEmpty() ? null : warnings);
    }
}
