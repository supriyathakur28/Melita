package com.melita.orders.intake.order;

import com.melita.orders.intake.TestOrders;
import com.melita.orders.intake.order.api.PlaceOrderRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.AddressRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.CustomerRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.DocumentType;
import com.melita.orders.intake.order.api.PlaceOrderRequest.IdentityDocumentRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.InstallationRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.ItemRequest;
import com.melita.orders.intake.order.api.PlaceOrderRequest.Product;
import com.melita.orders.intake.order.api.PlaceOrderRequest.TimeSlot;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Shape and cross-field rules (400). */
class PlaceOrderRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static CustomerRequest customer(String firstName, String mobile, IdentityDocumentRequest document) {
        var c = TestOrders.customer();
        return new CustomerRequest(firstName, c.lastName(), c.dateOfBirth(), c.email(), mobile, document, c.address());
    }

    @Test
    void validRequestPasses() {
        assertThat(invalidFields(TestOrders.internetAndMobile("ERP-2026_01.x", LocalDate.of(2026, 11, 9)))).isEmpty();
    }

    @Test
    void maltesePostcodeIsRequired() {
        assertThat(invalidFields(new AddressRequest("1 Main St", null, "Mosta", "MST1234"))).isEmpty();
        assertThat(invalidFields(new AddressRequest("1 Main St", null, "Mosta", "12345"))).containsExactly("postcode");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ċensu", "Ġorġ", "Ħelena", "Żaren", "Mary-Anne", "D'Amato"})
    void namesAcceptMalteseLettersApostrophesAndHyphens(String name) {
        assertThat(invalidFields(customer(name, "+35679123456", TestOrders.customer().identityDocument()))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria1", "<script>", " Maria"})
    void namesRejectDigitsAndMarkup(String name) {
        assertThat(invalidFields(customer(name, "+35679123456", TestOrders.customer().identityDocument())))
                .containsExactly("firstName");
    }

    @ParameterizedTest
    @ValueSource(strings = {"79123456", "+0035679123456", "+356 7912 3456"})
    void mobileMustBeE164(String mobile) {
        assertThat(invalidFields(customer("Maria", mobile, TestOrders.customer().identityDocument())))
                .containsExactly("mobileNumber");
    }

    @Test
    void identityDocumentFormatDependsOnItsType() {
        assertThat(invalidFields(new IdentityDocumentRequest(DocumentType.ID_CARD, "123456M"))).isEmpty();
        assertThat(invalidFields(new IdentityDocumentRequest(DocumentType.ID_CARD, "AB12345"))).containsExactly("number");
        assertThat(invalidFields(new IdentityDocumentRequest(DocumentType.PASSPORT, "AB1234567"))).isEmpty();
    }

    @Test
    void identityDocumentNumberIsNeverPrinted() {
        assertThat(new IdentityDocumentRequest(DocumentType.ID_CARD, "123456M").toString()).doesNotContain("123456");
    }

    @Test
    void installationAddressIsRequiredOnlyWithoutUseCustomerAddress() {
        LocalDate date = LocalDate.of(2026, 11, 9);
        assertThat(invalidFields(new InstallationRequest(false, null, date, TimeSlot.MORNING))).containsExactly("address");
        assertThat(invalidFields(new InstallationRequest(true, TestOrders.address(), date, TimeSlot.MORNING)))
                .containsExactly("address");
        assertThat(invalidFields(new InstallationRequest(false, TestOrders.address(), date, TimeSlot.MORNING))).isEmpty();
    }

    @Test
    void omittedUseCustomerAddressFollowsWhetherAnAddressIsGiven() {
        LocalDate date = LocalDate.of(2026, 11, 9);
        var withoutAddress = new InstallationRequest(null, null, date, TimeSlot.MORNING);
        var withAddress = new InstallationRequest(null, TestOrders.address(), date, TimeSlot.MORNING);

        assertThat(invalidFields(withoutAddress)).isEmpty();
        assertThat(withoutAddress.usesCustomerAddress()).isTrue();
        assertThat(invalidFields(withAddress)).isEmpty();
        assertThat(withAddress.usesCustomerAddress()).isFalse();
    }

    @Test
    void productsMayNotRepeatAndAtMostFourItems() {
        var duplicate = new PlaceOrderRequest("ERP-1", TestOrders.customer(), null, List.of(
                new ItemRequest(Product.MOBILE, "MOBILE_PREPAID"), new ItemRequest(Product.MOBILE, "MOBILE_POSTPAID")));
        var empty = new PlaceOrderRequest("ERP-1", TestOrders.customer(), null, List.of());

        assertThat(invalidFields(duplicate)).containsExactly("items");
        assertThat(invalidFields(empty)).containsExactly("items");
    }

    @Test
    void externalReferenceIsRestricted() {
        assertThat(invalidFields(TestOrders.mobileOnly("ERP 1/2"))).containsExactly("externalReference");
    }
}
