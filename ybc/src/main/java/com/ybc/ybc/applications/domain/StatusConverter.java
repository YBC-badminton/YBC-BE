package com.ybc.ybc.applications.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class StatusConverter implements AttributeConverter<Status, String> {
    @Override
    public String convertToDatabaseColumn(Status status) {
        return status == null ? null : status.name();
    }

    @Override
    public Status convertToEntityAttribute(String value) {
        return Status.fromDatabaseValue(value);
    }
}
