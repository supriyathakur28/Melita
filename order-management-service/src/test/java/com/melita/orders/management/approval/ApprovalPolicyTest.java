package com.melita.orders.management.approval;

import com.melita.orders.management.TestOrderEvents;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalPolicyTest {

    @Test
    void matchesConfiguredPackagesAndProducts() {
        var environment = new MockEnvironment()
                .withProperty("approval.required-packages[0]", "MOBILE_POSTPAID")
                .withProperty("approval.required-products[0]", "TV");
        var policy = new ApprovalPolicy(environment);

        assertThat(policy.reasonsFor(List.of(
                TestOrderEvents.item("MOBILE", "MOBILE_POSTPAID"),
                TestOrderEvents.item("TV", "TV_90_CHANNELS"),
                TestOrderEvents.item("INTERNET", "INTERNET_250MBPS"))))
                .containsExactly("PACKAGE:MOBILE_POSTPAID", "PRODUCT:TV");
    }

    @Test
    void noRulesMeansNoApproval() {
        var policy = new ApprovalPolicy(new MockEnvironment());

        assertThat(policy.reasonsFor(List.of(TestOrderEvents.item("MOBILE", "MOBILE_POSTPAID")))).isEmpty();
    }
}
