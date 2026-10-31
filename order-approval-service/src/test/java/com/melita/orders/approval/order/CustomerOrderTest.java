package com.melita.orders.approval.order;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerOrderTest {

    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");
    private static final Address ADDRESS = new Address("12, Triq il-Kbira", null, "Mosta", "MST 1234");

    private static CustomerOrder order(final List<String> approvalReasons) {
        final var customer = new CustomerDetails("Maria", "Borg", LocalDate.of(1990, 4, 12), "maria@example.com",
                "+35679123456", "ID_CARD", "123456M", ADDRESS);
        return CustomerOrder.receive(UUID.randomUUID(), "erp-partner", "ERP-1", "req-1", customer,
                new Installation(LocalDate.of(2026, 11, 9), "MORNING", ADDRESS),
                List.of(new CustomerOrder.NewItem("MOBILE", "MOBILE_POSTPAID", "Mobile post-paid")),
                approvalReasons, NOW);
    }

    @Test
    void orderNeedingApprovalIsHeld() {
        final CustomerOrder order = order(List.of("PACKAGE:MOBILE_POSTPAID"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_APPROVAL);
        assertThat(order.getNextAttemptAt()).isNull();
        assertThat(order.getHistory()).singleElement()
                .satisfies(h -> assertThat(h.getReason()).contains("PACKAGE:MOBILE_POSTPAID"));
    }

    @Test
    void orderWithoutApprovalIsReadyImmediately() {
        final CustomerOrder order = order(List.of());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_FULFILMENT);
        assertThat(order.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void approvalRecordsTheAgentAndMakesTheOrderReady() {
        final CustomerOrder order = order(List.of("PACKAGE:MOBILE_POSTPAID"));

        order.approve("agent1", "checked ID", NOW.plusSeconds(60));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_FULFILMENT);
        assertThat(order.getDecidedBy()).isEqualTo("agent1");
        assertThat(order.getHistory()).extracting(StatusChange::getActor).containsExactly("system", "agent1");
    }

    @Test
    void aDecidedOrderCannotBeDecidedAgain() {
        final CustomerOrder order = order(List.of("PACKAGE:MOBILE_POSTPAID"));
        order.reject("agent1", "failed the credit check", NOW);

        assertThatThrownBy(() -> order.approve("agent2", null, NOW))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    void anOrderThatNeedsNoApprovalCannotBeApproved() {
        assertThatThrownBy(() -> order(List.of()).approve("agent1", null, NOW))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void temporaryFulfilmentFailureKeepsTheOrderReadyWithALaterAttempt() {
        final CustomerOrder order = order(List.of());

        order.recordFailedAttempt("HTTP 503", NOW.plusSeconds(10), NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_FULFILMENT);
        assertThat(order.getAttempts()).isEqualTo(1);
        assertThat(order.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void submittedAndFailedAreTerminal() {
        final CustomerOrder submitted = order(List.of());
        submitted.markSubmitted("FUL-1", NOW);
        final CustomerOrder failed = order(List.of());
        failed.markFulfilmentFailed("HTTP 422", NOW);

        assertThat(submitted.getFulfilmentReference()).isEqualTo("FUL-1");
        assertThatThrownBy(() -> submitted.markFulfilmentFailed("late", NOW)).isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> failed.recordFailedAttempt("x", NOW, NOW)).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void installationIsExpiredOnOrAfterThePreferredDate() {
        final var installation = new Installation(LocalDate.of(2026, 11, 9), "MORNING", ADDRESS);

        assertThat(installation.isExpiredOn(LocalDate.of(2026, 11, 8))).isFalse();
        assertThat(installation.isExpiredOn(LocalDate.of(2026, 11, 9))).isTrue();
    }

    @Test
    void preferredSlotExpiryIsDecidedOnceSoRetriesSendTheSameBody() {
        final CustomerOrder order = order(List.of());

        assertThat(order.preferredSlotExpired(LocalDate.of(2026, 11, 8))).isFalse();
        assertThat(order.preferredSlotExpired(LocalDate.of(2026, 11, 10))).isFalse();
    }

    @Test
    void longErrorsAreTruncatedToFitTheirColumns() {
        final CustomerOrder order = order(List.of());

        order.recordFailedAttempt("x".repeat(1000), NOW, NOW);

        assertThat(order.getLastError()).hasSize(255);
    }

    @Test
    void customerToStringMasksTheIdNumber() {
        assertThat(order(List.of()).getCustomer().toString()).contains("****56M").doesNotContain("123456M");
    }
}
