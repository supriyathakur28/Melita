package com.melita.orders.intake;

import com.melita.orders.intake.order.api.PlaceOrderRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.AddressRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.CustomerRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.DocumentType;
import com.melita.orders.intake.order.api.PlaceOrderRequest.IdentityDocumentRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.InstallationRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.ItemRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.Product;
import com.melita.orders.intake.order.api.PlaceOrderRequest.TimeSlot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Test data for order requests. */
public final class TestOrders {

    private TestOrders() {
    }

    public static AddressRequest address() {
        return new AddressRequest("12, Triq il-Kbira", null, "Mosta", "MST 1234");
    }

    public static CustomerRequest customer() {
        return new CustomerRequest("Maria", "Borg", LocalDate.of(1990, 4, 12), "maria.borg@example.com",
                "+35679123456", new IdentityDocumentRequest(DocumentType.ID_CARD, "123456M"), address());
    }

    public static InstallationRequest installation(final LocalDate date) {
        return new InstallationRequest(true, null, date, TimeSlot.MORNING);
    }

    public static PlaceOrderRequest internetAndMobile(final String reference, final LocalDate installationDate) {
        return new PlaceOrderRequest(reference, customer(), installation(installationDate), List.of(
                new ItemRequest(Product.INTERNET, "INTERNET_1GBPS"),
                new ItemRequest(Product.MOBILE, "MOBILE_POSTPAID")));
    }

    public static PlaceOrderRequest mobileOnly(final String reference) {
        return new PlaceOrderRequest(reference, customer(), null,
                List.of(new ItemRequest(Product.MOBILE, "MOBILE_PREPAID")));
    }

    /** A Monday about two weeks out: always valid for the default lead time and horizon. */
    public static LocalDate validInstallationDate() {
        return LocalDate.now(ZoneId.of("Europe/Malta"))
                .plusDays(14)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }
}
