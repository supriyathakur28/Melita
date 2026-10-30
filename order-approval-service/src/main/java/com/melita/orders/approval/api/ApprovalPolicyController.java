package com.melita.orders.approval.api;

import com.melita.orders.approval.policy.ApprovalPolicy;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.TreeSet;

@RestController
class ApprovalPolicyController {

    private final ApprovalPolicy policy;

    ApprovalPolicyController(ApprovalPolicy policy) {
        this.policy = policy;
    }

    record PolicyResponse(Set<String> requiredPackages, Set<String> requiredProducts) {
    }

    @Operation(summary = "The approval policy currently in force (changed through Spring Cloud Config)")
    @GetMapping("/api/v1/approval-policy")
    @PreAuthorize("hasRole('SUPERVISOR')")
    PolicyResponse current() {
        return new PolicyResponse(new TreeSet<>(policy.rules().requiredPackages()),
                new TreeSet<>(policy.rules().requiredProducts()));
    }
}
