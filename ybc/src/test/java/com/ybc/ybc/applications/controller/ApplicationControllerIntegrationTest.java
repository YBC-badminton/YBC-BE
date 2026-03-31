package com.ybc.ybc.applications.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApplicationControllerIntegrationTest {
    private static final String APPLICATIONS_API = "/api/v1/applications";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
    }

    @Test
    @DisplayName("비로그인 사용자도 지원서를 제출하면 201 응답과 함께 저장된다")
    // 비로그인 사용자가 지원서를 제출하면 201 응답과 함께 실제로 저장되어야 한다.
    void createApplicationCreatesApplicationWithoutAuthentication() throws Exception {
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
        assertThat(application.getPhone()).isEqualTo(request.get("phone"));
        assertThat(application.getUniversity()).isEqualTo(request.get("university"));
        assertThat(application.getMajor()).isEqualTo(request.get("major"));
        assertThat(application.getIntroduction()).isEqualTo(request.get("introduction"));
        assertThat(application.getMotivation()).isEqualTo(request.get("motivation"));
        assertThat(application.getEquipment()).isEqualTo(request.get("equipment"));
        assertThat(application.getInterviewTimes()).containsExactly("2026-04-02 14:00", "2026-04-03 16:00");
        assertThat(application.getDiscoverySource().name()).isEqualTo(request.get("discoverySource"));
        assertThat(application.getDiscoveryEtc()).isEqualTo(request.get("discoveryEtc"));
        assertThat(application.isWantsStaff()).isEqualTo(request.get("wantsStaff"));
        assertThat(application.getFinalComment()).isEqualTo(request.get("finalComment"));
        assertThat(application.getStatus().name()).isEqualTo(request.get("status"));
        assertThat(application.getEmail()).isEqualTo(request.get("email"));
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

    private Map<String, Object> validRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "홍길동");
        request.put("address", "서울특별시 서대문구");
        request.put("gender", "male");
        request.put("age", 23);
        request.put("phone", "010-1234-5678");
        request.put("university", "이화여자대학교");
        request.put("major", "체육과학부");
        request.put("introduction", "배드민턴을 꾸준히 쳐 왔고 동아리 활동에도 적극적으로 참여하고 싶습니다.");
        request.put("motivation", "함께 배드민턴을 즐기며 좋은 사람들과 교류하고 싶어서 지원했습니다.");
        request.put("equipment", "배드민턴 라켓 2자루와 실내 운동화");
        request.put("interviewTimes", List.of("2026-04-02 14:00", "2026-04-03 16:00"));
        request.put("discoverySource", "instagram");
        request.put("discoveryEtc", "인스타그램 모집 게시글");
        request.put("wantsStaff", true);
        request.put("finalComment", "열심히 참여하겠습니다.");
        request.put("status", "NONE");
        request.put("email", "hong@example.com");
        return request;
    }
}
