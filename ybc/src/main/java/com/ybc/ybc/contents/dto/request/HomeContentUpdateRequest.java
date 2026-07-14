package com.ybc.ybc.contents.dto.request;

public record HomeContentUpdateRequest(
        String clubIntroduction,
        String activityImageUrl,
        Integer regularMeetingCount,
        Integer memberCount
) {
}
