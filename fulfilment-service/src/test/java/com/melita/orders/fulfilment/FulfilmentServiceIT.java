package com.melita.orders.fulfilment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real service against MySQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FulfilmentServiceIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("fulfilment");

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    JdbcTemplate jdbc;

    private MvcResult submit(final String key, final Object body) throws Exception {
        return mvc.perform(post("/api/v1/fulfilment-orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_fulfilment:submit")))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)))
                .andReturn();
    }

    private String reference(final MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString()).get("fulfilmentReference").asString();
    }

    @Test
    void approvedOrderIsStoredAsAFulfilmentOrderWithTheIdNumberEncrypted() throws Exception {
        final UUID orderId = UUID.randomUUID();

        final MvcResult created = submit(orderId.toString(), TestRequests.request(orderId));

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        final String reference = reference(created);
        assertThat(reference).matches("FUL-[0-9A-F]{10}");
        final String stored = jdbc.queryForObject("select id_document_number from fulfilment_order where order_id = ?",
                String.class, orderId.toString());
        assertThat(stored).startsWith("v1:").doesNotContain("123456M");

        mvc.perform(get("/api/v1/fulfilment-orders/" + reference)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_fulfilment:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.idDocumentNumberMasked").value("****56M"))
                .andExpect(jsonPath("$.items[0].packageCode").value("INTERNET_1GBPS"));
    }

    @Test
    void retryWithTheSameKeyAndBodyReturnsTheSameOrder() throws Exception {
        final UUID orderId = UUID.randomUUID();
        final String first = reference(submit(orderId.toString(), TestRequests.request(orderId)));

        final MvcResult retry = submit(orderId.toString(), TestRequests.request(orderId));

        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(reference(retry)).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from fulfilment_order where order_id = ?", Long.class,
                orderId.toString())).isEqualTo(1);
    }

    @Test
    void sameKeyWithADifferentBodyIs409() throws Exception {
        final UUID orderId = UUID.randomUUID();
        submit(orderId.toString(), TestRequests.request(orderId));
        final var original = TestRequests.request(orderId);
        final var changed = new com.melita.orders.fulfilment.api.FulfilmentOrderRequest(original.orderId(),
                "CHANGED-REF", original.partnerId(), original.customer(), original.installation(), original.items());

        final MvcResult result = submit(orderId.toString(), changed);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void sameOrderUnderAnotherKeyIs409() throws Exception {
        final UUID orderId = UUID.randomUUID();
        final String reference = reference(submit(orderId.toString(), TestRequests.request(orderId)));

        final MvcResult result = submit("another-key-" + orderId, TestRequests.request(orderId));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("ORDER_ALREADY_SUBMITTED", reference);
    }

    @Test
    void parallelSubmissionsWithOneKeyCreateOneOrder() throws Exception {
        final UUID orderId = UUID.randomUUID();
        final int callers = 6;
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<MvcResult>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                final Callable<MvcResult> call = () -> {
                    start.await();
                    return submit(orderId.toString(), TestRequests.request(orderId));
                };
                futures.add(pool.submit(call));
            }
            start.countDown();
            final List<String> references = new ArrayList<>();
            for (final Future<MvcResult> future : futures) {
                final MvcResult result = future.get();
                assertThat(result.getResponse().getStatus()).isIn(200, 201);
                references.add(reference(result));
            }
            assertThat(references).containsOnly(references.getFirst());
        }
        assertThat(jdbc.queryForObject("select count(*) from fulfilment_order where order_id = ?", Long.class,
                orderId.toString())).isEqualTo(1);
    }

    @Test
    void unknownReferenceIs404() throws Exception {
        mvc.perform(get("/api/v1/fulfilment-orders/FUL-0000000000")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_fulfilment:read"))))
                .andExpect(status().isNotFound());
    }
}
