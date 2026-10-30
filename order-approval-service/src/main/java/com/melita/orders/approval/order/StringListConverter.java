package com.melita.orders.approval.order;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

/** Stores short code lists (approval reasons) as a comma-separated column. */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(final List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values);
    }

    @Override
    public List<String> convertToEntityAttribute(final String column) {
        return column == null || column.isBlank() ? List.of() : Arrays.asList(column.split(","));
    }
}
