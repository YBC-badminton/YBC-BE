package com.ybc.ybc.applications.domain;

import java.util.Arrays;
import java.util.Locale;

public enum InterviewTime {
    FIRST_SESSION("앞타임"),
    SECOND_SESSION("뒷타임");

    private final String displayName;

    InterviewTime(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static InterviewTime fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedValue = value.trim();
        return Arrays.stream(values())
                .filter(interviewTime -> interviewTime.name().equalsIgnoreCase(normalizedValue)
                        || interviewTime.displayName.equals(normalizedValue)
                        || interviewTime.displayName.equalsIgnoreCase(normalizedValue.toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }
}
