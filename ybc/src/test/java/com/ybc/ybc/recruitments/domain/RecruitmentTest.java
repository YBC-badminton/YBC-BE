package com.ybc.ybc.recruitments.domain;

import com.ybc.ybc.global.exception.YbcException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecruitmentTest {

    @Test
    @DisplayName("모집 시작일은 모집 마감일보다 앞서야 한다")
    void createFailsWhenStartAtIsNotBeforeEndAt() {
        assertThatThrownBy(() -> recruitmentBuilder()
                .startAt(LocalDateTime.of(2026, 4, 7, 18, 0))
                .endAt(LocalDateTime.of(2026, 4, 7, 18, 0))
                .build())
                .isInstanceOfSatisfying(YbcException.class, exception ->
                        assertThat(exception.getExceptionCodeName()).isEqualTo("INVALID_RECRUITMENT_SCHEDULE"));
    }

    @Test
    @DisplayName("1차 발표일은 최종 발표일보다 늦을 수 없다")
    void createFailsWhenFirstResultDateIsAfterFinalResultDate() {
        assertThatThrownBy(() -> recruitmentBuilder()
                .firstResultDate(LocalDate.of(2026, 4, 18))
                .finalResultDate(LocalDate.of(2026, 4, 17))
                .interviewDate(LocalDate.of(2026, 4, 19))
                .build())
                .isInstanceOfSatisfying(YbcException.class, exception ->
                        assertThat(exception.getExceptionCodeName()).isEqualTo("INVALID_RECRUITMENT_SCHEDULE"));
    }

    @Test
    @DisplayName("면접일은 모집 마감일보다 앞설 수 없다")
    void createFailsWhenInterviewDateIsBeforeRecruitmentEndDate() {
        assertThatThrownBy(() -> recruitmentBuilder()
                .interviewDate(LocalDate.of(2026, 4, 6))
                .build())
                .isInstanceOfSatisfying(YbcException.class, exception ->
                        assertThat(exception.getExceptionCodeName()).isEqualTo("INVALID_RECRUITMENT_SCHEDULE"));
    }

    @Test
    @DisplayName("전체 정보 수정 시에도 기존 일정과의 순서를 검증한다")
    void updateFailsWhenEndAtMovesAfterInterviewDate() {
        Recruitment recruitment = recruitmentBuilder().build();

        assertThatThrownBy(() -> recruitment.update(
                "10기",
                LocalDateTime.of(2026, 4, 10, 9, 0),
                LocalDateTime.of(2026, 4, 13, 18, 0),
                LocalDate.of(2026, 4, 10),
                LocalDate.of(2026, 4, 17),
                LocalDate.of(2026, 4, 12),
                "ECC",
                "10:00",
                "14:00",
                LocalDate.of(2026, 4, 20),
                "기초반도 지원 가능합니다.",
                30000,
                6
        ))
                .isInstanceOfSatisfying(YbcException.class, exception ->
                        assertThat(exception.getExceptionCodeName()).isEqualTo("INVALID_RECRUITMENT_SCHEDULE"));
    }

    private Recruitment.RecruitmentBuilder recruitmentBuilder() {
        return Recruitment.builder()
                .term("10기")
                .startAt(LocalDateTime.of(2026, 4, 1, 9, 0))
                .endAt(LocalDateTime.of(2026, 4, 7, 18, 0))
                .firstResultDate(LocalDate.of(2026, 4, 10))
                .finalResultDate(LocalDate.of(2026, 4, 17))
                .interviewDate(LocalDate.of(2026, 4, 12))
                .interviewLocation("ECC")
                .interviewFirstTime("10:00")
                .interviewSecondTime("14:00")
                .otDate(LocalDate.of(2026, 4, 20))
                .recruitmentMessage("기초반도 지원 가능합니다.")
                .membershipFee(30000)
                .activityPeriod(6);
    }
}
