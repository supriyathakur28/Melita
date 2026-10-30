package com.melita.orders.platform.pii;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingTest {

    @ParameterizedTest
    @CsvSource({
            "123456M, ****56M",
            "AB1234567, ****567",
            "1234, ****234",
            "12M, ****",
            "M, ****"
    })
    void keepsOnlyTheLastThreeCharacters(final String value, final String masked) {
        assertThat(Masking.mask(value)).isEqualTo(masked);
    }

    @org.junit.jupiter.api.Test
    void nullStaysNull() {
        assertThat(Masking.mask(null)).isNull();
    }
}
