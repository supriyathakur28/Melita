package com.melita.orders.approval.api;

import com.melita.orders.approval.policy.ApprovalPolicy;
import com.melita.orders.approval.config.SecurityConfig;
import com.melita.orders.approval.order.InvalidTransitionException;
import com.melita.orders.approval.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role matrix and request validation of the Approval API, with the service mocked. */
@WebMvcTest({AgentOrderController.class, ApprovalPolicyController.class})
@Import(SecurityConfig.class)
class AgentOrderApiWebTest {

    private static final UUID ORDER_ID = UUID.fromString("3f2c8e1a-8d4b-4c71-9a55-0e6b2f1d7c90");

    @Autowired
    MockMvc mvc;
    @MockitoBean
    AgentOrderService service;
    @MockitoBean
    ApprovalPolicy policy;

    private static JwtRequestPostProcessor user(String username, String... roles) {
        return jwt().jwt(j -> j.claim("preferred_username", username))
                .authorities(java.util.Arrays.stream(roles)
                        .map(r -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                        .toList());
    }

    @Test
    void anonymousCallersAreRejected() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void partnerTokensWithoutAgentRoleAreForbidden() throws Exception {
        mvc.perform(get("/api/v1/orders").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders:write"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test
    void agentsCanListOrdersByStatus() throws Exception {
        when(service.list(eq(OrderStatus.PENDING_APPROVAL), any())).thenReturn(Page.empty());

        mvc.perform(get("/api/v1/orders").param("status", "PENDING_APPROVAL").with(user("agent1", "AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void sortingIsRestrictedToAnAllowList() throws Exception {
        mvc.perform(get("/api/v1/orders").param("sort", "customer.email").with(user("agent1", "AGENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void approvalUsesTheAgentFromTheToken() throws Exception {
        mvc.perform(post("/api/v1/orders/" + ORDER_ID + "/approve").with(user("agent1", "AGENT")))
                .andExpect(status().isOk());

        verify(service).approve(eq(ORDER_ID), eq("agent1"), isNull());
    }

    @Test
    void agentFallsBackToTheTokenSubject() throws Exception {
        mvc.perform(post("/api/v1/orders/" + ORDER_ID + "/approve")
                        .with(jwt().jwt(j -> j.subject("f3a1-agent-id")).authorities(new SimpleGrantedAuthority("ROLE_AGENT"))))
                .andExpect(status().isOk());

        verify(service).approve(eq(ORDER_ID), eq("f3a1-agent-id"), isNull());
    }

    @Test
    void rejectionNeedsAMeaningfulReason() throws Exception {
        mvc.perform(post("/api/v1/orders/" + ORDER_ID + "/reject").with(user("agent1", "AGENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"no\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        verifyNoInteractions(service);
    }

    @Test
    void decidingAnAlreadyDecidedOrderIs409() throws Exception {
        when(service.approve(any(), any(), any()))
                .thenThrow(new InvalidTransitionException(OrderStatus.REJECTED, OrderStatus.READY_FOR_FULFILMENT));

        mvc.perform(post("/api/v1/orders/" + ORDER_ID + "/approve").with(user("agent1", "AGENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void approvalPolicyIsForSupervisorsOnly() throws Exception {
        when(policy.rules()).thenReturn(new ApprovalPolicy.Rules(Set.of("MOBILE_POSTPAID"), Set.of()));

        mvc.perform(get("/api/v1/approval-policy").with(user("agent1", "AGENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/approval-policy").with(user("supervisor1", "SUPERVISOR", "AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredPackages[0]").value("MOBILE_POSTPAID"));
    }

    @Test
    void realmRolesFromKeycloakBecomeSpringRoles() {
        var token = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t").header("alg", "none")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("AGENT", "SUPERVISOR"))).build();

        org.assertj.core.api.Assertions.assertThat(SecurityConfig.realmRoles(token))
                .extracting(Object::toString).containsExactlyInAnyOrder("ROLE_AGENT", "ROLE_SUPERVISOR");
    }
}
