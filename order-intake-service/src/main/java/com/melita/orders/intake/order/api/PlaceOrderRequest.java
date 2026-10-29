package com.melita.orders.intake.order.api;

import com.melita.orders.intake.order.validation.NoDuplicateProducts;
import com.melita.orders.intake.order.validation.ValidIdentityDocument;
import com.melita.orders.intake.order.validation.ValidInstallationAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Public API request. Shape and cross-field rules are checked here (400); rules that need the catalogue or
 * the calendar are business rules (422), see {@code OrderRules}.
 */
@NoDuplicateProducts
public record PlaceOrderRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "may contain only letters, digits, '.', '_' and '-'")
        String externalReference,
        @NotNull @Valid CustomerRequest customer,
        @Valid InstallationRequest installation,
        @NotNull @Size(min = 1, max = 4) List<@NotNull @Valid ItemRequest> items) {

    static final String NAME = "[\\p{L}][\\p{L} '\\-]*";

    public record CustomerRequest(
            @NotBlank @Size(max = 100) @Pattern(regexp = NAME, message = "must contain only letters, spaces, apostrophes and hyphens")
            String firstName,
            @NotBlank @Size(max = 100) @Pattern(regexp = NAME, message = "must contain only letters, spaces, apostrophes and hyphens")
            String lastName,
            @NotNull @Past LocalDate dateOfBirth,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "\\+[1-9]\\d{7,14}", message = "must be in E.164 format, e.g. +35679123456")
            String mobileNumber,
            @NotNull @Valid IdentityDocumentRequest identityDocument,
            @NotNull @Valid AddressRequest address) {
    }

    @ValidIdentityDocument
    public record IdentityDocumentRequest(@NotNull DocumentType type, @NotBlank @Size(max = 20) String number) {

        /** Overridden so the document number never appears in logs. */
        @Override
        public String toString() {
            return "IdentityDocumentRequest[type=" + type + ", number=****]";
        }
    }

    public enum DocumentType { ID_CARD, PASSPORT }

    public record AddressRequest(
            @NotBlank @Size(max = 100) String line1,
            @Size(max = 100) String line2,
            @NotBlank @Size(max = 100) String locality,
            @NotBlank @Pattern(regexp = "[A-Z]{3} ?\\d{4}", message = "must be a Maltese postcode, e.g. MST 1234")
            String postcode) {
    }

    @ValidInstallationAddress
    public record InstallationRequest(
            boolean useCustomerAddress,
            @Valid AddressRequest address,
            @NotNull LocalDate preferredDate,
            @NotNull TimeSlot timeSlot) {
    }

    public enum TimeSlot { MORNING, AFTERNOON, EVENING }

    public enum Product { INTERNET, TV, TELEPHONY, MOBILE }

    public record ItemRequest(@NotNull Product product, @NotBlank @Size(max = 64) String packageCode) {
    }
}
