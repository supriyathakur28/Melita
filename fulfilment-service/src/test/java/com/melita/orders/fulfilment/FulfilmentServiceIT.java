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

    private MvcResult submit(String key, Object body) throws Exception {
        return mvc.perform(post("/api/v1/fulfilment-orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_fulfilment:submit")))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)))
                .andReturn();
    }

    private String reference(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString()).get("fulfilmentReference").asString();
    }

    @Test
    void approvedOrderIsStoredAsAFulfilmentOrderWithTheIdNumberEncrypted() throws Exception {
        UUID orderId = UUID.randomUUID();

        MvcResult created = submit(orderId.toString(), TestRequests.request(orderId));

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String reference = reference(created);
        assertThat(reference).matches("FUL-[0-9A-F]{10}");
        String stored = jdbc.queryForObject("select id_document_number from fulfilment_order where order_id = ?",
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
        UUID orderId = UUID.randomUUID();
        String first = reference(submit(orderId.toString(), TestRequests.request(orderId)));

        MvcResult retry = submit(orderId.toString(), TestRequests.request(orderId));

        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(reference(retry)).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from fulfilment_order where order_id = ?", Long.class,
                orderId.toString())).isEqualTo(1);
    }

    @Test
    void sameKeyWithADifferentBodyIs409() throws Exception {
        UUID orderId = UUID.randomUUID();
        submit(orderId.toString(), TestRequests.request(orderId));
        var changed = TestRequests.request(orderId);
        changed = new com.melita.orders.fulfilment.api.FulfilmentOrderRequest(changed.orderId(), "CHANGED-REF",
                changed.partnerId(), changed.customer(), changed.installation(), changed.items());

        MvcResult result = submit(orderId.toString(), changed);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void sameOrderUnderAnotherKeyIs409() throws Exception {
        UUID orderId = UUID.randomUUID();
        String reference = reference(submit(orderId.toString(), TestRequests.request(orderId)));

        MvcResult result = submit("another-key-" + orderId, TestRequests.request(orderId));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("ORDER_ALREADY_SUBMITTED", reference);
    }

    @Test
    void parallelSubmissionsWithOneKeyCreateOneOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        int callers = 6;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                Callable<MvcResult> call = () -> {
                    start.await();
                    return submit(orderId.toString(), TestRequests.request(orderId));
                };
                futures.add(pool.submit(call));
            }
            start.countDown();
            List<String> references = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get();
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
