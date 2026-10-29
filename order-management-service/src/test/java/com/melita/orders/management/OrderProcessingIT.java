package com.melita.orders.management;

import com.melita.orders.events.OrderEvents;
import com.melita.orders.management.order.CustomerOrderRepository;
import com.melita.orders.management.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.core.Message;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Task 2 end to end: consume order.accepted, store, hold or submit to Fulfilment. */
class OrderProcessingIT extends ManagementIntegrationTest {

    @Autowired
    CustomerOrderRepository orders;
    @Autowired
    JdbcTemplate jdbc;

    private OrderStatus statusOf(UUID orderId) {
        return orders.findById(orderId).map(o -> o.getStatus()).orElse(null);
    }

    @Test
    void orderWithAPackageNeedingApprovalIsHeldAndAgentsAreAsked() {
        UUID orderId = UUID.randomUUID();
        publishAccepted(TestOrderEvents.accepted(orderId,
                List.of(TestOrderEvents.item("MOBILE", "MOBILE_POSTPAID")), null));

        await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId) == OrderStatus.PENDING_APPROVAL);
        await().atMost(Duration.ofSeconds(10)).until(() -> eventSeen(OrderEvents.ORDER_APPROVAL_REQUESTED, orderId.toString()));
        assertThat(FULFILMENT.receivedFor(orderId.toString())).isEmpty();
    }

    @Test
    void identityDocumentNumberIsEncryptedAtRest() {
        UUID orderId = UUID.randomUUID();
        publishAccepted(TestOrderEvents.accepted(orderId, List.of(TestOrderEvents.item("MOBILE", "MOBILE_POSTPAID")), null));
        await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId) != null);

        String stored = jdbc.queryForObject("select id_document_number from customer_order where id = ?",
                String.class, orderId.toString());
        assertThat(stored).startsWith("v1:").doesNotContain("123456M");
        assertThat(orders.findById(orderId).orElseThrow().getCustomer().idDocumentNumber()).isEqualTo("123456M");
    }

    @Test
    void orderNotNeedingApprovalIsSubmittedToFulfilmentWithTokenAndIdempotencyKey() {
        UUID orderId = UUID.randomUUID();
        publishAccepted(TestOrderEvents.accepted(orderId, List.of(
                TestOrderEvents.item("INTERNET", "INTERNET_250MBPS"), TestOrderEvents.item("MOBILE", "MOBILE_PREPAID")),
                LocalDate.now().plusDays(14)));

        await().atMost(Duration.ofSeconds(15)).until(() -> statusOf(orderId) == OrderStatus.SUBMITTED);
        var calls = FULFILMENT.receivedFor(orderId.toString());
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().authorization()).isEqualTo("Bearer test-token");
        assertThat(calls.getFirst().body()).contains("\"idDocumentNumber\":\"123456M\"", "\"preferredSlotExpired\":false");
        assertThat(orders.findById(orderId).orElseThrow().getFulfilmentReference()).startsWith("FUL-");
        await().atMost(Duration.ofSeconds(10)).until(() -> eventSeen(OrderEvents.ORDER_SUBMITTED, orderId.toString()));
    }

    @Test
    void duplicateDeliveryCreatesOneOrder() {
        UUID orderId = UUID.randomUUID();
        var event = publishAccepted(TestOrderEvents.accepted(orderId,
                List.of(TestOrderEvents.item("MOBILE", "MOBILE_POSTPAID")), null));
        publishRaw(event.eventId().toString(), jsonMapper.writeValueAsString(event));
        publishRaw(event.eventId().toString(), jsonMapper.writeValueAsString(event));

        await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId) != null);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> true);
        assertThat(jdbc.queryForObject("select count(*) from customer_order where id = ?", Long.class,
                orderId.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from order_status_history where order_id = ?", Long.class,
                orderId.toString())).isEqualTo(1);
    }

    @Test
    void unreadableMessageGoesToTheDeadLetterQueue() {
        publishRaw("poison-" + UUID.randomUUID(), "{\"this is\": not json");

        Message dead = rabbitTemplate.receive("order-management.order-accepted.dlq", 15_000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains("not json");
    }

    @Test
    void temporaryFulfilmentFailureIsRetriedWithTheSameKey() {
        UUID orderId = UUID.randomUUID();
        FULFILMENT.script(orderId.toString(), 503);
        publishAccepted(TestOrderEvents.accepted(orderId, List.of(TestOrderEvents.item("MOBILE", "MOBILE_PREPAID")), null));

        await().atMost(Duration.ofSeconds(15)).until(() -> statusOf(orderId) == OrderStatus.SUBMITTED);
        assertThat(FULFILMENT.receivedFor(orderId.toString())).hasSize(2)
                .allSatisfy(call -> assertThat(call.idempotencyKey()).isEqualTo(orderId.toString()));
        assertThat(orders.findById(orderId).orElseThrow().getAttempts()).isEqualTo(1);
    }

    @Test
    void rejectedByFulfilmentFailsTheOrderWithoutRetry() {
        UUID orderId = UUID.randomUUID();
        FULFILMENT.script(orderId.toString(), 422);
        publishAccepted(TestOrderEvents.accepted(orderId, List.of(TestOrderEvents.item("MOBILE", "MOBILE_PREPAID")), null));

        await().atMost(Duration.ofSeconds(15)).until(() -> statusOf(orderId) == OrderStatus.FULFILMENT_FAILED);
        assertThat(FULFILMENT.receivedFor(orderId.toString())).hasSize(1);
        assertThat(orders.findById(orderId).orElseThrow().getLastError()).contains("422");
        await().atMost(Duration.ofSeconds(10)).until(() -> eventSeen(OrderEvents.ORDER_FULFILMENT_FAILED, orderId.toString()));
    }

    @Test
    void retriesStopAfterTheConfiguredNumberOfAttempts() {
        UUID orderId = UUID.randomUUID();
        FULFILMENT.script(orderId.toString(), 503, 503, 503, 503);
        publishAccepted(TestOrderEvents.accepted(orderId, List.of(TestOrderEvents.item("MOBILE", "MOBILE_PREPAID")), null));

        await().atMost(Duration.ofSeconds(20)).until(() -> statusOf(orderId) == OrderStatus.FULFILMENT_FAILED);
        assertThat(FULFILMENT.receivedFor(orderId.toString())).hasSize(3);
        assertThat(orders.findById(orderId).orElseThrow().getLastError()).startsWith("retries exhausted");
    }
}
