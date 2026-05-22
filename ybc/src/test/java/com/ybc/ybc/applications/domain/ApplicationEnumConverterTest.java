package com.ybc.ybc.applications.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationEnumConverterTest {

    @Test
    @DisplayName("지원서 enum converter는 영문 enum 이름과 한글 표시값을 모두 읽는다")
    void convertersAcceptLegacyDisplayNames() {
        GenderConverter genderConverter = new GenderConverter();
        DiscoverySourceConverter discoverySourceConverter = new DiscoverySourceConverter();
        StatusConverter statusConverter = new StatusConverter();
        InterviewTimeConverter interviewTimeConverter = new InterviewTimeConverter();

        assertThat(genderConverter.convertToEntityAttribute("MALE")).isEqualTo(Gender.MALE);
        assertThat(genderConverter.convertToEntityAttribute("여성")).isEqualTo(Gender.FEMALE);
        assertThat(discoverySourceConverter.convertToEntityAttribute("INSTAGRAM")).isEqualTo(DiscoverySource.INSTAGRAM);
        assertThat(discoverySourceConverter.convertToEntityAttribute("에브리타임")).isEqualTo(DiscoverySource.EVERYTIME);
        assertThat(statusConverter.convertToEntityAttribute("FINAL_PASS")).isEqualTo(Status.FINAL_PASS);
        assertThat(statusConverter.convertToEntityAttribute("불합격")).isEqualTo(Status.FAIL);
        assertThat(interviewTimeConverter.convertToEntityAttribute("FIRST_SESSION")).isEqualTo(InterviewTime.FIRST_SESSION);
        assertThat(interviewTimeConverter.convertToEntityAttribute("뒷타임")).isEqualTo(InterviewTime.SECOND_SESSION);
    }

    @Test
    @DisplayName("지원서 enum converter는 알 수 없는 값이 와도 null로 처리한다")
    void convertersReturnNullForUnknownValues() {
        GenderConverter genderConverter = new GenderConverter();
        DiscoverySourceConverter discoverySourceConverter = new DiscoverySourceConverter();
        StatusConverter statusConverter = new StatusConverter();
        InterviewTimeConverter interviewTimeConverter = new InterviewTimeConverter();

        assertThat(genderConverter.convertToEntityAttribute("UNKNOWN")).isNull();
        assertThat(discoverySourceConverter.convertToEntityAttribute("UNKNOWN")).isNull();
        assertThat(statusConverter.convertToEntityAttribute("UNKNOWN")).isNull();
        assertThat(interviewTimeConverter.convertToEntityAttribute("UNKNOWN")).isNull();
    }
}
