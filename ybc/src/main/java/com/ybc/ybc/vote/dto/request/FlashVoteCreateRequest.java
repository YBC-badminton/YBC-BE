package com.ybc.ybc.vote.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FlashVoteCreateRequest(
        @NotBlank(message = "제목을 입력해 주세요.")
        String title,
        @NotNull(message = "활동 날짜를 입력해 주세요.")
        LocalDate activityDate,
        @NotBlank(message = "활동 시간을 입력해 주세요.")
        String activityTime,
        @NotBlank(message = "장소를 입력해 주세요.")
        String location,
        String memo,
        @NotNull(message = "투표 시작 시간을 입력해 주세요.")
        LocalDateTime voteStartAt,
        @NotNull(message = "투표 종료 시간을 입력해 주세요.")
        LocalDateTime voteEndAt,
        @NotNull(message = "투표 정원을 입력해 주세요.")
        @Positive(message = "투표 정원은 1명 이상이어야 합니다.")
        Integer capacity
) {
}
