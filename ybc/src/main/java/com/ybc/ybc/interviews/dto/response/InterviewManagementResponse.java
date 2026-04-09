package com.ybc.ybc.interviews.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record InterviewManagementResponse(
        InterviewSummaryResponse summary,
        @JsonProperty("interview_list")
        List<InterviewManagementItemResponse> interviewList
) {
}
