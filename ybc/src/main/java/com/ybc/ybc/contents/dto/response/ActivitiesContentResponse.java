package com.ybc.ybc.contents.dto.response;

import com.ybc.ybc.contents.domain.ActivitiesContent;
import com.ybc.ybc.contents.domain.ActivitiesEvent;

import java.util.List;

public record ActivitiesContentResponse(
        String mainTitle,
        String subDescription,
        RegularMeetingResponse regularMeeting,
        List<ActivityEventResponse> events,
        AfterPartiesResponse afterParties
) {
    public static ActivitiesContentResponse from(ActivitiesContent activitiesContent) {
        return new ActivitiesContentResponse(
                activitiesContent.getMainTitle(),
                activitiesContent.getSubDescription(),
                new RegularMeetingResponse(
                        activitiesContent.getRegularMeetingDescription(),
                        List.copyOf(activitiesContent.getRegularMeetingImageUrls())
                ),
                activitiesContent.getEvents().stream()
                        .map(ActivityEventResponse::from)
                        .toList(),
                new AfterPartiesResponse(
                        List.copyOf(activitiesContent.getAfterPartyDescriptions()),
                        activitiesContent.getAfterPartyImageUrl()
                )
        );
    }

    public record RegularMeetingResponse(
            String description,
            List<String> imageUrls
    ) {
    }

    public record ActivityEventResponse(
            String title,
            String description,
            String imageUrl
    ) {
        public static ActivityEventResponse from(ActivitiesEvent activitiesEvent) {
            return new ActivityEventResponse(
                    activitiesEvent.getTitle(),
                    activitiesEvent.getDescription(),
                    activitiesEvent.getImageUrl()
            );
        }
    }

    public record AfterPartiesResponse(
            List<String> description,
            String imageUrl
    ) {
    }
}
