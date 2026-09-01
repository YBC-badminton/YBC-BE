package com.ybc.ybc.vote.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum AdminVoteStatus {
    UPCOMING,
    IN_PROGRESS,
    ACTIVE,
    COMPLETED;

    private static final Pattern ACTIVITY_TIME_PATTERN = Pattern.compile("(\\d{1,2}:\\d{2})");
    private static final DateTimeFormatter ACTIVITY_TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    public static AdminVoteStatus from(LocalDateTime voteStartAt,
                                       LocalDateTime voteEndAt,
                                       LocalDate activityDate,
                                       String activityTime,
                                       LocalDateTime now) {
        if (now.isBefore(voteStartAt)) {
            return UPCOMING;
        }
        if (now.isBefore(voteEndAt)) {
            return IN_PROGRESS;
        }
        LocalDateTime activityEndAt = resolveActivityEndAt(activityDate, activityTime);
        if (activityEndAt != null && now.isBefore(activityEndAt)) {
            return ACTIVE;
        }
        return COMPLETED;
    }

    private static LocalDateTime resolveActivityEndAt(LocalDate activityDate, String activityTime) {
        if (activityDate == null || activityTime == null || activityTime.isBlank()) {
            return null;
        }

        Matcher matcher = ACTIVITY_TIME_PATTERN.matcher(activityTime);
        String lastMatchedTime = null;
        while (matcher.find()) {
            lastMatchedTime = matcher.group(1);
        }

        if (lastMatchedTime == null) {
            return null;
        }

        try {
            return activityDate.atTime(LocalTime.parse(lastMatchedTime, ACTIVITY_TIME_FORMATTER));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
