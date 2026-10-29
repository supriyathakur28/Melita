package com.melita.orders.fulfilmentstub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FulfilmentOrderController.class)
class FulfilmentStubWebTest {

    @Autowired
    MockMvc mvc;

    private static MockHttpServletRequestBuilder submit(String key, String body) {
        return post("/api/v1/fulfilment-orders")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_fulfilment:submit")))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void requiresTheSubmitScope() throws Exception {
        mvc.perform(post("/api/v1/fulfilment-orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/fulfilment-orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sameKeyAndBodyReturnTheOriginalReference() throws Exception {
        String reference = mvc.perform(submit("order-1", "{\"orderId\":\"order-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(submit("order-1", "{\"orderId\":\"order-1\"}"))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getContentAsString()).isEqualTo(reference));
    }

    @Test
    void sameKeyWithADifferentBodyIs409() throws Exception {
        mvc.perform(submit("order-2", "{\"orderId\":\"order-2\"}")).andExpect(status().isCreated());
        mvc.perform(submit("order-2", "{\"orderId\":\"other\"}")).andExpect(status().isConflict());
    }

    @Test
    void canBeForcedToFail() throws Exception {
        mvc.perform(submit("order-3", "{}").header("X-Stub-Force-Status", "503"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(submit("order-3", "{}").header("X-Stub-Force-Status", "422"))
                .andExpect(status().isUnprocessableContent());
    }
}
