package com.ybc.ybc.applications.dto.response;

import com.ybc.ybc.recruitments.domain.Recruitment;

import java.time.LocalDateTime;

public record ApplicationAvailabilityResponse(
        boolean recruiting,
        String term,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
    public static ApplicationAvailabilityResponse from(Recruitment recruitment) {
        return new ApplicationAvailabilityResponse(
                true,
                recruitment.getTerm(),
                recruitment.getStartAt(),
                recruitment.getEndAt()
        );
    }

    public static ApplicationAvailabilityResponse closed() {
        return new ApplicationAvailabilityResponse(false, null, null, null);
    }
}
