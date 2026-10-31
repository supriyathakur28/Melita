package com.melita.orders.intake.order;

import com.melita.orders.intake.TestOrders;
import com.melita.orders.intake.config.SecurityConfig;
import com.melita.orders.intake.order.api.OrderReceipt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller contract and security matrix, with the service mocked. */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderApiWebTest {

    private static final UUID ORDER_ID = UUID.fromString("3f2c8e1a-8d4b-4c71-9a55-0e6b2f1d7c90");
    private static final OrderReceipt RECEIPT =
            new OrderReceipt(ORDER_ID, "ERP-1", "ACCEPTED", Instant.parse("2026-11-04T09:00:00Z"));

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper jsonMapper;
    @MockitoBean
    OrderIntakeService service;

    private String validBody() {
        return jsonMapper.writeValueAsString(TestOrders.internetAndMobile("ERP-1", LocalDate.of(2026, 11, 9)));
    }

    private static JwtRequestPostProcessor partner(final String... scopes) {
        return jwt().jwt(j -> j.claim("partner_id", "erp-partner"))
                .authorities(java.util.Arrays.stream(scopes)
                        .map(s -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + s))
                        .toList());
    }

    @Test
    void placingAnOrderNeedsAToken() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(service);
    }

    @Test
    void placingAnOrderNeedsTheWriteScope() throws Exception {
        mvc.perform(post("/api/v1/orders").with(partner("orders:read")).header("Idempotency-Key", "key-00000001")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void tokenWithoutPartnerClaimIsRejected() throws Exception {
        mvc.perform(post("/api/v1/orders").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write")))
                        .header("Idempotency-Key", "key-00000001")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PARTNER_NOT_CONFIGURED"));
    }

    @Test
    void idempotencyKeyIsRequired() throws Exception {
        mvc.perform(post("/api/v1/orders").with(partner("orders:write"))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.errors[0].message").value("is required"));
        verifyNoInteractions(service);
    }

    @Test
    void malformedIdempotencyKeyIs400() throws Exception {
        mvc.perform(post("/api/v1/orders").with(partner("orders:write")).header("Idempotency-Key", "short")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
        verifyNoInteractions(service);
    }

    @Test
    void invalidBodyIs400WithFieldErrors() throws Exception {
        final String body = validBody().replace("+35679123456", "79123456").replace("MST 1234", "12345");

        mvc.perform(post("/api/v1/orders").with(partner("orders:write")).header("Idempotency-Key", "key-00000001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("customer.mobileNumber", "customer.address.postcode")));
        verifyNoInteractions(service);
    }

    @Test
    void acceptedOrderIs201WithLocation() throws Exception {
        when(service.place(eq("erp-partner"), eq("key-00000001"), any())).thenReturn(new PlacementResult(RECEIPT, false));

        mvc.perform(post("/api/v1/orders").with(partner("orders:write")).header("Idempotency-Key", "key-00000001")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/orders/" + ORDER_ID)))
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        verify(service).place(eq("erp-partner"), eq("key-00000001"), any());
    }

    @Test
    void readingAnOrderNeedsTheReadScope() throws Exception {
        mvc.perform(get("/api/v1/orders/" + ORDER_ID).with(partner("orders:write")))
                .andExpect(status().isForbidden());

        when(service.find("erp-partner", ORDER_ID)).thenReturn(RECEIPT);
        mvc.perform(get("/api/v1/orders/" + ORDER_ID).with(partner("orders:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalReference").value("ERP-1"));
    }

    @Test
    void unmappedPathsAreDenied() throws Exception {
        mvc.perform(get("/api/v1/admin").with(partner("orders:read", "orders:write")))
                .andExpect(status().isForbidden());
    }
}
