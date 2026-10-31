package com.melita.orders.intake.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Installation date rules, from Spring Cloud Config. The date is a customer preference: Fulfilment owns
 * scheduling and confirms the appointment.
 */
@Validated
@ConfigurationProperties("installation")
public record InstallationProperties(
        @Min(0) @Max(30) Integer minLeadWorkingDays,
        @Min(1) @Max(365) Integer maxHorizonDays,
        Set<DayOfWeek> excludedDays,
        Set<LocalDate> publicHolidays) {

    public InstallationProperties {
        minLeadWorkingDays = minLeadWorkingDays == null ? 2 : minLeadWorkingDays;
        maxHorizonDays = maxHorizonDays == null ? 60 : maxHorizonDays;
        excludedDays = excludedDays == null ? Set.of(DayOfWeek.SUNDAY) : Set.copyOf(excludedDays);
        publicHolidays = publicHolidays == null ? Set.of() : Set.copyOf(publicHolidays);
    }

    public boolean isWorkingDay(final LocalDate date) {
        return !excludedDays.contains(date.getDayOfWeek()) && !publicHolidays.contains(date);
    }
}
