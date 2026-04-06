package com.ybc.ybc.applications.domain;

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
}
