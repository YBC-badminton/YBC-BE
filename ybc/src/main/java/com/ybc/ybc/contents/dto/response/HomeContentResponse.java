package com.ybc.ybc.contents.dto.response;

import com.ybc.ybc.contents.domain.HomeContent;

public record HomeContentResponse(
        String clubIntroduction,
        String activityImageUrl,
        Integer regularMeetingCount,
        Integer memberCount
) {
    public static HomeContentResponse from(HomeContent homeContent) {
        return new HomeContentResponse(
                homeContent.getClubIntroduction(),
                homeContent.getActivityImageUrl(),
                homeContent.getRegularMeetingCount(),
                homeContent.getMemberCount()
        );
    }
}
