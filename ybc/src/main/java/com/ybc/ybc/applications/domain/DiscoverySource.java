package com.ybc.ybc.applications.domain;

import java.util.Arrays;
import java.util.Locale;

public enum DiscoverySource {
    INSTAGRAM("인스타그램"),
    EVERYTIME("에브리타임"),
    CAMPUSPICK("캠퍼스픽"),
    ETC("기타");

    private final String displayName;

    DiscoverySource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static DiscoverySource fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedValue = value.trim();
        return Arrays.stream(values())
                .filter(source -> source.name().equalsIgnoreCase(normalizedValue)
                        || source.displayName.equals(normalizedValue)
                        || source.displayName.equalsIgnoreCase(normalizedValue.toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }
}
