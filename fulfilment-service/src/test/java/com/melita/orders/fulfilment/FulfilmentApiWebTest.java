package com.melita.orders.fulfilment;

import com.melita.orders.fulfilment.api.FulfilmentOrderResponse;
import com.melita.orders.fulfilment.config.SecurityConfig;
import com.melita.orders.fulfilment.order.FulfilmentOrderService;
import com.melita.orders.fulfilment.order.SubmissionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract and security matrix, with the service mocked. */
@WebMvcTest(controllers = com.melita.orders.fulfilment.api.FulfilmentOrderController.class)
@Import(SecurityConfig.class)
class FulfilmentApiWebTest {

    private static final UUID ORDER_ID = UUID.fromString("3f2c8e1a-8d4b-4c71-9a55-0e6b2f1d7c90");
    private static final FulfilmentOrderResponse RESPONSE =
            new FulfilmentOrderResponse("FUL-0123456789", ORDER_ID, "SCHEDULED", Instant.parse("2026-11-04T09:00:00Z"));

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper jsonMapper;
    @MockitoBean
    FulfilmentOrderService service;

    private MockHttpServletRequestBuilder submit(String body) {
        return post("/api/v1/fulfilment-orders").header("Idempotency-Key", ORDER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String body() {
        return jsonMapper.writeValueAsString(TestRequests.request(ORDER_ID));
    }

    @Test
    void submittingNeedsATokenWithTheSubmitScope() throws Exception {
        mvc.perform(submit(body())).andExpect(status().isUnauthorized());
        mvc.perform(submit(body()).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_fulfilment:read"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void newOrderIs201WithLocation() throws Exception {
        when(service.submit(eq(ORDER_ID.toString()), any())).thenReturn(new SubmissionResult(RESPONSE, false));

        mvc.perform(submit(body()).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_fulfilment:submit"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/fulfilment-orders/FUL-0123456789")))
                .andExpect(jsonPath("$.fulfilmentReference").value("FUL-0123456789"));
    }

    @Test
    void replayIs200() throws Exception {
        when(service.submit(any(), any())).thenReturn(new SubmissionResult(RESPONSE, true));

        mvc.perform(submit(body()).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_fulfilment:submit"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfilmentReference").value("FUL-0123456789"));
    }

    @Test
    void idempotencyKeyIsRequired() throws Exception {
        mvc.perform(post("/api/v1/fulfilment-orders").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_fulfilment:submit")))
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
        verifyNoInteractions(service);
    }

    @Test
    void incompleteOrderIs400() throws Exception {
        String withoutItems = body().replaceAll("\"items\":\\[[^]]*]", "\"items\":[]");

        mvc.perform(submit(withoutItems).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_fulfilment:submit"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
        verifyNoInteractions(service);
    }

    @Test
    void readingNeedsTheReadScope() throws Exception {
        mvc.perform(get("/api/v1/fulfilment-orders/FUL-0123456789")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_fulfilment:submit"))))
                .andExpect(status().isForbidden());
    }
}
