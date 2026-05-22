package com.ybc.ybc.applications.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class InterviewTimeConverter implements AttributeConverter<InterviewTime, String> {
    @Override
    public String convertToDatabaseColumn(InterviewTime interviewTime) {
        return interviewTime == null ? null : interviewTime.name();
    }

    @Override
    public InterviewTime convertToEntityAttribute(String value) {
        return InterviewTime.fromDatabaseValue(value);
    }
}
