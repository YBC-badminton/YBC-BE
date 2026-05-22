package com.ybc.ybc.applications.domain;

import java.util.Arrays;
import java.util.Locale;

public enum Gender {
    MALE("남성"),
    FEMALE("여성");

    private final String displayName;

    Gender(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Gender fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedValue = value.trim();
        return Arrays.stream(values())
                .filter(gender -> gender.name().equalsIgnoreCase(normalizedValue)
                        || gender.displayName.equals(normalizedValue)
                        || gender.displayName.equalsIgnoreCase(normalizedValue.toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }
}
