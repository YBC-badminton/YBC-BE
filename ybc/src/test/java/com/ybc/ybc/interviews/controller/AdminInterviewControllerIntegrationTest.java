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
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import com.ybc.ybc.support.TestMailConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
@Import(TestMailConfig.class)
class AdminInterviewControllerIntegrationTest {
    private static final String ADMIN_INTERVIEWS_API = "/admin/interviews";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private ClubMemberRepository clubMemberRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @BeforeEach
    void setUp() {
        clubMemberRepository.deleteAll();
        interviewRepository.deleteAll();
        applicationRepository.deleteAll();
    }

    @Test
    @DisplayName("면접 대상 지원자가 없어도 빈 목록을 반환한다")
    void getInterviewManagementReturnsEmptyDashboardWhenNoInterviewTargets() throws Exception {
        mockMvc.perform(get(ADMIN_INTERVIEWS_API)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalInterviewees").value(0))
                .andExpect(jsonPath("$.summary.passedCount").value(0))
                .andExpect(jsonPath("$.summary.failedCount").value(0))
                .andExpect(jsonPath("$.summary.holdCount").value(0))
                .andExpect(jsonPath("$.interviewList").isArray())
                .andExpect(jsonPath("$.interviewList").isEmpty());
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
                .andExpect(jsonPath("$.summary.totalInterviewees").value(3))
                .andExpect(jsonPath("$.summary.passedCount").value(1))
                .andExpect(jsonPath("$.summary.failedCount").value(0))
                .andExpect(jsonPath("$.summary.holdCount").value(1))
                .andExpect(jsonPath("$.interviewList[0].applicationId").value(firstPassApplication.getId()))
                .andExpect(jsonPath("$.interviewList[0].availableSessions[0]").value("FIRST_SESSION"))
                .andExpect(jsonPath("$.interviewList[0].availableSessions[1]").value("SECOND_SESSION"))
                .andExpect(jsonPath("$.interviewList[0].scheduledAt").doesNotExist())
                .andExpect(jsonPath("$.interviewList[1].applicationId").value(finalPassApplication.getId()))
                .andExpect(jsonPath("$.interviewList[1].scheduledAt").value("SECOND_SESSION"))
                .andExpect(jsonPath("$.interviewList[1].memo").value("레슨 경험 있음"))
                .andExpect(jsonPath("$.interviewList[2].applicationId").value(holdApplication.getId()));

        assertThat(applicationRepository.findById(failApplication.getId())).isPresent();
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
                .andExpect(jsonPath("$.summary.totalInterviewees").value(2))
                .andExpect(jsonPath("$.summary.passedCount").value(1))
                .andExpect(jsonPath("$.summary.failedCount").value(0))
                .andExpect(jsonPath("$.summary.holdCount").value(1))
                .andExpect(jsonPath("$.interviewList[0].applicationId").value(firstPassApplication.getId()))
                .andExpect(jsonPath("$.interviewList[0].scheduledAt").value("FIRST_SESSION"))
                .andExpect(jsonPath("$.interviewList[0].status").value("FINAL_PASS"))
                .andExpect(jsonPath("$.interviewList[0].memo").value("양배추 지인"));

        Application updatedApplication = applicationRepository.findById(firstPassApplication.getId()).orElseThrow();
        Interview interview = interviewRepository.findByApplication_Id(firstPassApplication.getId()).orElseThrow();
        assertThat(updatedApplication.getStatus()).isEqualTo(Status.FINAL_PASS);
        assertThat(interview.getScheduledAt()).isEqualTo(InterviewTime.FIRST_SESSION);
        assertThat(interview.getMemo()).isEqualTo("양배추 지인");
    }

    @Test
    @DisplayName("면접 관리에서 최종 합격으로 변경되면 부원 명단에 자동 추가된다")
    void updateInterviewCreatesClubMemberWhenStatusBecomesFinalPass() throws Exception {
        Application application = saveApplication("김철수", Status.FIRST_PASS, List.of(InterviewTime.FIRST_SESSION));

        mockMvc.perform(patch(ADMIN_INTERVIEWS_API + "/{applicationId}", application.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledAt", "FIRST_SESSION",
                                "status", "FINAL_PASS",
                                "memo", "합격"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewList[0].status").value("FINAL_PASS"));

        ClubMember clubMember = clubMemberRepository.findByApplicationId(application.getId()).orElseThrow();
        assertThat(clubMember.getAge()).isEqualTo(expectedBirthYear(24));
        assertThat(clubMember.getTerm()).isEqualTo("미정");
    }

    @Test
    @DisplayName("면접 관리에서 최종 합격이 해제되면 부원 명단에서 자동 삭제된다")
    void updateInterviewDeletesClubMemberWhenStatusLeavesFinalPass() throws Exception {
        Application application = saveApplication("김철수", Status.FIRST_PASS, List.of(InterviewTime.FIRST_SESSION));

        mockMvc.perform(patch(ADMIN_INTERVIEWS_API + "/{applicationId}", application.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledAt", "FIRST_SESSION",
                                "status", "FINAL_PASS",
                                "memo", "합격"
                        ))))
                .andExpect(status().isOk());

        assertThat(clubMemberRepository.findByApplicationId(application.getId())).isPresent();

        mockMvc.perform(patch(ADMIN_INTERVIEWS_API + "/{applicationId}", application.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "scheduledAt", "SECOND_SESSION",
                                "status", "HOLD",
                                "memo", "재검토"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewList[0].status").value("HOLD"));

        assertThat(clubMemberRepository.findByApplicationId(application.getId())).isEmpty();
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
                .term("10기")
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

    private String expectedBirthYear(int age) {
        int birthYear = java.time.Year.now().getValue() - age + 1;
        return String.format("%02d", Math.floorMod(birthYear, 100));
    }
}
