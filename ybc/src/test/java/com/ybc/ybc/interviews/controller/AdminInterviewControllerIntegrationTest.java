package com.ybc.ybc.interviews.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.DiscoverySource;
import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.applications.domain.InterviewTime;
import com.ybc.ybc.applications.domain.Status;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import com.ybc.ybc.interviews.domain.Interview;
import com.ybc.ybc.interviews.repository.InterviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminInterviewControllerIntegrationTest {
    private static final String ADMIN_INTERVIEWS_API = "/admin/interviews";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private InterviewRepository interviewRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @BeforeEach
    void setUp() {
        interviewRepository.deleteAll();
        applicationRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사용자는 면접 관리 리스트를 조회할 수 있다")
    void getInterviewManagementReturnsInterviewDashboardWhenAuthenticated() throws Exception {
        Application firstPassApplication = saveApplication("김철수", Status.FIRST_PASS, List.of(InterviewTime.FIRST_SESSION, InterviewTime.SECOND_SESSION));
        Application finalPassApplication = saveApplication("이영희", Status.FINAL_PASS, List.of(InterviewTime.SECOND_SESSION));
        Application failApplication = saveApplication("박민수", Status.FAIL, List.of(InterviewTime.FIRST_SESSION));
        Application holdApplication = saveApplication("최수진", Status.HOLD, List.of(InterviewTime.SECOND_SESSION));
        saveInterview(finalPassApplication, InterviewTime.SECOND_SESSION, "레슨 경험 있음");
        saveApplication("지원전", Status.NONE, List.of(InterviewTime.FIRST_SESSION));

        mockMvc.perform(get(ADMIN_INTERVIEWS_API)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total_interviewees").value(4))
                .andExpect(jsonPath("$.summary.passed_count").value(1))
                .andExpect(jsonPath("$.summary.failed_count").value(1))
                .andExpect(jsonPath("$.summary.hold_count").value(1))
                .andExpect(jsonPath("$.interview_list[0].application_id").value(firstPassApplication.getId()))
                .andExpect(jsonPath("$.interview_list[0].available_sessions[0]").value("FIRST_SESSION"))
                .andExpect(jsonPath("$.interview_list[0].available_sessions[1]").value("SECOND_SESSION"))
                .andExpect(jsonPath("$.interview_list[0].scheduled_at").doesNotExist())
                .andExpect(jsonPath("$.interview_list[1].application_id").value(finalPassApplication.getId()))
                .andExpect(jsonPath("$.interview_list[1].scheduled_at").value("SECOND_SESSION"))
                .andExpect(jsonPath("$.interview_list[1].memo").value("레슨 경험 있음"))
                .andExpect(jsonPath("$.interview_list[2].application_id").value(failApplication.getId()))
                .andExpect(jsonPath("$.interview_list[3].application_id").value(holdApplication.getId()));
    }

    @Test
    @DisplayName("인증된 사용자는 면접 관리 정보를 수정할 수 있다")
    void updateInterviewReturnsUpdatedDashboardWhenAuthenticated() throws Exception {
        Application firstPassApplication = saveApplication("김철수", Status.FIRST_PASS, List.of(InterviewTime.FIRST_SESSION, InterviewTime.SECOND_SESSION));
        Application holdApplication = saveApplication("이영희", Status.HOLD, List.of(InterviewTime.SECOND_SESSION));

        mockMvc.perform(patch(ADMIN_INTERVIEWS_API + "/{applicationId}", firstPassApplication.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledAt", "FIRST_SESSION",
                                "status", "FINAL_PASS",
                                "memo", "양배추 지인"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total_interviewees").value(2))
                .andExpect(jsonPath("$.summary.passed_count").value(1))
                .andExpect(jsonPath("$.summary.failed_count").value(0))
                .andExpect(jsonPath("$.summary.hold_count").value(1))
                .andExpect(jsonPath("$.interview_list[0].application_id").value(firstPassApplication.getId()))
                .andExpect(jsonPath("$.interview_list[0].scheduled_at").value("FIRST_SESSION"))
                .andExpect(jsonPath("$.interview_list[0].status").value("FINAL_PASS"))
                .andExpect(jsonPath("$.interview_list[0].memo").value("양배추 지인"));

        Application updatedApplication = applicationRepository.findById(firstPassApplication.getId()).orElseThrow();
        Interview interview = interviewRepository.findByApplication_Id(firstPassApplication.getId()).orElseThrow();
        assertThat(updatedApplication.getStatus()).isEqualTo(Status.FINAL_PASS);
        assertThat(interview.getScheduledAt()).isEqualTo(InterviewTime.FIRST_SESSION);
        assertThat(interview.getMemo()).isEqualTo("양배추 지인");
    }

    @Test
    @DisplayName("면접 대상이 아닌 지원자를 수정하면 400을 반환한다")
    void updateInterviewReturnsBadRequestWhenApplicationIsNotInterviewTarget() throws Exception {
        Application application = saveApplication("김철수", Status.NONE, List.of(InterviewTime.FIRST_SESSION));

        mockMvc.perform(patch(ADMIN_INTERVIEWS_API + "/{applicationId}", application.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledAt", "FIRST_SESSION",
                                "status", "FINAL_PASS",
                                "memo", "양배추 지인"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("APPLICATION_NOT_INTERVIEW_TARGET"));
    }

    @Test
    @DisplayName("면접 상태값이 올바르지 않으면 400을 반환한다")
    void updateInterviewReturnsBadRequestWhenStatusIsInvalid() throws Exception {
        Application application = saveApplication("김철수", Status.FIRST_PASS, List.of(InterviewTime.FIRST_SESSION));

        mockMvc.perform(patch(ADMIN_INTERVIEWS_API + "/{applicationId}", application.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledAt", "FIRST_SESSION",
                                "status", "NONE",
                                "memo", "양배추 지인"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INTERVIEW_STATUS"));
    }

    @Test
    @DisplayName("인증 정보가 없으면 면접 관리 리스트 조회는 401을 반환한다")
    void getInterviewManagementReturnsUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(get(ADMIN_INTERVIEWS_API))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    private Application saveApplication(String name, Status status, List<InterviewTime> interviewTimes) {
        Application application = Application.builder()
                .name(name)
                .address("서울 강서구")
                .gender(Gender.MALE)
                .age(24)
                .phone("010-1234-5678")
                .university("한국대학교")
                .major("컴퓨터공학과")
                .introduction("성실하게 활동할 자신 있습니다...")
                .motivation("운동을 좋아하고 사람들과 어울리는 것을 좋아합니다.")
                .equipment("개인 라켓 보유")
                .discoverySource(DiscoverySource.INSTAGRAM)
                .discoveryEtc(null)
                .wantsStaff(false)
                .finalComment("열심히 하겠습니다!")
                .status(status)
                .email(name + "@example.com")
                .build();
        interviewTimes.forEach(application::addInterviewTime);
        Application savedApplication = applicationRepository.saveAndFlush(application);
        return applicationRepository.findById(savedApplication.getId()).orElseThrow();
    }

    private Interview saveInterview(Application application, InterviewTime scheduledAt, String memo) {
        Interview interview = Interview.create(application, scheduledAt, memo);
        Interview savedInterview = interviewRepository.saveAndFlush(interview);
        return interviewRepository.findById(savedInterview.getId()).orElseThrow();
    }

    private JwtRequestPostProcessor authorizedUser() {
        return jwt().jwt(jwt -> jwt
                .subject("1")
                .claim("tokenType", "access")
                .claim("provider", "KAKAO")
                .claim("socialId", "123456789")
                .claim("email", "admin@example.com")
                .claim("nickname", "관리자"));
    }
}
