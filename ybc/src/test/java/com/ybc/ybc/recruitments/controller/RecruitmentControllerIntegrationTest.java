package com.ybc.ybc.recruitments.controller;

import com.ybc.ybc.recruitments.domain.Recruitment;
import com.ybc.ybc.recruitments.repository.RecruitmentRepository;
import com.ybc.ybc.support.TestMailConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestMailConfig.class)
class RecruitmentControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecruitmentRepository recruitmentRepository;

    @BeforeEach
    void setUp() {
        recruitmentRepository.deleteAll();
    }

    @Test
    @DisplayName("비로그인 사용자도 현재 모집 중인 일정의 문구를 조회할 수 있다")
    void getRecruitmentMessageReturnsCurrentRecruitmentMessageWithoutAuthentication() throws Exception {
        recruitmentRepository.saveAndFlush(Recruitment.builder()
                .term("12기")
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .firstResultDate(LocalDate.now().plusDays(3))
                .finalResultDate(LocalDate.now().plusDays(7))
                .interviewDate(LocalDate.now().plusDays(5))
                .interviewLocation("ECC")
                .interviewFirstTime("10:00")
                .interviewSecondTime("14:00")
                .otDate(LocalDate.now().plusDays(10))
                .recruitmentMessage("배드민턴 경험이 없어도 지원 가능합니다.")
                .membershipFee(30000)
                .activityPeriod(6)
                .build());

        mockMvc.perform(get("/recruitments/message"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruiting").value(true))
                .andExpect(jsonPath("$.term").value("12기"))
                .andExpect(jsonPath("$.recruitmentMessage").value("배드민턴 경험이 없어도 지원 가능합니다."));
    }

    @Test
    @DisplayName("현재 모집 중인 일정이 없으면 닫힌 상태를 반환한다")
    void getRecruitmentMessageReturnsClosedWhenNoCurrentRecruitmentExists() throws Exception {
        recruitmentRepository.saveAndFlush(Recruitment.builder()
                .term("11기")
                .startAt(LocalDateTime.now().minusDays(10))
                .endAt(LocalDateTime.now().minusDays(5))
                .firstResultDate(LocalDate.now().minusDays(3))
                .finalResultDate(LocalDate.now().minusDays(1))
                .interviewDate(LocalDate.now())
                .interviewLocation("ECC")
                .interviewFirstTime("10:00")
                .interviewSecondTime("14:00")
                .otDate(LocalDate.now().plusDays(3))
                .recruitmentMessage("지난 모집 안내 문구")
                .membershipFee(30000)
                .activityPeriod(6)
                .build());

        mockMvc.perform(get("/recruitments/message"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruiting").value(false))
                .andExpect(jsonPath("$.term").doesNotExist())
                .andExpect(jsonPath("$.recruitmentMessage").doesNotExist());
    }

    @Test
    @DisplayName("비로그인 사용자도 현재 모집 중인 기수의 면접 시간을 조회할 수 있다")
    void getCurrentRecruitmentInterviewTimesReturnsCurrentInterviewTimesWithoutAuthentication() throws Exception {
        recruitmentRepository.saveAndFlush(Recruitment.builder()
                .term("12기")
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .firstResultDate(LocalDate.now().plusDays(3))
                .finalResultDate(LocalDate.now().plusDays(7))
                .interviewDate(LocalDate.now().plusDays(5))
                .interviewLocation("ECC")
                .interviewFirstTime("18:00 ~ 19:00")
                .interviewSecondTime("19:00 ~ 20:00")
                .otDate(LocalDate.now().plusDays(10))
                .recruitmentMessage("배드민턴 경험이 없어도 지원 가능합니다.")
                .membershipFee(30000)
                .activityPeriod(6)
                .build());

        mockMvc.perform(get("/recruitments/interview-times"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruiting").value(true))
                .andExpect(jsonPath("$.term").value("12기"))
                .andExpect(jsonPath("$.interviewDate").value(LocalDate.now().plusDays(5).toString()))
                .andExpect(jsonPath("$.interviewFirstTime").value("18:00 ~ 19:00"))
                .andExpect(jsonPath("$.interviewSecondTime").value("19:00 ~ 20:00"));
    }

    @Test
    @DisplayName("현재 모집 중인 기수가 없으면 면접 시간 조회는 닫힌 상태를 반환한다")
    void getCurrentRecruitmentInterviewTimesReturnsClosedWhenNoCurrentRecruitmentExists() throws Exception {
        recruitmentRepository.saveAndFlush(Recruitment.builder()
                .term("11기")
                .startAt(LocalDateTime.now().minusDays(10))
                .endAt(LocalDateTime.now().minusDays(5))
                .firstResultDate(LocalDate.now().minusDays(3))
                .finalResultDate(LocalDate.now().minusDays(1))
                .interviewDate(LocalDate.now())
                .interviewLocation("ECC")
                .interviewFirstTime("10:00")
                .interviewSecondTime("14:00")
                .otDate(LocalDate.now().plusDays(3))
                .recruitmentMessage("지난 모집 안내 문구")
                .membershipFee(30000)
                .activityPeriod(6)
                .build());

        mockMvc.perform(get("/recruitments/interview-times"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruiting").value(false))
                .andExpect(jsonPath("$.term").doesNotExist())
                .andExpect(jsonPath("$.interviewDate").doesNotExist())
                .andExpect(jsonPath("$.interviewFirstTime").doesNotExist())
                .andExpect(jsonPath("$.interviewSecondTime").doesNotExist());
    }
}
