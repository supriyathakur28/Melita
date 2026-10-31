package com.melita.orders.platform.pii;

import lombok.experimental.UtilityClass;

/** Masks identifiers wherever they are shown: APIs, emails, {@code toString()}. Never log the clear value. */
@UtilityClass
public class Masking {

    private static final int VISIBLE = 3;

    /** {@code 123456M} becomes {@code ****56M}; values of up to three characters are fully masked. */
    public static String mask(final String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= VISIBLE) {
            return "****";
        }
        return "****" + value.substring(value.length() - VISIBLE);
    }
}
