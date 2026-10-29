package com.melita.orders.platform.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdsTest {

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
}
