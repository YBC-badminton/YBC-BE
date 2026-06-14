package com.ybc.ybc.global.domain;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.recruitments.domain.Recruitment;
import com.ybc.ybc.recruitments.repository.RecruitmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AuditingPersistenceTest {
    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RecruitmentRepository recruitmentRepository;

    @Test
    void memberSetsAuditTimestampsWithoutDatabaseDefaults() {
        Member member = Member.createSocialMember(
                SocialProvider.KAKAO,
                "123456789",
                "양배추",
                "ybc@example.com"
        );
        member.updateProfile("양배추", "ybc@example.com");

        Member savedMember = memberRepository.saveAndFlush(member);

        assertThat(savedMember.getCreatedAt()).isNotNull();
        assertThat(savedMember.getUpdatedAt()).isNotNull();
        assertThat(savedMember.getLastLoginAt()).isNotNull();
    }

    @Test
    void recruitmentSetsAuditTimestampsWithoutDatabaseDefaults() {
        Recruitment recruitment = Recruitment.builder()
                .term("10.5기")
                .startAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                .endAt(LocalDateTime.of(2026, 5, 10, 18, 0))
                .firstResultDate(LocalDate.of(2026, 5, 12))
                .finalResultDate(LocalDate.of(2026, 5, 15))
                .interviewDate(LocalDate.of(2026, 5, 13))
                .interviewLocation("학생회관")
                .interviewFirstTime("18:00")
                .interviewSecondTime("19:00")
                .otDate(LocalDate.of(2026, 5, 20))
                .recruitmentMessage("기초반도 지원 가능합니다.")
                .membershipFee(30000)
                .activityPeriod(6)
                .build();

        Recruitment savedRecruitment = recruitmentRepository.saveAndFlush(recruitment);

        assertThat(savedRecruitment.getCreatedAt()).isNotNull();
        assertThat(savedRecruitment.getUpdatedAt()).isNotNull();

        LocalDateTime createdAt = savedRecruitment.getCreatedAt();
        LocalDateTime updatedAt = savedRecruitment.getUpdatedAt();

        savedRecruitment.update(
                "11기",
                LocalDateTime.of(2026, 5, 2, 10, 0),
                LocalDateTime.of(2026, 5, 11, 18, 0),
                LocalDate.of(2026, 5, 12),
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 5, 13),
                "학생회관",
                "18:00",
                "19:00",
                LocalDate.of(2026, 5, 20),
                "기초반도 지원 가능합니다.",
                30000,
                6
        );
        Recruitment updatedRecruitment = recruitmentRepository.saveAndFlush(savedRecruitment);

        assertThat(updatedRecruitment.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updatedRecruitment.getUpdatedAt()).isAfterOrEqualTo(updatedAt);
    }
}
