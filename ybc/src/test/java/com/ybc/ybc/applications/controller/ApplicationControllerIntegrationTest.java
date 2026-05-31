package com.ybc.ybc.applications.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import com.ybc.ybc.applications.service.ApplicationSubmissionMailService;
import com.ybc.ybc.recruitments.domain.Recruitment;
import com.ybc.ybc.recruitments.repository.RecruitmentRepository;
import com.ybc.ybc.support.TestMailConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestMailConfig.class)
class ApplicationControllerIntegrationTest {
    private static final String APPLICATIONS_API = "/applications";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private RecruitmentRepository recruitmentRepository;

    @Autowired
    private ApplicationSubmissionMailService applicationSubmissionMailService;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        recruitmentRepository.deleteAll();
    }

    @Test
    @DisplayName("비로그인 사용자도 지원서를 제출하면 201 응답과 함께 저장된다")
    // 비로그인 사용자가 지원서를 제출하면 201 응답과 함께 실제로 저장되어야 한다.
    void createApplicationCreatesApplicationWithoutAuthentication() throws Exception {
        saveActiveRecruitment("12기");
        Map<String, Object> request = validRequest();

        performCreateApplication(request)
                .andExpect(status().isCreated());

        List<Application> applications = applicationRepository.findAll();
        assertThat(applications).hasSize(1);

        Application application = applications.get(0);
        assertThat(application.getName()).isEqualTo(request.get("name"));
        assertThat(application.getAddress()).isEqualTo(request.get("address"));
        assertThat(application.getGender().name()).isEqualTo(request.get("gender"));
        assertThat(application.getAge()).isEqualTo(request.get("age"));
        assertThat(application.getPhone()).isEqualTo("01012345678");
        assertThat(application.getTerm()).isEqualTo("12기");
        assertThat(application.getUniversity()).isEqualTo(request.get("university"));
        assertThat(application.getMajor()).isEqualTo(request.get("major"));
        assertThat(application.getIntroduction()).isEqualTo(request.get("introduction"));
        assertThat(application.getMotivation()).isEqualTo(request.get("motivation"));
        assertThat(application.getEquipment()).isEqualTo(request.get("equipment"));
        assertThat(application.getInterviewTimes()).extracting(Enum::name)
                .containsExactly("FIRST_SESSION", "SECOND_SESSION");
        assertThat(application.getDiscoverySource().name()).isEqualTo(request.get("discoverySource"));
        assertThat(application.getDiscoveryEtc()).isEqualTo(request.get("discoveryEtc"));
        assertThat(application.isWantsStaff()).isEqualTo(request.get("wantsStaff"));
        assertThat(application.getFinalComment()).isEqualTo(request.get("finalComment"));
        assertThat(application.getStatus().name()).isEqualTo(request.get("status"));
        assertThat(application.getEmail()).isEqualTo(request.get("email"));

        ArgumentCaptor<Application> applicationCaptor = ArgumentCaptor.forClass(Application.class);
        verify(applicationSubmissionMailService).sendApplicationSubmittedMail(applicationCaptor.capture());
        assertThat(applicationCaptor.getValue().getEmail()).isEqualTo(request.get("email"));
        assertThat(applicationCaptor.getValue().getTerm()).isEqualTo("12기");
    }

    @Test
    @DisplayName("비로그인 사용자도 현재 지원 가능 여부를 조회할 수 있다")
    void getApplicationAvailabilityReturnsRecruitingStatusWithoutAuthentication() throws Exception {
        saveActiveRecruitment("12기");

        mockMvc.perform(get(APPLICATIONS_API + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruiting").value(true))
                .andExpect(jsonPath("$.term").value("12기"))
                .andExpect(jsonPath("$.startAt").exists())
                .andExpect(jsonPath("$.endAt").exists());
    }

    @Test
    @DisplayName("모집중인 기수가 없으면 현재 지원 가능 여부는 false를 반환한다")
    void getApplicationAvailabilityReturnsFalseWhenRecruitmentMissing() throws Exception {
        mockMvc.perform(get(APPLICATIONS_API + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruiting").value(false))
                .andExpect(jsonPath("$.term").isEmpty())
                .andExpect(jsonPath("$.startAt").isEmpty())
                .andExpect(jsonPath("$.endAt").isEmpty());
    }

    @Test
    @DisplayName("이름이 비어 있으면 400 응답을 반환하고 저장하지 않는다")
    // 필수 입력값인 이름이 비어 있으면 400 응답을 반환하고 저장되지 않아야 한다.
    void createApplicationReturnsBadRequestWhenNameIsBlank() throws Exception {
        Map<String, Object> invalidRequest = validRequest();
        invalidRequest.put("name", "");

        performCreateApplication(invalidRequest)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ILLEGAL_ARGUMENT"));

        assertThat(applicationRepository.findAll()).isEmpty();
    }

    private ResultActions performCreateApplication(Map<String, Object> request) throws Exception {
        return mockMvc.perform(post(APPLICATIONS_API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private void saveActiveRecruitment(String term) {
        LocalDateTime now = LocalDateTime.now();
        recruitmentRepository.saveAndFlush(Recruitment.builder()
                .term(term)
                .startAt(now.minusDays(1))
                .endAt(now.plusDays(1))
                .firstResultDate(LocalDate.now().plusDays(2))
                .finalResultDate(LocalDate.now().plusDays(5))
                .interviewDate(LocalDate.now().plusDays(6))
                .interviewLocation("이화여대 체육관")
                .interviewFirstTime("18:00")
                .interviewSecondTime("19:00")
                .otDate(LocalDate.now().plusDays(10))
                .membershipFee(30000)
                .activityPeriod(6)
                .build());
    }

    private Map<String, Object> validRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "홍길동");
        request.put("address", "서울특별시 서대문구");
        request.put("gender", "MALE");
        request.put("age", 23);
        request.put("phone", "010-1234-5678");
        request.put("university", "이화여자대학교");
        request.put("major", "체육과학부");
        request.put("introduction", "배드민턴을 꾸준히 쳐 왔고 동아리 활동에도 적극적으로 참여하고 싶습니다.");
        request.put("motivation", "함께 배드민턴을 즐기며 좋은 사람들과 교류하고 싶어서 지원했습니다.");
        request.put("equipment", "배드민턴 라켓 2자루와 실내 운동화");
        request.put("interviewTimes", List.of("FIRST_SESSION", "SECOND_SESSION"));
        request.put("discoverySource", "INSTAGRAM");
        request.put("discoveryEtc", "인스타그램 모집 게시글");
        request.put("wantsStaff", true);
        request.put("finalComment", "열심히 참여하겠습니다.");
        request.put("status", "NONE");
        request.put("email", "hong@example.com");
        return request;
    }
}
