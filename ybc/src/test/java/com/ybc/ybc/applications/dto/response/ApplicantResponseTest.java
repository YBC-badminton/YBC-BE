package com.ybc.ybc.applications.dto.response;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.InterviewTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicantResponseTest {

    @Test
    @DisplayName("지원서 응답 변환은 누락된 enum 값과 null 면접 시간에도 실패하지 않는다")
    void responsesHandleMissingEnumsAndNullInterviewTimes() {
        Application application = Application.builder()
                .name("김철수")
                .address("서울 강서구")
                .gender(null)
                .age(24)
                .phone("01012345678")
                .term("10기")
                .university("한국대학교")
                .major("컴퓨터공학과")
                .introduction("자기소개")
                .motivation("지원동기")
                .equipment("라켓")
                .discoverySource(null)
                .discoveryEtc(null)
                .wantsStaff(false)
                .finalComment("잘 부탁드립니다")
                .status(null)
                .email("applicant@example.com")
                .build();
        application.addInterviewTime(InterviewTime.FIRST_SESSION);
        application.addInterviewTime(null);

        ApplicantSummaryResponse summaryResponse = ApplicantSummaryResponse.from(application, false);
        ApplicantDetailResponse detailResponse = ApplicantDetailResponse.from(application, false);

        assertThat(summaryResponse.gender()).isNull();
        assertThat(summaryResponse.status()).isEqualTo("NONE");
        assertThat(detailResponse.gender()).isNull();
        assertThat(detailResponse.discoverySource()).isNull();
        assertThat(detailResponse.interviewAvailableTimes()).containsExactly("앞타임");
        assertThat(detailResponse.status()).isEqualTo("NONE");
    }
}
