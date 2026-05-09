package com.ybc.ybc.applications.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.DiscoverySource;
import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.applications.domain.InterviewTime;
import com.ybc.ybc.applications.domain.Status;
import com.ybc.ybc.applications.repository.ApplicationRepository;
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

import java.time.LocalDateTime;
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
class AdminApplicationControllerIntegrationTest {
    private static final String ADMIN_APPLICATIONS_API = "/admin/applications";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ClubMemberRepository clubMemberRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @BeforeEach
    void setUp() {
        clubMemberRepository.deleteAll();
        applicationRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사용자는 지원자 목록을 조회할 수 있다")
    void getApplicantsReturnsApplicantListWhenAuthenticated() throws Exception {
        Application firstApplication = saveApplication("김철수", "10기", "010-1234-5678", Status.NONE, Gender.MALE, DiscoverySource.INSTAGRAM);
        Application secondApplication = saveApplication("이영희", "10기", "010-8888-9999", Status.FIRST_PASS, Gender.FEMALE, DiscoverySource.EVERYTIME);

        mockMvc.perform(get(ADMIN_APPLICATIONS_API)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.applicants[0].applicationId").value(secondApplication.getId()))
                .andExpect(jsonPath("$.applicants[0].name").value("이영희"))
                .andExpect(jsonPath("$.applicants[0].term").value("10기"))
                .andExpect(jsonPath("$.applicants[0].isReapplicant").value(false))
                .andExpect(jsonPath("$.applicants[0].gender").value("여성"))
                .andExpect(jsonPath("$.applicants[0].status").value("FIRST_PASS"))
                .andExpect(jsonPath("$.applicants[0].createdAt").value(format(secondApplication.getCreatedAt())))
                .andExpect(jsonPath("$.applicants[1].applicationId").value(firstApplication.getId()))
                .andExpect(jsonPath("$.applicants[1].name").value("김철수"));
    }

    @Test
    @DisplayName("인증된 사용자는 지원자 상세 정보를 조회할 수 있다")
    void getApplicantReturnsApplicantDetailWhenAuthenticated() throws Exception {
        Application application = saveApplication("김철수", "10기", "010-1234-5678", Status.NONE, Gender.MALE, DiscoverySource.INSTAGRAM);

        mockMvc.perform(get(ADMIN_APPLICATIONS_API + "/{applicationId}", application.getId())
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(application.getId()))
                .andExpect(jsonPath("$.name").value("김철수"))
                .andExpect(jsonPath("$.term").value("10기"))
                .andExpect(jsonPath("$.isReapplicant").value(false))
                .andExpect(jsonPath("$.address").value("서울 강서구"))
                .andExpect(jsonPath("$.gender").value("남성"))
                .andExpect(jsonPath("$.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.discoverySource").value("인스타그램"))
                .andExpect(jsonPath("$.interviewAvailableTimes[0]").value("앞타임"))
                .andExpect(jsonPath("$.interviewAvailableTimes[1]").value("뒷타임"))
                .andExpect(jsonPath("$.status").value("NONE"))
                .andExpect(jsonPath("$.createdAt").value(format(application.getCreatedAt())))
                .andExpect(jsonPath("$.email").value("email@example.com"));
    }

    @Test
    @DisplayName("기수로 지원자 목록을 필터링하고 재지원자 여부를 함께 조회할 수 있다")
    void getApplicantsFiltersByTermAndMarksReapplicant() throws Exception {
        saveApplication("김철수", "9기", "01012345678", Status.NONE, Gender.MALE, DiscoverySource.INSTAGRAM);
        Application reapplicant = saveApplication("김철수", "10기", "01012345678", Status.FIRST_PASS, Gender.MALE, DiscoverySource.INSTAGRAM);
        saveApplication("박민수", "9기", "010-5555-6666", Status.NONE, Gender.MALE, DiscoverySource.EVERYTIME);

        mockMvc.perform(get(ADMIN_APPLICATIONS_API)
                        .param("term", "10기")
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.applicants[0].applicationId").value(reapplicant.getId()))
                .andExpect(jsonPath("$.applicants[0].term").value("10기"))
                .andExpect(jsonPath("$.applicants[0].isReapplicant").value(true))
                .andExpect(jsonPath("$.applicants[0].phone").value("010-1234-5678"));
    }

    @Test
    @DisplayName("인증된 사용자는 지원자 상태를 수정할 수 있다")
    void updateApplicantStatusUpdatesStatusWhenAuthenticated() throws Exception {
        Application application = saveApplication("김철수", "10기", "010-1234-5678", Status.NONE, Gender.MALE, DiscoverySource.INSTAGRAM);

        mockMvc.perform(patch(ADMIN_APPLICATIONS_API + "/{applicationId}", application.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "FIRST_PASS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(application.getId()))
                .andExpect(jsonPath("$.status").value("FIRST_PASS"));

        Application updatedApplication = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(updatedApplication.getStatus()).isEqualTo(Status.FIRST_PASS);
    }

    @Test
    @DisplayName("지원자 상태가 최종 합격이 되면 부원 명단에 자동 추가된다")
    void updateApplicantStatusCreatesClubMemberWhenStatusBecomesFinalPass() throws Exception {
        Application application = saveApplication("김철수", "10기", "010-1234-5678", Status.FIRST_PASS, Gender.MALE, DiscoverySource.INSTAGRAM);

        mockMvc.perform(patch(ADMIN_APPLICATIONS_API + "/{applicationId}", application.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "FINAL_PASS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINAL_PASS"));

        ClubMember clubMember = clubMemberRepository.findByApplicationId(application.getId()).orElseThrow();
        assertThat(clubMember.getName()).isEqualTo("김철수");
        assertThat(clubMember.getUniversity()).isEqualTo("한국대학교");
        assertThat(clubMember.getPhone()).isEqualTo("01012345678");
        assertThat(clubMember.getAge()).isEqualTo(expectedBirthYear(24));
        assertThat(clubMember.getTerm()).isEqualTo("미정");
        assertThat(clubMember.getCourt()).isNull();
    }

    @Test
    @DisplayName("지원자 상태가 최종 합격에서 변경되면 부원 명단에서 자동 삭제된다")
    void updateApplicantStatusDeletesClubMemberWhenStatusLeavesFinalPass() throws Exception {
        Application application = saveApplication("김철수", "10기", "010-1234-5678", Status.FIRST_PASS, Gender.MALE, DiscoverySource.INSTAGRAM);

        mockMvc.perform(patch(ADMIN_APPLICATIONS_API + "/{applicationId}", application.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "FINAL_PASS"))))
                .andExpect(status().isOk());

        assertThat(clubMemberRepository.findByApplicationId(application.getId())).isPresent();

        mockMvc.perform(patch(ADMIN_APPLICATIONS_API + "/{applicationId}", application.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "HOLD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HOLD"));

        assertThat(clubMemberRepository.findByApplicationId(application.getId())).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 지원자 상세 조회 시 404를 반환한다")
    void getApplicantReturnsNotFoundWhenApplicationDoesNotExist() throws Exception {
        mockMvc.perform(get(ADMIN_APPLICATIONS_API + "/{applicationId}", 999L)
                        .with(authorizedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("APPLICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("유효하지 않은 지원자 상태값이면 400을 반환한다")
    void updateApplicantStatusReturnsBadRequestWhenStatusIsInvalid() throws Exception {
        Application application = saveApplication("김철수", "10기", "010-1234-5678", Status.NONE, Gender.MALE, DiscoverySource.INSTAGRAM);

        mockMvc.perform(patch(ADMIN_APPLICATIONS_API + "/{applicationId}", application.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "WRONG_STATUS"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_APPLICATION_STATUS"));

        Application unchangedApplication = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(unchangedApplication.getStatus()).isEqualTo(Status.NONE);
    }

    @Test
    @DisplayName("인증 정보가 없으면 지원자 목록 조회는 401을 반환한다")
    void getApplicantsReturnsUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(get(ADMIN_APPLICATIONS_API))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    private Application saveApplication(String name,
                                        String term,
                                        String phone,
                                        Status status,
                                        Gender gender,
                                        DiscoverySource discoverySource) {
        Application application = Application.builder()
                .name(name)
                .address("서울 강서구")
                .gender(gender)
                .age(24)
                .phone(phone)
                .term(term)
                .university("한국대학교")
                .major("컴퓨터공학과")
                .introduction("성실하게 활동할 자신 있습니다...")
                .motivation("운동을 좋아하고 사람들과 어울리는 것을 좋아합니다.")
                .equipment("개인 라켓 보유")
                .discoverySource(discoverySource)
                .discoveryEtc(null)
                .wantsStaff(false)
                .finalComment("열심히 하겠습니다!")
                .status(status)
                .email("email@example.com")
                .build();
        application.addInterviewTime(InterviewTime.FIRST_SESSION);
        application.addInterviewTime(InterviewTime.SECOND_SESSION);
        Application savedApplication = applicationRepository.saveAndFlush(application);
        return applicationRepository.findById(savedApplication.getId()).orElseThrow();
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

    private String format(LocalDateTime createdAt) {
        return createdAt.withNano(0).toString();
    }

    private String expectedBirthYear(int age) {
        int birthYear = java.time.Year.now().getValue() - age + 1;
        return String.format("%02d", Math.floorMod(birthYear, 100));
    }
}
