package com.ybc.ybc.applications.domain;

import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;

import java.util.Arrays;
import java.util.Locale;

public enum Status {
    NONE("없음"),
    FIRST_PASS("1차합격"),
    FINAL_PASS("최종합격"),
    FAIL("불합격"),
    HOLD("보류");

    private final String displayName;

    Status(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isInterviewTarget() {
        return this == FIRST_PASS || this == FINAL_PASS || this == FAIL || this == HOLD;
    }

    public static Status from(String value) {
        try {
            return Status.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new YbcException(ExceptionCode.INVALID_APPLICATION_STATUS);
        }
    }

    public static Status fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedValue = value.trim();
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(normalizedValue)
                        || status.displayName.equals(normalizedValue))
                .findFirst()
                .orElse(null);
    }

    public static Status fromInterviewStatus(String value) {
        try {
            Status status = Status.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (!status.isInterviewTarget()) {
                throw new YbcException(ExceptionCode.INVALID_INTERVIEW_STATUS);
            }
            return status;
        } catch (YbcException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new YbcException(ExceptionCode.INVALID_INTERVIEW_STATUS);
        }
    }
}
