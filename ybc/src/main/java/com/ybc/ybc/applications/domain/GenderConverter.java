package com.ybc.ybc.applications.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class GenderConverter implements AttributeConverter<Gender, String> {
    @Override
    public String convertToDatabaseColumn(Gender gender) {
        return gender == null ? null : gender.name();
    }

    @Override
    public Gender convertToEntityAttribute(String value) {
        return Gender.fromDatabaseValue(value);
    }
}
