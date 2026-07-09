package com.ybc.ybc.faqs.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.faqs.domain.Faq;
import com.ybc.ybc.faqs.repository.FaqRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestMailConfig.class)
class AdminFaqControllerIntegrationTest {
    private static final String ADMIN_FAQS_API = "/admin/content/faqs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FaqRepository faqRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @BeforeEach
    void setUp() {
        faqRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사용자는 FAQ를 등록할 수 있다")
    void createFaqReturnsCreatedFaqWhenAuthenticated() throws Exception {
        mockMvc.perform(post(ADMIN_FAQS_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "초보자도 가입할 수 있나요?",
                                "answer", "네. 초보자도 가입 가능합니다."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faqId").isNumber())
                .andExpect(jsonPath("$.question").value("초보자도 가입할 수 있나요?"))
                .andExpect(jsonPath("$.answer").value("네. 초보자도 가입 가능합니다."));

        assertThat(faqRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("인증 정보가 없으면 FAQ 등록은 401을 반환한다")
    void createFaqReturnsUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(post(ADMIN_FAQS_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "초보자도 가입할 수 있나요?",
                                "answer", "네. 초보자도 가입 가능합니다."
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("질문이 없으면 FAQ 등록 시 400을 반환한다")
    void createFaqReturnsBadRequestWhenQuestionIsMissing() throws Exception {
        mockMvc.perform(post(ADMIN_FAQS_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "answer", "네. 초보자도 가입 가능합니다."
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ILLEGAL_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("질문을 입력해 주세요."));
    }

    @Test
    @DisplayName("인증된 사용자는 FAQ를 수정할 수 있다")
    void updateFaqReturnsUpdatedFaqWhenAuthenticated() throws Exception {
        Faq faq = saveFaq("초보자도 가입할 수 있나요?", "네. 초보자도 가입 가능합니다.");

        mockMvc.perform(patch(ADMIN_FAQS_API + "/{faqId}", faq.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "라켓이 없어도 되나요?",
                                "answer", "네. 대여 라켓을 이용할 수 있습니다."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faqId").value(faq.getId()))
                .andExpect(jsonPath("$.question").value("라켓이 없어도 되나요?"))
                .andExpect(jsonPath("$.answer").value("네. 대여 라켓을 이용할 수 있습니다."));

        Faq updatedFaq = faqRepository.findById(faq.getId()).orElseThrow();
        assertThat(updatedFaq.getQuestion()).isEqualTo("라켓이 없어도 되나요?");
        assertThat(updatedFaq.getAnswer()).isEqualTo("네. 대여 라켓을 이용할 수 있습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 FAQ 수정은 404를 반환한다")
    void updateFaqReturnsNotFoundWhenFaqDoesNotExist() throws Exception {
        mockMvc.perform(patch(ADMIN_FAQS_API + "/{faqId}", 999L)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "라켓이 없어도 되나요?",
                                "answer", "네. 대여 라켓을 이용할 수 있습니다."
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("FAQ_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 FAQ입니다."));
    }

    @Test
    @DisplayName("인증된 사용자는 FAQ를 삭제할 수 있다")
    void deleteFaqReturnsOkWhenAuthenticated() throws Exception {
        Faq faq = saveFaq("초보자도 가입할 수 있나요?", "네. 초보자도 가입 가능합니다.");

        mockMvc.perform(delete(ADMIN_FAQS_API + "/{faqId}", faq.getId())
                        .with(authorizedUser()))
                .andExpect(status().isOk());

        assertThat(faqRepository.findById(faq.getId())).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 FAQ 삭제는 404를 반환한다")
    void deleteFaqReturnsNotFoundWhenFaqDoesNotExist() throws Exception {
        mockMvc.perform(delete(ADMIN_FAQS_API + "/{faqId}", 999L)
                        .with(authorizedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("FAQ_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 FAQ입니다."));
    }

    private Faq saveFaq(String question, String answer) {
        return faqRepository.saveAndFlush(Faq.create(question, answer));
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
