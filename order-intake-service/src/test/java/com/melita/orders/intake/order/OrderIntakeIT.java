package com.melita.orders.intake.order;

import com.melita.orders.events.EventEnvelope;
import com.melita.orders.events.OrderAccepted;
import com.melita.orders.events.OrderEvents;
import com.melita.orders.intake.IntakeIntegrationTest;
import com.melita.orders.intake.TestOrders;
import com.melita.orders.platform.messaging.Queues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(OrderIntakeIT.EventCapture.class)
class OrderIntakeIT extends IntakeIntegrationTest {

    static final String CAPTURE_QUEUE = "it.order-accepted";

    @TestConfiguration
    static class EventCapture {
        @Bean
        Declarables captureQueue() {
            var queue = QueueBuilder.durable(CAPTURE_QUEUE).build();
            return new Declarables(queue, BindingBuilder.bind(queue).to(Queues.eventsExchange()).with(OrderEvents.ORDER_ACCEPTED));
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void drainCapturedEvents() {
        while (rabbitTemplate.receive(CAPTURE_QUEUE) != null) {
            // start each test with an empty queue
        }
    }

    private static JwtRequestPostProcessor partner(String partnerId) {
        return jwt().jwt(j -> j.claim("partner_id", partnerId)).authorities(
                new SimpleGrantedAuthority("SCOPE_orders:write"), new SimpleGrantedAuthority("SCOPE_orders:read"));
    }

    private MvcResult place(String partnerId, String key, Object body) throws Exception {
        return mvc.perform(post("/api/v1/orders").with(partner(partnerId))
                        .header("Idempotency-Key", key).header("X-Request-Id", "req-" + key)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)))
                .andReturn();
    }

