package com.ybc.ybc.interviews.dto.response;

import java.util.List;

public record InterviewManagementResponse(
        InterviewSummaryResponse summary,
        List<InterviewManagementItemResponse> interviewList
) {
}
