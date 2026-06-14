package com.ybc.ybc.recruitments.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestMailConfig.class)
class AdminRecruitmentControllerIntegrationTest {
    private static final String ADMIN_RECRUITMENTS_API = "/admin/recruitments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecruitmentRepository recruitmentRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @BeforeEach
    void setUp() {
        recruitmentRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사용자는 모집 기수 목록을 최신순으로 조회할 수 있다")
    void getRecruitmentsReturnsRecruitmentIdsAndTermsInDescendingOrder() throws Exception {
        Recruitment tenth = saveRecruitment("10기");
        Recruitment tenthHalf = saveRecruitment("10.5기");
        Recruitment eleventh = saveRecruitment("11기");
        Recruitment twelfth = saveRecruitment("12기");

        mockMvc.perform(get(ADMIN_RECRUITMENTS_API)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitments[0].recruitmentId").value(twelfth.getId()))
                .andExpect(jsonPath("$.recruitments[0].term").value("12기"))
                .andExpect(jsonPath("$.recruitments[1].recruitmentId").value(eleventh.getId()))
                .andExpect(jsonPath("$.recruitments[1].term").value("11기"))
                .andExpect(jsonPath("$.recruitments[2].recruitmentId").value(tenthHalf.getId()))
                .andExpect(jsonPath("$.recruitments[2].term").value("10.5기"))
                .andExpect(jsonPath("$.recruitments[3].recruitmentId").value(tenth.getId()))
                .andExpect(jsonPath("$.recruitments[3].term").value("10기"));
    }

    @Test
    @DisplayName("인증 정보가 없으면 모집 기수 목록 조회는 401을 반환한다")
    void getRecruitmentsReturnsUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(get(ADMIN_RECRUITMENTS_API))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("인증된 사용자는 모집 기수 상세 정보를 조회할 수 있다")
    void getRecruitmentReturnsRecruitmentDetailWhenAuthenticated() throws Exception {
        Recruitment recruitment = saveRecruitment("10기");

        mockMvc.perform(get(ADMIN_RECRUITMENTS_API + "/{recruitmentId}", recruitment.getId())
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitmentId").value(recruitment.getId()))
                .andExpect(jsonPath("$.term").value("10기"))
                .andExpect(jsonPath("$.startAt").value("2026-04-01T09:00:00"))
                .andExpect(jsonPath("$.endAt").value("2026-04-07T18:00:00"))
                .andExpect(jsonPath("$.firstResultDate").value("2026-04-10"))
                .andExpect(jsonPath("$.finalResultDate").value("2026-04-17"))
                .andExpect(jsonPath("$.interviewDate").value("2026-04-12"))
                .andExpect(jsonPath("$.interviewLocation").value("ECC"))
                .andExpect(jsonPath("$.interviewFirstTime").value("10:00"))
                .andExpect(jsonPath("$.interviewSecondTime").value("14:00"))
                .andExpect(jsonPath("$.otDate").value("2026-04-20"))
                .andExpect(jsonPath("$.recruitmentMessage").value("기초반도 지원 가능합니다."))
                .andExpect(jsonPath("$.membershipFee").value(30000))
                .andExpect(jsonPath("$.activityPeriod").value(6));
    }

    @Test
    @DisplayName("존재하지 않는 모집 기수 상세 조회는 404를 반환한다")
    void getRecruitmentReturnsNotFoundWhenRecruitmentDoesNotExist() throws Exception {
        mockMvc.perform(get(ADMIN_RECRUITMENTS_API + "/{recruitmentId}", 999L)
                        .with(authorizedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RECRUITMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 정보가 없으면 모집 기수 상세 조회는 401을 반환한다")
    void getRecruitmentReturnsUnauthorizedWithoutAccessToken() throws Exception {
        Recruitment recruitment = saveRecruitment("10기");

        mockMvc.perform(get(ADMIN_RECRUITMENTS_API + "/{recruitmentId}", recruitment.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("인증된 사용자는 모집 기수를 생성할 수 있다")
    void createRecruitmentReturnsCreatedRecruitmentWhenAuthenticated() throws Exception {
        mockMvc.perform(post(ADMIN_RECRUITMENTS_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRecruitmentRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recruitmentId").isNumber())
                .andExpect(jsonPath("$.term").value("10.5기"))
                .andExpect(jsonPath("$.startAt").value("2026-03-01T09:00:00"))
                .andExpect(jsonPath("$.endAt").value("2026-03-15T23:59:59"))
                .andExpect(jsonPath("$.firstResultDate").value("2026-03-17"))
                .andExpect(jsonPath("$.finalResultDate").value("2026-03-25"))
                .andExpect(jsonPath("$.interviewDate").value("2026-03-22"))
                .andExpect(jsonPath("$.interviewLocation").value("마포구 청년센터 3층 회의실"))
                .andExpect(jsonPath("$.interviewFirstTime").value("13:00 ~ 15:00"))
                .andExpect(jsonPath("$.interviewSecondTime").value("15:30 ~ 18:00"))
                .andExpect(jsonPath("$.otDate").value("2026-03-28"))
                .andExpect(jsonPath("$.recruitmentMessage").value("배드민턴 경험이 없어도 지원 가능합니다."))
                .andExpect(jsonPath("$.membershipFee").value(50000))
                .andExpect(jsonPath("$.activityPeriod").value(6));
    }

    @Test
    @DisplayName("인증 정보가 없으면 모집 기수 생성은 401을 반환한다")
    void createRecruitmentReturnsUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(post(ADMIN_RECRUITMENTS_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRecruitmentRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("잘못된 모집 일정으로 모집 기수를 생성하면 400을 반환한다")
    void createRecruitmentReturnsBadRequestWhenScheduleIsInvalid() throws Exception {
        mockMvc.perform(post(ADMIN_RECRUITMENTS_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRecruitmentCreateRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_RECRUITMENT_SCHEDULE"));
    }

    @Test
    @DisplayName("인증된 사용자는 모집 기수의 기본 정보를 수정할 수 있다")
    void updateRecruitmentReturnsUpdatedRecruitmentWhenAuthenticated() throws Exception {
        Recruitment recruitment = saveRecruitment("10기");

        mockMvc.perform(patch(ADMIN_RECRUITMENTS_API + "/{recruitmentId}", recruitment.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRecruitmentUpdateRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitmentId").value(recruitment.getId()))
                .andExpect(jsonPath("$.term").value("7.5기"))
                .andExpect(jsonPath("$.startAt").value("2026-03-01T09:00:00"))
                .andExpect(jsonPath("$.endAt").value("2026-03-15T23:59:59"))
                .andExpect(jsonPath("$.firstResultDate").value("2026-04-10"))
                .andExpect(jsonPath("$.finalResultDate").value("2026-04-17"))
                .andExpect(jsonPath("$.interviewDate").value("2026-04-12"));

        Recruitment updatedRecruitment = recruitmentRepository.findById(recruitment.getId()).orElseThrow();
        assertThat(updatedRecruitment.getTerm()).isEqualTo("7.5기");
        assertThat(updatedRecruitment.getStartAt()).isEqualTo(LocalDateTime.of(2026, 3, 1, 9, 0));
        assertThat(updatedRecruitment.getEndAt()).isEqualTo(LocalDateTime.of(2026, 3, 15, 23, 59, 59));
        assertThat(updatedRecruitment.getInterviewLocation()).isEqualTo("ECC");
    }

    @Test
    @DisplayName("존재하지 않는 모집 기수 수정은 404를 반환한다")
    void updateRecruitmentReturnsNotFoundWhenRecruitmentDoesNotExist() throws Exception {
        mockMvc.perform(patch(ADMIN_RECRUITMENTS_API + "/{recruitmentId}", 999L)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRecruitmentUpdateRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RECRUITMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 정보가 없으면 모집 기수 수정은 401을 반환한다")
    void updateRecruitmentReturnsUnauthorizedWithoutAccessToken() throws Exception {
        Recruitment recruitment = saveRecruitment("10기");

        mockMvc.perform(patch(ADMIN_RECRUITMENTS_API + "/{recruitmentId}", recruitment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRecruitmentUpdateRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("기본 정보 수정으로 일정 순서가 깨지면 400을 반환한다")
    void updateRecruitmentReturnsBadRequestWhenScheduleIsInvalid() throws Exception {
        Recruitment recruitment = saveRecruitment("10기");

        mockMvc.perform(patch(ADMIN_RECRUITMENTS_API + "/{recruitmentId}", recruitment.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRecruitmentUpdateRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_RECRUITMENT_SCHEDULE"));
    }

    private Recruitment saveRecruitment(String term) {
        Recruitment recruitment = Recruitment.builder()
                .term(term)
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
                .activityPeriod(6)
                .build();
        Recruitment savedRecruitment = recruitmentRepository.saveAndFlush(recruitment);
        return recruitmentRepository.findById(savedRecruitment.getId()).orElseThrow();
    }

    private JwtRequestPostProcessor authorizedUser() {
        return jwt().jwt(jwt -> jwt
                .subject("1")
                .claim("tokenType", "access")
                .claim("provider", "KAKAO")
                .claim("socialId", "123456789")
                .claim("email", "admin@example.com")
                .claim("nickname", "admin"));
    }

    private Map<String, Object> createRecruitmentRequest() {
        return Map.ofEntries(
                Map.entry("term", "10.5기"),
                Map.entry("startAt", "2026-03-01T09:00:00"),
                Map.entry("endAt", "2026-03-15T23:59:59"),
                Map.entry("firstResultDate", "2026-03-17"),
                Map.entry("finalResultDate", "2026-03-25"),
                Map.entry("interviewDate", "2026-03-22"),
                Map.entry("interviewLocation", "마포구 청년센터 3층 회의실"),
                Map.entry("interviewFirstTime", "13:00 ~ 15:00"),
                Map.entry("interviewSecondTime", "15:30 ~ 18:00"),
                Map.entry("otDate", "2026-03-28"),
                Map.entry("recruitmentMessage", "배드민턴 경험이 없어도 지원 가능합니다."),
                Map.entry("membershipFee", 50000),
                Map.entry("activityPeriod", 6)
        );
    }

    private Map<String, Object> createRecruitmentUpdateRequest() {
        return Map.of(
                "term", "7.5기",
                "startAt", "2026-03-01T09:00:00",
                "endAt", "2026-03-15T23:59:59"
        );
    }

    private Map<String, Object> invalidRecruitmentCreateRequest() {
        return Map.ofEntries(
                Map.entry("term", "10.5기"),
                Map.entry("startAt", "2026-03-15T09:00:00"),
                Map.entry("endAt", "2026-03-15T09:00:00"),
                Map.entry("firstResultDate", "2026-03-17"),
                Map.entry("finalResultDate", "2026-03-25"),
                Map.entry("interviewDate", "2026-03-22"),
                Map.entry("interviewLocation", "마포구 청년센터 3층 회의실"),
                Map.entry("interviewFirstTime", "13:00 ~ 15:00"),
                Map.entry("interviewSecondTime", "15:30 ~ 18:00"),
                Map.entry("otDate", "2026-03-28"),
                Map.entry("membershipFee", 50000),
                Map.entry("activityPeriod", 6)
        );
    }

    private Map<String, Object> invalidRecruitmentUpdateRequest() {
        return Map.of(
                "term", "7.5기",
                "startAt", "2026-04-10T09:00:00",
                "endAt", "2026-04-13T18:00:00"
        );
    }
}