    /** Collects order.accepted events for a few seconds and counts those for the given order. */
    private long acceptedEventsFor(String orderId) {
        long count = 0;
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(CAPTURE_QUEUE, 500);
            if (message != null && new String(message.getBody(), StandardCharsets.UTF_8).contains(orderId)) {
                count++;
            }
        }
        return count;
    }

    private static String uniqueReference() {
        return "ERP-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }

    @Test
    void acceptedOrderIsPublishedAsOrderAcceptedWithTheFullOrder() throws Exception {
        String reference = uniqueReference();
        String key = uniqueKey();
        var request = TestOrders.internetAndMobile(reference, TestOrders.validInstallationDate());

        MvcResult result = place("erp-partner", key, request);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String orderId = jsonMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asString();

        Message message = rabbitTemplate.receive(CAPTURE_QUEUE, 10_000);
        assertThat(message).isNotNull();
        EventEnvelope<OrderAccepted> event = jsonMapper.readValue(
                new String(message.getBody(), StandardCharsets.UTF_8), new TypeReference<>() { });
        assertThat(event.eventType()).isEqualTo(OrderEvents.ORDER_ACCEPTED);
        assertThat(event.correlationId()).isEqualTo("req-" + key);
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        OrderAccepted order = event.data();
        assertThat(order.orderId()).hasToString(orderId);
        assertThat(order.partnerId()).isEqualTo("erp-partner");
        assertThat(order.externalReference()).isEqualTo(reference);
        assertThat(order.installation().address().postcode()).isEqualTo("MST 1234");
        assertThat(order.items()).extracting(OrderAccepted.Item::packageName)
                .containsExactly("Internet 1Gbps", "Mobile post-paid");

        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject(
                "select count(*) from outbox_event where payload is not null", Long.class) == 0);
    }

    @Test
    void parallelRequestsWithTheSameKeyCreateOneOrder() throws Exception {
        String key = uniqueKey();
        var request = TestOrders.mobileOnly(uniqueReference());
        int callers = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                Callable<MvcResult> call = () -> {
                    start.await();
                    return place("erp-partner", key, request);
                };
                results.add(pool.submit(call));
            }
            start.countDown();

            List<String> orderIds = new ArrayList<>();
            for (Future<MvcResult> future : results) {
                MvcResult result = future.get();
                assertThat(result.getResponse().getStatus()).isEqualTo(201);
                orderIds.add(jsonMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asString());
            }
            assertThat(orderIds).hasSize(callers).containsOnly(orderIds.getFirst());
            assertThat(jdbc.queryForObject("select count(*) from accepted_order where id = ?", Long.class,
                    orderIds.getFirst())).isEqualTo(1);
            assertThat(acceptedEventsFor(orderIds.getFirst())).isEqualTo(1);
        }
    }

    @Test
    void retryWithTheSameKeyAndBodyReplaysTheOriginalResponse() throws Exception {
        String key = uniqueKey();
        var request = TestOrders.mobileOnly(uniqueReference());

        MvcResult first = place("erp-partner", key, request);
        MvcResult retry = place("erp-partner", key, request);

        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
    }

    @Test
    void sameKeyWithADifferentBodyIs422() throws Exception {
        String key = uniqueKey();
        place("erp-partner", key, TestOrders.mobileOnly(uniqueReference()));

        mvc.perform(post("/api/v1/orders").with(partner("erp-partner")).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(TestOrders.mobileOnly(uniqueReference()))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void sameReferenceWithANewKeyIs409WithTheExistingOrder() throws Exception {
        String reference = uniqueReference();
        MvcResult first = place("erp-partner", uniqueKey(), TestOrders.mobileOnly(reference));
        String orderId = jsonMapper.readTree(first.getResponse().getContentAsString()).get("orderId").asString();

        mvc.perform(post("/api/v1/orders").with(partner("erp-partner")).header("Idempotency-Key", uniqueKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(TestOrders.mobileOnly(reference))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EXTERNAL_REFERENCE"))
                .andExpect(jsonPath("$.existingOrderId").value(orderId));
    }

    @Test
    void theSameReferenceIsFineForAnotherPartner() throws Exception {
        String reference = uniqueReference();
        place("erp-partner", uniqueKey(), TestOrders.mobileOnly(reference));

        assertThat(place("other-partner", uniqueKey(), TestOrders.mobileOnly(reference)).getResponse().getStatus())
                .isEqualTo(201);
    }

    @Test
    void partnersCanReadOnlyTheirOwnOrders() throws Exception {
        MvcResult created = place("erp-partner", uniqueKey(), TestOrders.mobileOnly(uniqueReference()));
        String orderId = jsonMapper.readTree(created.getResponse().getContentAsString()).get("orderId").asString();

        mvc.perform(get("/api/v1/orders/" + orderId).with(partner("erp-partner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(get("/api/v1/orders/" + orderId).with(partner("other-partner")))
                .andExpect(status().isNotFound());
    }

    @Test
    void businessRuleViolationsAre422AndNothingIsStored() throws Exception {
        String reference = uniqueReference();
        var request = new com.melita.orders.intake.order.api.PlaceOrderRequest(reference, TestOrders.customer(), null,
                List.of(new com.melita.orders.intake.order.api.PlaceOrderRequest.ItemRequest(
                        com.melita.orders.intake.order.api.PlaceOrderRequest.Product.INTERNET, "INTERNET_1GBPS")));

        MvcResult result = place("erp-partner", uniqueKey(), request);

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(result.getResponse().getContentAsString()).contains("INSTALLATION_REQUIRED");
        assertThat(jdbc.queryForObject("select count(*) from accepted_order where external_reference = ?",
                Long.class, reference)).isZero();
    }

    @Test
    void unknownJsonPropertiesAreRejected() throws Exception {
        String body = jsonMapper.writeValueAsString(TestOrders.mobileOnly(uniqueReference()))
                .replaceFirst("\\{", "{\"externalRefrence\":\"typo\",");

        mvc.perform(post("/api/v1/orders").with(partner("erp-partner")).header("Idempotency-Key", uniqueKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void catalogueListsActiveProductsAndPackages() throws Exception {
        String body = mvc.perform(get("/api/v1/catalogue/products").with(partner("erp-partner")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> products = jsonMapper.readValue(body, new TypeReference<>() { });
        assertThat(products).extracting(p -> p.get("code")).containsExactly("INTERNET", "MOBILE", "TELEPHONY", "TV");
    }
}
