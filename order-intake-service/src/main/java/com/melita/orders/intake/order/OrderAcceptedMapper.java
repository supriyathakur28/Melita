package com.melita.orders.intake.order;

import com.melita.orders.events.OrderAccepted;
import com.melita.orders.intake.catalogue.Catalogue;
import com.melita.orders.intake.order.api.PlaceOrderRequest;

import java.time.Instant;
import java.util.UUID;

/** Translates the public request into the event contract (Message Translator at the edge). */
final class OrderAcceptedMapper {

    private OrderAcceptedMapper() {
    }

    static OrderAccepted toEvent(final UUID orderId, final String partnerId, final PlaceOrderRequest request,
                                 final Catalogue catalogue, final Instant acceptedAt) {
        final PlaceOrderRequest.CustomerRequest c = request.customer();
        final OrderAccepted.Address customerAddress = address(c.address());
        final var customer = new OrderAccepted.Customer(c.firstName(), c.lastName(), c.dateOfBirth(), c.email(),
                c.mobileNumber(),
                new OrderAccepted.IdentityDocument(c.identityDocument().type().name(), c.identityDocument().number()),
                customerAddress);

        OrderAccepted.Installation installation = null;
        if (request.installation() != null) {
            final PlaceOrderRequest.InstallationRequest i = request.installation();
            installation = new OrderAccepted.Installation(
                    i.usesCustomerAddress() ? customerAddress : address(i.address()),
                    i.preferredDate(), i.timeSlot().name());
        }

        final var items = request.items().stream()
                .map(item -> new OrderAccepted.Item(item.product().name(), item.packageCode(),
                        catalogue.findPackage(item.packageCode()).orElseThrow().pkg().name()))
                .toList();

        return new OrderAccepted(orderId, partnerId, request.externalReference(), customer, installation, items,
                acceptedAt);
    }

    private static OrderAccepted.Address address(final PlaceOrderRequest.AddressRequest a) {
        return new OrderAccepted.Address(a.line1(), a.line2(), a.locality(), a.postcode());
    }
}
