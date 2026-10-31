package com.melita.orders.platform.pii;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingTest {

    @Test
    void keepsOnlyTheLastThreeCharacters() {
        assertThat(Masking.mask("123456M")).isEqualTo("****56M");
        assertThat(Masking.mask("AB1234567")).isEqualTo("****567");
        assertThat(Masking.mask("1234")).isEqualTo("****234");
        assertThat(Masking.mask("12M")).isEqualTo("****");
        assertThat(Masking.mask(null)).isNull();
    }
}
