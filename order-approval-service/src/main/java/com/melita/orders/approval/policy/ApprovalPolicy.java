package com.melita.orders.approval.policy;

import com.melita.orders.events.OrderAccepted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Which products or packages need an agent's approval, from Spring Cloud Config
 * ({@code approval.required-packages}, {@code approval.required-products}).
 * <p>
 * {@code @RefreshScope}: after {@code POST /actuator/refresh} this bean is recreated and binds the new values.
 * It binds from the {@link Environment} itself because an immutable {@code @ConfigurationProperties} record is
 * not rebound on refresh.
 */
@Component
@RefreshScope
public class ApprovalPolicy {

    private static final Logger log = LoggerFactory.getLogger(ApprovalPolicy.class);

    public record Rules(Set<String> requiredPackages, Set<String> requiredProducts) {
        public Rules {
            requiredPackages = requiredPackages == null ? Set.of() : Set.copyOf(requiredPackages);
            requiredProducts = requiredProducts == null ? Set.of() : Set.copyOf(requiredProducts);
        }
    }

    private final Rules rules;

    public ApprovalPolicy(Environment environment) {
        this.rules = Binder.get(environment).bind("approval", Rules.class).orElse(new Rules(Set.of(), Set.of()));
        log.info("Approval policy: packages {}, products {}", rules.requiredPackages(), rules.requiredProducts());
    }

    public Rules rules() {
        return rules;
    }

    /** @return the reasons the order must be approved, e.g. {@code PACKAGE:MOBILE_POSTPAID}; empty if none. */
    public List<String> reasonsFor(List<OrderAccepted.Item> items) {
        List<String> reasons = new ArrayList<>();
        for (OrderAccepted.Item item : items) {
            if (rules.requiredProducts().contains(item.product())) {
                reasons.add("PRODUCT:" + item.product());
            } else if (rules.requiredPackages().contains(item.packageCode())) {
                reasons.add("PACKAGE:" + item.packageCode());
            }
        }
        return reasons;
    }
}
