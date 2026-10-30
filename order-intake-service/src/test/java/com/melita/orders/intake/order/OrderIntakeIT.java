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
import java.util.stream.Stream;

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
            final var queue = QueueBuilder.durable(CAPTURE_QUEUE).build();
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

    private static JwtRequestPostProcessor partner(final String partnerId) {
        return jwt().jwt(j -> j.claim("partner_id", partnerId)).authorities(
                new SimpleGrantedAuthority("SCOPE_orders:write"), new SimpleGrantedAuthority("SCOPE_orders:read"));
    }

    private MvcResult place(final String partnerId, final String key, final Object body) throws Exception {
        return mvc.perform(post("/api/v1/orders").with(partner(partnerId))
                        .header("Idempotency-Key", key).header("X-Request-Id", "req-" + key)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)))
                .andReturn();
    }

    /** Collects order.accepted events for a few seconds and counts those for the given order. */
    private long acceptedEventsFor(final String orderId) {
        final long deadline = System.currentTimeMillis() + 3_000;
        return Stream.generate(() -> rabbitTemplate.receive(CAPTURE_QUEUE, 500))
                .takeWhile(message -> System.currentTimeMillis() < deadline)
                .filter(message -> message != null
                        && new String(message.getBody(), StandardCharsets.UTF_8).contains(orderId))
                .count();
    }

    private static String uniqueReference() {
        return "ERP-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }

    @Test
    void acceptedOrderIsPublishedAsOrderAcceptedWithTheFullOrder() throws Exception {
        final String reference = uniqueReference();
        final String key = uniqueKey();
        final var request = TestOrders.internetAndMobile(reference, TestOrders.validInstallationDate());

        final MvcResult result = place("erp-partner", key, request);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        final String orderId = jsonMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asString();

        final Message message = rabbitTemplate.receive(CAPTURE_QUEUE, 10_000);
        assertThat(message).isNotNull();
        final EventEnvelope<OrderAccepted> event = jsonMapper.readValue(
                new String(message.getBody(), StandardCharsets.UTF_8), new TypeReference<>() {
                });
        assertThat(event.eventType()).isEqualTo(OrderEvents.ORDER_ACCEPTED);
        assertThat(event.correlationId()).isEqualTo("req-" + key);
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        final OrderAccepted order = event.data();
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
        final String key = uniqueKey();
        final var request = TestOrders.mobileOnly(uniqueReference());
        final int callers = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<MvcResult>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                final Callable<MvcResult> call = () -> {
                    start.await();
                    return place("erp-partner", key, request);
                };
                results.add(pool.submit(call));
            }
            start.countDown();

            final List<String> orderIds = new ArrayList<>();
            for (final Future<MvcResult> future : results) {
                final MvcResult result = future.get();
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
        final String key = uniqueKey();
        final var request = TestOrders.mobileOnly(uniqueReference());

        final MvcResult first = place("erp-partner", key, request);
        final MvcResult retry = place("erp-partner", key, request);

        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
    }

    @Test
    void sameKeyWithADifferentBodyIs422() throws Exception {
        final String key = uniqueKey();
        place("erp-partner", key, TestOrders.mobileOnly(uniqueReference()));

        mvc.perform(post("/api/v1/orders").with(partner("erp-partner")).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(TestOrders.mobileOnly(uniqueReference()))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void sameReferenceWithANewKeyIs409WithTheExistingOrder() throws Exception {
        final String reference = uniqueReference();
        final MvcResult first = place("erp-partner", uniqueKey(), TestOrders.mobileOnly(reference));
        final String orderId = jsonMapper.readTree(first.getResponse().getContentAsString()).get("orderId").asString();

        mvc.perform(post("/api/v1/orders").with(partner("erp-partner")).header("Idempotency-Key", uniqueKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(TestOrders.mobileOnly(reference))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EXTERNAL_REFERENCE"))
                .andExpect(jsonPath("$.existingOrderId").value(orderId));
    }

    @Test
    void idempotencyKeysAreCaseSensitive() throws Exception {
        final String key = "CaseKey-" + UUID.randomUUID().toString().substring(0, 8);
        place("erp-partner", key, TestOrders.mobileOnly(uniqueReference()));

        assertThat(place("erp-partner", key.toLowerCase(), TestOrders.mobileOnly(uniqueReference()))
                .getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void theSameReferenceIsFineForAnotherPartner() throws Exception {
        final String reference = uniqueReference();
        place("erp-partner", uniqueKey(), TestOrders.mobileOnly(reference));

        assertThat(place("other-partner", uniqueKey(), TestOrders.mobileOnly(reference)).getResponse().getStatus())
                .isEqualTo(201);
    }

    @Test
    void partnersCanReadOnlyTheirOwnOrders() throws Exception {
        final MvcResult created = place("erp-partner", uniqueKey(), TestOrders.mobileOnly(uniqueReference()));
        final String orderId =
                jsonMapper.readTree(created.getResponse().getContentAsString()).get("orderId").asString();

        mvc.perform(get("/api/v1/orders/" + orderId).with(partner("erp-partner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(get("/api/v1/orders/" + orderId).with(partner("other-partner")))
                .andExpect(status().isNotFound());
    }

    @Test
    void businessRuleViolationsAre422AndNothingIsStored() throws Exception {
        final String reference = uniqueReference();
        final var request =
                new com.melita.orders.intake.order.api.PlaceOrderRequest(reference, TestOrders.customer(), null,
                List.of(new com.melita.orders.intake.order.api.PlaceOrderRequest.ItemRequest(
                        com.melita.orders.intake.order.api.PlaceOrderRequest.Product.INTERNET, "INTERNET_1GBPS")));

        final MvcResult result = place("erp-partner", uniqueKey(), request);

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(result.getResponse().getContentAsString()).contains("INSTALLATION_REQUIRED");
        assertThat(jdbc.queryForObject("select count(*) from accepted_order where external_reference = ?",
                Long.class, reference)).isZero();
    }

    @Test
    void unknownJsonPropertiesAreRejected() throws Exception {
        final String body = jsonMapper.writeValueAsString(TestOrders.mobileOnly(uniqueReference()))
                .replaceFirst("\\{", "{\"externalRefrence\":\"typo\",");

        mvc.perform(post("/api/v1/orders").with(partner("erp-partner")).header("Idempotency-Key", uniqueKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void catalogueListsActiveProductsAndPackages() throws Exception {
        final String body = mvc.perform(get("/api/v1/catalogue/products").with(partner("erp-partner")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        final List<Map<String, Object>> products = jsonMapper.readValue(body, new TypeReference<>() {
        });
        assertThat(products).extracting(p -> p.get("code")).containsExactly("INTERNET", "MOBILE", "TELEPHONY", "TV");
    }
}
