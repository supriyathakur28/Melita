package com.melita.orders.platform.idempotency;

import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Fingerprint of a request for idempotency: SHA-256 of the parsed request re-serialised canonically (keys
 * sorted, no whitespace). Formatting or key order in the original body does not change the hash.
 * <p>
 * Used by every service that honours an {@code Idempotency-Key}: the same key with a different hash is a
 * different request.
 */
public class RequestHasher {

    private final JsonMapper canonical;

    public RequestHasher(final JsonMapper jsonMapper) {
        this.canonical = jsonMapper.rebuild()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    public String hash(final Object request) {
        final byte[] json = canonical.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
