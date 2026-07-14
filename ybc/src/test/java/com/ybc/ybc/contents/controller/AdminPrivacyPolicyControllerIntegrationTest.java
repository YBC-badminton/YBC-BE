package com.ybc.ybc.contents.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestMailConfig.class)
class AdminPrivacyPolicyControllerIntegrationTest {
    private static final String ADMIN_PRIVACY_POLICY_API = "/admin/content/privacy-policy";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrivacyPolicyRepository privacyPolicyRepository;

    @Autowired
    private MemberRepository memberRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private Member adminMember;
    private Member nonAdminMember;

    @BeforeEach
    void setUp() {
        privacyPolicyRepository.deleteAll();
        memberRepository.deleteAll();
        adminMember = memberRepository.save(Member.createSocialMember(
                SocialProvider.KAKAO,
                "123456789",
                "관리자",
                "admin@example.com",
                true
        ));
        nonAdminMember = memberRepository.save(Member.createSocialMember(
                SocialProvider.KAKAO,
                "987654321",
                "일반회원",
                "user@example.com",
                false
        ));
    }

    @Test
    @DisplayName("관리자는 개인정보 처리방침 내용을 수정할 수 있다")
    void updatePrivacyPolicyReturnsUpdatedPrivacyPolicyWhenAdmin() throws Exception {
        mockMvc.perform(patch(ADMIN_PRIVACY_POLICY_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "개인정보 처리방침 내용입니다."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("개인정보 처리방침 내용입니다."))
                .andExpect(jsonPath("$.updatedAt").value(LocalDate.now().toString()));

        PrivacyPolicy savedPrivacyPolicy = privacyPolicyRepository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(savedPrivacyPolicy.getContent()).isEqualTo("개인정보 처리방침 내용입니다.");
    }

    @Test
    @DisplayName("인증 정보가 없으면 개인정보 처리방침 수정은 401을 반환한다")
    void updatePrivacyPolicyReturnsUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(patch(ADMIN_PRIVACY_POLICY_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "개인정보 처리방침 내용입니다."
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("관리자가 아니면 개인정보 처리방침 수정은 401을 반환한다")
    void updatePrivacyPolicyReturnsUnauthorizedWhenUserIsNotAdmin() throws Exception {
        mockMvc.perform(patch(ADMIN_PRIVACY_POLICY_API)
                        .with(authorizedNonAdminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "개인정보 처리방침 내용입니다."
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없는 회원입니다."));
    }

    @Test
    @DisplayName("내용이 없으면 개인정보 처리방침 수정은 400을 반환한다")
    void updatePrivacyPolicyReturnsBadRequestWhenContentIsMissing() throws Exception {
        mockMvc.perform(patch(ADMIN_PRIVACY_POLICY_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ILLEGAL_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("필수 파라미터 누락"));
    }

    private RequestPostProcessor authorizedUser() {
        return authenticatedMember(adminMember);
    }

    private RequestPostProcessor authorizedNonAdminUser() {
        return authenticatedMember(nonAdminMember);
    }

    private RequestPostProcessor authenticatedMember(Member member) {
        MemberPrincipal principal = MemberPrincipal.from(MemberSummary.from(member));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        ));
    }
}
