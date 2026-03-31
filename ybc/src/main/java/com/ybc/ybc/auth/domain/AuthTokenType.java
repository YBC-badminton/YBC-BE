package com.ybc.ybc.auth.domain;

public enum AuthTokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String value;

    AuthTokenType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
