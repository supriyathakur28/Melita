package com.melita.orders.fulfilment;

import com.melita.orders.fulfilment.api.FulfilmentOrderRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TestRequests {

    private TestRequests() {
    }

    public static FulfilmentOrderRequest request(final UUID orderId) {
        final var address = new FulfilmentOrderRequest.Address("12, Triq il-Kbira", null, "Mosta", "MST 1234");
        return new FulfilmentOrderRequest(orderId, "ERP-" + orderId.toString().substring(0, 8), "erp-partner",
                new FulfilmentOrderRequest.Customer("Maria", "Borg", LocalDate.of(1990, 4, 12), "maria.borg@example.com",
                        "+35679123456", "ID_CARD", "123456M", address),
                new FulfilmentOrderRequest.Installation(address, LocalDate.of(2026, 11, 16), "MORNING", false),
                List.of(new FulfilmentOrderRequest.Item("INTERNET", "INTERNET_1GBPS", "Internet 1Gbps")));
    }
}
