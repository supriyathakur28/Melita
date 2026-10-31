package com.melita.orders.intake.order;

import com.melita.orders.intake.TestOrders;
import com.melita.orders.intake.catalogue.Catalogue;
import com.melita.orders.intake.catalogue.Catalogue.PackageEntry;
import com.melita.orders.intake.catalogue.Catalogue.ProductEntry;
import com.melita.orders.intake.order.api.PlaceOrderRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.ItemRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.Product;
import com.melita.orders.platform.web.FieldViolation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRulesTest {

    /** Wednesday 4 Nov 2026, 09:00 UTC. */
    private static final Instant WEDNESDAY_MORNING = Instant.parse("2026-11-04T09:00:00Z");

    private static final Catalogue CATALOGUE = new Catalogue(List.of(
            new ProductEntry("INTERNET", "Internet", true, true, List.of(
                    new PackageEntry("INTERNET_250MBPS", "Internet 250Mbps", true),
                    new PackageEntry("INTERNET_1GBPS", "Internet 1Gbps", true),
                    new PackageEntry("INTERNET_100MBPS", "Internet 100Mbps (retired)", false))),
            new ProductEntry("MOBILE", "Mobile", false, true, List.of(
                    new PackageEntry("MOBILE_PREPAID", "Mobile prepaid", true),
                    new PackageEntry("MOBILE_POSTPAID", "Mobile post-paid", true)))));

    private final InstallationProperties properties = new InstallationProperties(
            2, 60, Set.of(DayOfWeek.SUNDAY), Set.of(LocalDate.of(2026, 12, 8)));

    private List<FieldViolation> check(final PlaceOrderRequest request, final Instant now) {
        return new OrderRules(properties, Clock.fixed(now, ZoneOffset.UTC)).check(request, CATALOGUE);
    }

    private static List<String> codes(final List<FieldViolation> violations) {
        return violations.stream().map(FieldViolation::code).toList();
    }

    @Test
    void unknownAndRetiredPackagesAreRejected() {
        final var request = new PlaceOrderRequest("ERP-1", TestOrders.customer(), null, List.of(
                new ItemRequest(Product.INTERNET, "INTERNET_10GBPS"),
                new ItemRequest(Product.MOBILE, "INTERNET_100MBPS")));

        final var violations = check(request, WEDNESDAY_MORNING);

        assertThat(violations).extracting(FieldViolation::field)
                .contains("items[0].packageCode", "items[1].packageCode");
        assertThat(codes(violations)).containsExactly("UNKNOWN_PACKAGE", "UNKNOWN_PACKAGE");
    }

    @Test
    void packageMustBelongToTheStatedProduct() {
        final var request = new PlaceOrderRequest("ERP-1", TestOrders.customer(), null,
                List.of(new ItemRequest(Product.MOBILE, "INTERNET_1GBPS")));

        assertThat(codes(check(request, WEDNESDAY_MORNING))).containsExactly("PACKAGE_PRODUCT_MISMATCH");
    }

    @Test
    void mobileOnlyOrderMustNotCarryInstallationDetails() {
        final var request = new PlaceOrderRequest("ERP-1", TestOrders.customer(),
                TestOrders.installation(LocalDate.of(2026, 11, 9)),
                List.of(new ItemRequest(Product.MOBILE, "MOBILE_PREPAID")));

        assertThat(codes(check(request, WEDNESDAY_MORNING))).containsExactly("INSTALLATION_NOT_APPLICABLE");
    }

    @Test
    void customerMustBeAtLeast18() {
        // 18th birthday is 5 Nov 2026: underage the day before, accepted on the day.
        final var customer = TestOrders.customer();
        final var minor = new PlaceOrderRequest.CustomerRequest(customer.firstName(), customer.lastName(),
                LocalDate.of(2008, 11, 5), customer.email(), customer.mobileNumber(), customer.identityDocument(),
                customer.address());
        final var request = new PlaceOrderRequest("ERP-1", minor, null,
                List.of(new ItemRequest(Product.MOBILE, "MOBILE_PREPAID")));

        assertThat(codes(check(request, WEDNESDAY_MORNING))).containsExactly("CUSTOMER_UNDERAGE");
        assertThat(check(request, WEDNESDAY_MORNING.plus(Duration.ofDays(1)))).isEmpty();
    }

    @Test
    void installationNeedsTwoWorkingDaysNoticeSkippingSundays() {
        // Friday 6 Nov: two working days ahead are Sat 7 and Mon 9 (Sunday is skipped).
        final Instant friday = Instant.parse("2026-11-06T09:00:00Z");

        assertThat(check(TestOrders.internetAndMobile("A", LocalDate.of(2026, 11, 7)), friday))
                .extracting(FieldViolation::message).singleElement().asString().contains("on or after 2026-11-09");
        assertThat(check(TestOrders.internetAndMobile("A", LocalDate.of(2026, 11, 9)), friday)).isEmpty();
    }

    @Test
    void zeroLeadTimeAllowsToday() {
        final var sameDay = new InstallationProperties(0, 60, Set.of(DayOfWeek.SUNDAY), Set.of());
        final var rules = new OrderRules(sameDay, Clock.fixed(WEDNESDAY_MORNING, ZoneOffset.UTC));

        assertThat(rules.check(TestOrders.internetAndMobile("A", LocalDate.of(2026, 11, 4)), CATALOGUE)).isEmpty();
    }

    @Test
    void sundaysPublicHolidaysAndDatesBeyondTheHorizonAreRejected() {
        assertThat(codes(check(TestOrders.internetAndMobile("A", LocalDate.of(2026, 11, 15)), WEDNESDAY_MORNING)))
                .containsExactly("INSTALLATION_DATE_INVALID");
        assertThat(codes(check(TestOrders.internetAndMobile("A", LocalDate.of(2026, 12, 8)), WEDNESDAY_MORNING)))
                .containsExactly("INSTALLATION_DATE_INVALID");
        assertThat(codes(check(TestOrders.internetAndMobile("A", LocalDate.of(2027, 1, 5)), WEDNESDAY_MORNING)))
                .containsExactly("INSTALLATION_DATE_INVALID");
    }

    @Test
    void everyViolationIsReportedTogether() {
        final var customer = TestOrders.customer();
        final var minor = new PlaceOrderRequest.CustomerRequest(customer.firstName(), customer.lastName(),
                LocalDate.of(2015, 1, 1), customer.email(), customer.mobileNumber(), customer.identityDocument(),
                customer.address());
        final var request = new PlaceOrderRequest("ERP-1", minor, null, List.of(
                new ItemRequest(Product.INTERNET, "INTERNET_1GBPS"),
                new ItemRequest(Product.MOBILE, "NOPE")));

        assertThat(codes(check(request, WEDNESDAY_MORNING)))
                .containsExactlyInAnyOrder("UNKNOWN_PACKAGE", "INSTALLATION_REQUIRED", "CUSTOMER_UNDERAGE");
    }
}
