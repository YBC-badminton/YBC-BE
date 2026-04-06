package com.ybc.ybc.interviews.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InterviewSummaryResponse(
        @JsonProperty("total_interviewees")
        long totalInterviewees,
        @JsonProperty("passed_count")
        long passedCount,
        @JsonProperty("failed_count")
        long failedCount,
        @JsonProperty("hold_count")
        long holdCount
) {
}
