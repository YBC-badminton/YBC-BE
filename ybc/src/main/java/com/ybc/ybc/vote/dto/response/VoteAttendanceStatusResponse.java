package com.ybc.ybc.vote.dto.response;

public record VoteAttendanceStatusResponse(
        Boolean attendanceStatus
) {
    public static VoteAttendanceStatusResponse from(Boolean attendanceStatus) {
        return new VoteAttendanceStatusResponse(attendanceStatus);
    }
}
