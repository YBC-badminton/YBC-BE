package com.ybc.ybc.contents.controller;

import com.ybc.ybc.contents.domain.PrivacyPolicy;
import com.ybc.ybc.contents.repository.PrivacyPolicyRepository;
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
class PrivacyPolicyControllerIntegrationTest {
    private static final String PRIVACY_POLICY_API = "/privacy-policy";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrivacyPolicyRepository privacyPolicyRepository;

    @BeforeEach
    void setUp() {
        privacyPolicyRepository.deleteAll();
    }

    @Test
    @DisplayName("개인정보 처리방침을 조회할 수 있다")
    void getPrivacyPolicyReturnsPrivacyPolicy() throws Exception {
        PrivacyPolicy privacyPolicy = privacyPolicyRepository.saveAndFlush(
                PrivacyPolicy.create("개인정보 처리방침 내용입니다.")
        );

        mockMvc.perform(get(PRIVACY_POLICY_API))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("개인정보 처리방침 내용입니다."))
                .andExpect(jsonPath("$.updatedAt").value(privacyPolicy.getUpdatedAt().toLocalDate().toString()));
    }

    @Test
    @DisplayName("개인정보 처리방침이 없으면 404를 반환한다")
    void getPrivacyPolicyReturnsNotFoundWhenPrivacyPolicyDoesNotExist() throws Exception {
        mockMvc.perform(get(PRIVACY_POLICY_API))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRIVACY_POLICY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("개인정보 처리방침을 찾을 수 없습니다."));
    }
}
