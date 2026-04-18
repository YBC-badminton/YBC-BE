package com.ybc.ybc.vote.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;

import java.util.Locale;

public enum ActivityType {
    REGULAR("REGULAR"),
    FLASH("FLASH"),
    EVENT("EVENT");

    private final String apiValue;

    ActivityType(String apiValue) {
        this.apiValue = apiValue;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    public static ActivityType from(String value) {
        try {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "REGULAR" -> REGULAR;
                case "FLASH" -> FLASH;
                case "EVENT" -> EVENT;
                default -> throw new IllegalArgumentException("Unsupported activity type");
            };
        } catch (RuntimeException exception) {
            throw new YbcException(ExceptionCode.INVALID_VOTE_ACTIVITY_TYPE);
        }
    }
}
