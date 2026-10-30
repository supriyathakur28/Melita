package com.melita.orders.platform.pii;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Encrypts a column transparently (use with {@code @Convert}). The service declares a {@link PiiCipher} bean
 * with its own key. Hibernate obtains converters from Spring (Spring Boot registers Spring as
 * Hibernate's bean container), so the cipher is injected.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final PiiCipher cipher;

    public EncryptedStringConverter(PiiCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String column) {
        return cipher.decrypt(column);
    }
}
