package com.melita.orders.platform.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
class ErrorHandlingWebTest {

    @Autowired
    MockMvc mvc;

    @Test
    void missingTokenIs401ProblemWithBearerChallengeAndRequestId() throws Exception {
        mvc.perform(get("/test/ok").header(RequestIds.HEADER, "req-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(header().string(RequestIds.HEADER, "req-123"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").value("req-123"));
    }

    @Test
    void missingScopeIs403Problem() throws Exception {
        mvc.perform(get("/test/scoped").with(jwt().authorities()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void preAuthorizeDenialIs403ProblemNot500() throws Exception {
        mvc.perform(get("/test/admin").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void requestIdIsGeneratedWhenAbsent() throws Exception {
        mvc.perform(get("/test/ok").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIds.HEADER));
    }

    @Test
    void notFoundIs404Problem() throws Exception {
        mvc.perform(get("/test/not-found").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.type").value("https://api.melita.example/problems/not-found"));
    }

    @Test
    void conflictCarriesExtraProperties() throws Exception {
        mvc.perform(get("/test/conflict").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EXTERNAL_REFERENCE"))
                .andExpect(jsonPath("$.existingOrderId").value("abc"));
    }

    @Test
    void businessRulesAre422WithEveryViolation() throws Exception {
        mvc.perform(get("/test/rules").with(jwt()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_PACKAGE"));
    }

    @Test
    void beanValidationIs400WithFieldErrors() throws Exception {
        mvc.perform(post("/test/validate").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[1].field").value("name"));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/test/validate").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unsupportedMediaTypeKeepsItsStatusWithOurShape() throws Exception {
        mvc.perform(post("/test/validate").with(jwt())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unexpectedErrorIs500WithoutInternals() throws Exception {
        mvc.perform(get("/test/boom").with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret internal detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
