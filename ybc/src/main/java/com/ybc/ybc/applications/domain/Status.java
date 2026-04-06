package com.ybc.ybc.applications.domain;

import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;

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

    public static Status from(String value) {
        try {
            return Status.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new YbcException(ExceptionCode.INVALID_APPLICATION_STATUS);
        }
    }
}
