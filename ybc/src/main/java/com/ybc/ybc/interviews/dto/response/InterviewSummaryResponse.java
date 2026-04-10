package com.ybc.ybc.interviews.dto.response;

public record InterviewSummaryResponse(
        long totalInterviewees,
        long passedCount,
        long failedCount,
        long holdCount
) {
}
