package com.ybc.ybc.contents.dto.request;

import java.util.List;

public record ActivitiesContentUpdateRequest(
        String mainTitle,
        String subDescription,
        RegularMeetingRequest regularMeeting,
        List<ActivityEventRequest> events,
        AfterPartiesRequest afterParties
) {
    public record RegularMeetingRequest(
            String description,
            List<String> imageUrls
    ) {
    }

    public record ActivityEventRequest(
            String title,
            String description,
            String imageUrl
    ) {
    }

    public record AfterPartiesRequest(
            List<String> description,
            String imageUrl
    ) {
    }
}
