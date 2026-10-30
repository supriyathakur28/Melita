package com.melita.orders.approval;

import com.melita.orders.events.OrderAccepted;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** order.accepted payloads for tests. */
public final class TestOrderEvents {

    private TestOrderEvents() {
    }

    public static OrderAccepted.Address address() {
        return new OrderAccepted.Address("12, Triq il-Kbira", null, "Mosta", "MST 1234");
    }

    public static OrderAccepted accepted(final UUID orderId, final List<OrderAccepted.Item> items,
                                         final LocalDate installationDate) {
        final var customer = new OrderAccepted.Customer("Maria", "Borg", LocalDate.of(1990, 4, 12),
                "maria.borg@example.com", "+35679123456", new OrderAccepted.IdentityDocument("ID_CARD", "123456M"),
                address());
        final var installation = installationDate == null ? null
                : new OrderAccepted.Installation(address(), installationDate, "MORNING");
        return new OrderAccepted(orderId, "erp-partner", "ERP-" + orderId.toString().substring(0, 8), customer,
                installation, items, Instant.now());
    }

    public static OrderAccepted.Item item(final String product, final String packageCode) {
        return new OrderAccepted.Item(product, packageCode, packageCode.replace('_', ' '));
    }
}
