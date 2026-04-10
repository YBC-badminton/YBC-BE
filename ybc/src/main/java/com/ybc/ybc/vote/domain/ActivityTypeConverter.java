package com.ybc.ybc.vote.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ActivityTypeConverter implements AttributeConverter<ActivityType, String> {
    @Override
    public String convertToDatabaseColumn(ActivityType activityType) {
        return activityType == null ? null : activityType.getApiValue();
    }

    @Override
    public ActivityType convertToEntityAttribute(String value) {
        return value == null ? null : ActivityType.fromStoredValue(value);
    }
}
