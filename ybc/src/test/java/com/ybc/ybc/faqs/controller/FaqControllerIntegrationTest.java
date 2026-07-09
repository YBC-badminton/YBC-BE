package com.ybc.ybc.faqs.controller;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestMailConfig.class)
class FaqControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FaqRepository faqRepository;

    @BeforeEach
    void setUp() {
        faqRepository.deleteAll();
    }

    @Test
    @DisplayName("인증 없이 FAQ 목록을 조회할 수 있다")
    void getFaqsReturnsFaqListWithoutAuthentication() throws Exception {
        saveFaq("초보자도 가입할 수 있나요?", "네. 초보자도 가입 가능합니다.");
        saveFaq("라켓이 없어도 되나요?", "네. 대여 라켓을 이용할 수 있습니다.");

        mockMvc.perform(get("/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faqs[0].faqId").isNumber())
                .andExpect(jsonPath("$.faqs[0].question").value("초보자도 가입할 수 있나요?"))
                .andExpect(jsonPath("$.faqs[0].answer").value("네. 초보자도 가입 가능합니다."))
                .andExpect(jsonPath("$.faqs[1].question").value("라켓이 없어도 되나요?"))
                .andExpect(jsonPath("$.faqs[1].answer").value("네. 대여 라켓을 이용할 수 있습니다."));
    }

    @Test
    @DisplayName("FAQ가 없으면 목록 조회 시 404를 반환한다")
    void getFaqsReturnsNotFoundWhenFaqIsMissing() throws Exception {
        mockMvc.perform(get("/faqs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("FAQ_INFO_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("FAQ 정보를 찾을 수 없습니다."));
    }

    private Faq saveFaq(String question, String answer) {
        return faqRepository.saveAndFlush(Faq.create(question, answer));
    }
}
