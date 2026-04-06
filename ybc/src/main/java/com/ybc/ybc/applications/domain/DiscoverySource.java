package com.ybc.ybc.applications.domain;

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
}
