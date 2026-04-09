package com.ybc.ybc.applications.domain;

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
}
