package com.melita.orders.platform.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdsTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsASafeCallerSuppliedId() {
        assertThat(RequestIds.acceptOrGenerate("erp-req-42.a_b")).isEqualTo("erp-req-42.a_b");
    }

    @Test
    void replacesUnsafeOrMissingIds() {
        assertThat(RequestIds.acceptOrGenerate(null)).hasSize(36);
        assertThat(RequestIds.acceptOrGenerate("bad id\r\nX-Injected: 1")).hasSize(36);
        assertThat(RequestIds.acceptOrGenerate("x".repeat(65))).hasSize(36);
    }

    @Test
    void jobWorkRunsUnderTheGivenIdAndLeavesNothingBehind() {
        assertThat(RequestIds.callWith("req-42", RequestIds::current)).isEqualTo("req-42");
        assertThat(RequestIds.current()).isNull();
    }

    @Test
    void restoresTheSurroundingIdEvenWhenTheWorkFails() {
        MDC.put(RequestIds.MDC_KEY, "outer");

        assertThat(RequestIds.callWith(null, RequestIds::current)).isNull();
        assertThatThrownBy(() -> RequestIds.runWith("inner", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(RequestIds.current()).isEqualTo("outer");
    }
}
