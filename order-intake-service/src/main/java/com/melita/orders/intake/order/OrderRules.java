package com.melita.orders.intake.order;

import com.melita.orders.intake.catalogue.Catalogue;
import com.melita.orders.intake.order.api.PlaceOrderRequest;
import com.melita.orders.platform.web.FieldViolation;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Business rules that need reference data or the calendar (422). Every violation is collected, so a partner
 * can fix everything in one round-trip. "Today" is the date in Malta, from the injected clock.
 */
@Component
public class OrderRules {

    static final int MINIMUM_AGE = 18;

    private final InstallationProperties installation;
    private final Clock clock;

    public OrderRules(InstallationProperties installation, Clock clock) {
        this.installation = installation;
        this.clock = clock;
    }

    public List<FieldViolation> check(PlaceOrderRequest request, Catalogue catalogue) {
        List<FieldViolation> violations = new ArrayList<>();
        LocalDate today = LocalDate.now(clock.withZone(installation.zone()));

        boolean needsInstallation = checkItems(request, catalogue, violations);
        checkInstallationPresence(request, needsInstallation, violations);
        checkAge(request, today, violations);
        if (request.installation() != null && needsInstallation) {
            checkInstallationDate(request.installation().preferredDate(), today, violations);
        }
        return violations;
    }

    /** @return whether any item needs a technician visit. */
    private static boolean checkItems(PlaceOrderRequest request, Catalogue catalogue, List<FieldViolation> violations) {
        boolean needsInstallation = false;
        for (int i = 0; i < request.items().size(); i++) {
            PlaceOrderRequest.ItemRequest item = request.items().get(i);
            String field = "items[" + i + "].packageCode";
            Optional<Catalogue.Offer> offer = catalogue.findPackage(item.packageCode());
            if (offer.isEmpty() || !offer.get().pkg().active() || !offer.get().product().active()) {
                violations.add(new FieldViolation(field, "UNKNOWN_PACKAGE",
                        "'" + item.packageCode() + "' is not an available package"));
                continue;
            }
            if (!offer.get().product().code().equals(item.product().name())) {
                violations.add(new FieldViolation(field, "PACKAGE_PRODUCT_MISMATCH",
                        "'" + item.packageCode() + "' is a " + offer.get().product().code() + " package, not "
                                + item.product().name()));
                continue;
            }
            needsInstallation |= offer.get().product().requiresInstallation();
        }
        return needsInstallation;
    }

    private static void checkInstallationPresence(PlaceOrderRequest request, boolean needsInstallation,
                                                  List<FieldViolation> violations) {
        if (needsInstallation && request.installation() == null) {
            violations.add(new FieldViolation("installation", "INSTALLATION_REQUIRED",
                    "installation details are required for Internet, TV and Telephony"));
        } else if (!needsInstallation && request.installation() != null) {
            violations.add(new FieldViolation("installation", "INSTALLATION_NOT_APPLICABLE",
                    "installation details must be omitted when no product needs a technician visit"));
        }
    }

    private static void checkAge(PlaceOrderRequest request, LocalDate today, List<FieldViolation> violations) {
        LocalDate dateOfBirth = request.customer().dateOfBirth();
        if (Period.between(dateOfBirth, today).getYears() < MINIMUM_AGE) {
            violations.add(new FieldViolation("customer.dateOfBirth", "CUSTOMER_UNDERAGE",
                    "the customer must be at least " + MINIMUM_AGE));
        }
    }

    private void checkInstallationDate(LocalDate preferred, LocalDate today, List<FieldViolation> violations) {
        String field = "installation.preferredDate";
        LocalDate earliest = addWorkingDays(today, installation.minLeadWorkingDays());
        LocalDate latest = today.plusDays(installation.maxHorizonDays());
        if (preferred.isBefore(earliest)) {
            violations.add(new FieldViolation(field, "INSTALLATION_DATE_INVALID",
                    "must be on or after " + earliest + " (" + installation.minLeadWorkingDays() + " working days' notice)"));
        } else if (preferred.isAfter(latest)) {
            violations.add(new FieldViolation(field, "INSTALLATION_DATE_INVALID",
                    "must be on or before " + latest));
        } else if (!installation.isWorkingDay(preferred)) {
            violations.add(new FieldViolation(field, "INSTALLATION_DATE_INVALID",
                    preferred + " is not an installation day (Sundays and public holidays are excluded)"));
        }
    }

    private LocalDate addWorkingDays(LocalDate from, int workingDays) {
        LocalDate date = from;
        int added = 0;
        while (added < workingDays) {
            date = date.plusDays(1);
            if (installation.isWorkingDay(date)) {
                added++;
            }
        }
        return date;
    }
}
