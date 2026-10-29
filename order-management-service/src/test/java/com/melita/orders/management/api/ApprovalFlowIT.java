package com.melita.orders.management.api;

import com.melita.orders.events.OrderEvents;
import com.melita.orders.management.ManagementIntegrationTest;
import com.melita.orders.management.TestOrderEvents;
import com.melita.orders.management.order.CustomerOrderRepository;
import com.melita.orders.management.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApprovalFlowIT extends ManagementIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    CustomerOrderRepository orders;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static JwtRequestPostProcessor agent(String name) {
        return jwt().jwt(j -> j.claim("preferred_username", name)).authorities(new SimpleGrantedAuthority("ROLE_AGENT"));
    }

    private UUID heldOrder(LocalDate installationDate) {
        UUID orderId = UUID.randomUUID();
        var items = installationDate == null
                ? List.of(TestOrderEvents.item("MOBILE", "MOBILE_POSTPAID"))
                : List.of(TestOrderEvents.item("INTERNET", "INTERNET_1GBPS"));
        publishAccepted(TestOrderEvents.accepted(orderId, items, installationDate));
        await().atMost(Duration.ofSeconds(10)).until(() -> orders.findById(orderId)
                .map(o -> o.getStatus() == OrderStatus.PENDING_APPROVAL).orElse(false));
        return orderId;
    }

    @Test
    void pendingOrdersAreListedWithoutPersonalData() throws Exception {
        UUID orderId = heldOrder(null);

        mvc.perform(get("/api/v1/orders").param("status", "PENDING_APPROVAL").param("size", "100").with(agent("agent1")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains(orderId.toString()).doesNotContain("maria.borg@example.com"));
    }

    @Test
    void detailMasksTheIdentityDocumentNumber() throws Exception {
        UUID orderId = heldOrder(null);

        mvc.perform(get("/api/v1/orders/" + orderId).with(agent("agent1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.idDocumentNumberMasked").value("****56M"))
                .andExpect(jsonPath("$.approvalReasons[0]").value("PACKAGE:MOBILE_POSTPAID"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("123456M"));
    }

    @Test
    void approvedOrderIsSubmittedAndTheAgentIsAudited() throws Exception {
        UUID orderId = heldOrder(null);

        mvc.perform(post("/api/v1/orders/" + orderId + "/approve").with(agent("agent1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"ID checked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.decidedBy").value("agent1"));

        await().atMost(Duration.ofSeconds(15)).until(() -> orders.findById(orderId).orElseThrow().getStatus() == OrderStatus.SUBMITTED);
        await().atMost(Duration.ofSeconds(10)).until(() -> eventSeen(OrderEvents.ORDER_APPROVED, orderId.toString()));
    }

    @Test
    void rejectedOrderIsNeverSubmitted() throws Exception {
        UUID orderId = heldOrder(null);

        mvc.perform(post("/api/v1/orders/" + orderId + "/reject").with(agent("agent1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Customer failed the credit check\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        await().atMost(Duration.ofSeconds(10)).until(() -> eventSeen(OrderEvents.ORDER_REJECTED, orderId.toString()));
        assertThat(FULFILMENT.receivedFor(orderId.toString())).isEmpty();
    }

    @Test
    void approvingAfterTheSlotHasPassedWarnsAndFlagsFulfilment() throws Exception {
        UUID orderId = heldOrder(LocalDate.now().minusDays(1));

        mvc.perform(post("/api/v1/orders/" + orderId + "/approve").with(agent("agent1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0]").value("PREFERRED_SLOT_EXPIRED"));

        await().atMost(Duration.ofSeconds(15)).until(() -> !FULFILMENT.receivedFor(orderId.toString()).isEmpty());
        assertThat(FULFILMENT.receivedFor(orderId.toString()).getFirst().body()).contains("\"preferredSlotExpired\":true");
    }

    /** Repeated: the race is timing-dependent; it once exposed a MySQL deadlock (fixed with a row lock). */
    @org.junit.jupiter.api.RepeatedTest(10)
    void twoAgentsDecidingAtOnceGetOneWinnerAndOne409() throws Exception {
        UUID orderId = heldOrder(null);
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<MvcResult> approve = CompletableFuture.supplyAsync(() -> decide(start,
                post("/api/v1/orders/" + orderId + "/approve").with(agent("agent1"))));
        CompletableFuture<MvcResult> reject = CompletableFuture.supplyAsync(() -> decide(start,
                post("/api/v1/orders/" + orderId + "/reject").with(agent("agent2"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Duplicate of another order\"}")));
        start.countDown();

        List<Integer> statuses = List.of(approve.join().getResponse().getStatus(), reject.join().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from order_status_history where order_id = ?", Long.class,
                orderId.toString())).isEqualTo(2);
    }

    private MvcResult decide(CountDownLatch start,
                             org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        try {
            start.await();
            return mvc.perform(request).andReturn();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
