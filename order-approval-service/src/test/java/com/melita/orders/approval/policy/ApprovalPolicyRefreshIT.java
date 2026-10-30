package com.melita.orders.approval.policy;

import com.melita.orders.approval.ApprovalIntegrationTest;
import com.melita.orders.approval.TestOrderEvents;
import com.melita.orders.approval.order.CustomerOrderRepository;
import com.melita.orders.approval.order.OrderStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** A policy change from Spring Cloud Config takes effect after a refresh, without a restart. */
class ApprovalPolicyRefreshIT extends ApprovalIntegrationTest {

    private static final String SOURCE = "it-policy-change";

    @Autowired
    ApprovalPolicy policy;
    @Autowired
    ContextRefresher refresher;
    @Autowired
    ConfigurableEnvironment environment;
    @Autowired
    CustomerOrderRepository orders;

    @AfterEach
    void restore() {
        environment.getPropertySources().remove(SOURCE);
        refresher.refresh();
    }

    @Test
    void refreshedPolicyAppliesToTheNextOrder() {
        assertThat(policy.rules().requiredPackages()).doesNotContain("MOBILE_PREPAID");

        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE,
                Map.of("approval.required-packages[0]", "MOBILE_PREPAID")));
        refresher.refresh();

        assertThat(policy.rules().requiredPackages()).containsExactly("MOBILE_PREPAID");
        UUID orderId = UUID.randomUUID();
        publishAccepted(TestOrderEvents.accepted(orderId, List.of(TestOrderEvents.item("MOBILE", "MOBILE_PREPAID")), null));
        await().atMost(Duration.ofSeconds(10)).until(() -> orders.findById(orderId)
                .map(o -> o.getStatus() == OrderStatus.PENDING_APPROVAL).orElse(false));
    }
}
