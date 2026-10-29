package com.melita.orders.management.pii;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiiCipherTest {

    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private final PiiCipher cipher = new PiiCipher(KEY);

    @Test
    void roundTripsAndHidesThePlaintext() {
        String stored = cipher.encrypt("123456M");

        assertThat(stored).startsWith("v1:").doesNotContain("123456M");
        assertThat(cipher.decrypt(stored)).isEqualTo("123456M");
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        assertThat(cipher.encrypt("123456M")).isNotEqualTo(cipher.encrypt("123456M"));
    }

    @Test
    void tamperedValuesAreRejected() {
        String stored = cipher.encrypt("123456M");
        byte[] raw = Base64.getDecoder().decode(stored.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aDifferentKeyCannotDecrypt() {
        String stored = cipher.encrypt("123456M");
        var other = new PiiCipher(Base64.getEncoder().encodeToString(new byte[32]));

        assertThatThrownBy(() -> other.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keyMustBe256Bits() {
        assertThatThrownBy(() -> new PiiCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
