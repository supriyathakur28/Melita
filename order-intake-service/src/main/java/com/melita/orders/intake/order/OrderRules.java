package com.melita.orders.intake.order;

import com.melita.orders.intake.catalogue.Catalogue;
import com.melita.orders.intake.order.api.PlaceOrderRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.ItemRequest;
import com.melita.orders.platform.web.FieldViolation;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Business rules that need reference data or the calendar (422). Each rule returns its own violations and
 * {@link #check} concatenates them, so a partner can fix everything in one round-trip. "Today" is the date
 * in Malta, from the injected clock.
 */
@Component
public class OrderRules {

    static final int MINIMUM_AGE = 18;

    private final InstallationProperties installation;
    private final Clock clock;

    public OrderRules(final InstallationProperties installation, final Clock clock) {
        this.installation = installation;
        this.clock = clock;
    }

    public List<FieldViolation> check(final PlaceOrderRequest request, final Catalogue catalogue) {
        final LocalDate today = LocalDate.now(clock.withZone(installation.zone()));
        final boolean needsInstallation = needsInstallation(request, catalogue);
        return Stream.of(
                        itemViolations(request, catalogue),
                        installationPresenceViolation(request, needsInstallation).stream(),
                        ageViolation(request, today).stream(),
                        installationDateViolation(request, needsInstallation, today).stream())
                .flatMap(Function.identity())
                .toList();
    }

    /** Every item must name an available package that belongs to the stated product. */
    private static Stream<FieldViolation> itemViolations(final PlaceOrderRequest request, final Catalogue catalogue) {
        return IntStream.range(0, request.items().size())
                .mapToObj(index -> itemViolation(index, request.items().get(index), catalogue))
                .flatMap(Optional::stream);
    }

    private static Optional<FieldViolation> itemViolation(final int index, final ItemRequest item,
                                                          final Catalogue catalogue) {
        final String field = "items[" + index + "].packageCode";
        final Optional<Catalogue.Offer> offer = availableOffer(item, catalogue);
        if (offer.isEmpty()) {
            return Optional.of(new FieldViolation(field, "UNKNOWN_PACKAGE",
                    "'" + item.packageCode() + "' is not an available package"));
        }
        return offer
                .filter(available -> !belongsToProduct(available, item))
                .map(available -> new FieldViolation(field, "PACKAGE_PRODUCT_MISMATCH",
                        "'" + item.packageCode() + "' is a " + available.product().code() + " package, not "
                                + item.product().name()));
    }

    /** Whether any valid item needs a technician visit (Internet, TV, Telephony). */
    private static boolean needsInstallation(final PlaceOrderRequest request, final Catalogue catalogue) {
        return request.items().stream()
                .map(item -> availableOffer(item, catalogue).filter(offer -> belongsToProduct(offer, item)))
                .flatMap(Optional::stream)
                .anyMatch(offer -> offer.product().requiresInstallation());
    }

    private static Optional<Catalogue.Offer> availableOffer(final ItemRequest item, final Catalogue catalogue) {
        return catalogue.findPackage(item.packageCode())
                .filter(offer -> offer.pkg().active() && offer.product().active());
    }

    private static boolean belongsToProduct(final Catalogue.Offer offer, final ItemRequest item) {
        return offer.product().code().equals(item.product().name());
    }

    private static Optional<FieldViolation> installationPresenceViolation(final PlaceOrderRequest request,
                                                                          final boolean needsInstallation) {
        if (needsInstallation && request.installation() == null) {
            return Optional.of(new FieldViolation("installation", "INSTALLATION_REQUIRED",
                    "installation details are required for Internet, TV and Telephony"));
        }
        if (!needsInstallation && request.installation() != null) {
            return Optional.of(new FieldViolation("installation", "INSTALLATION_NOT_APPLICABLE",
                    "installation details must be omitted when no product needs a technician visit"));
        }
        return Optional.empty();
    }

    private static Optional<FieldViolation> ageViolation(final PlaceOrderRequest request, final LocalDate today) {
        return Optional.of(request.customer().dateOfBirth())
                .filter(dateOfBirth -> Period.between(dateOfBirth, today).getYears() < MINIMUM_AGE)
                .map(dateOfBirth -> new FieldViolation("customer.dateOfBirth", "CUSTOMER_UNDERAGE",
                        "the customer must be at least " + MINIMUM_AGE));
    }

    /** Only checked when installation details are both needed and present. */
    private Optional<FieldViolation> installationDateViolation(final PlaceOrderRequest request,
                                                               final boolean needsInstallation,
                                                               final LocalDate today) {
        if (!needsInstallation || request.installation() == null) {
            return Optional.empty();
        }
        final String field = "installation.preferredDate";
        final LocalDate preferred = request.installation().preferredDate();
        final LocalDate earliest = addWorkingDays(today, installation.minLeadWorkingDays());
        final LocalDate latest = today.plusDays(installation.maxHorizonDays());
        if (preferred.isBefore(earliest)) {
            return Optional.of(new FieldViolation(field, "INSTALLATION_DATE_INVALID", "must be on or after "
                    + earliest + " (" + installation.minLeadWorkingDays() + " working days' notice)"));
        }
        if (preferred.isAfter(latest)) {
            return Optional.of(new FieldViolation(field, "INSTALLATION_DATE_INVALID", "must be on or before " + latest));
        }
        if (!installation.isWorkingDay(preferred)) {
            return Optional.of(new FieldViolation(field, "INSTALLATION_DATE_INVALID",
                    preferred + " is not an installation day (Sundays and public holidays are excluded)"));
        }
        return Optional.empty();
    }

    /** The date that is {@code workingDays} working days after {@code from}, skipping Sundays and holidays. */
    private LocalDate addWorkingDays(final LocalDate from, final int workingDays) {
        if (workingDays == 0) {
            return from;
        }
        return Stream.iterate(from.plusDays(1), date -> date.plusDays(1))
                .filter(installation::isWorkingDay)
                .skip(workingDays - 1L)
                .findFirst()
                .orElseThrow();
    }
}
