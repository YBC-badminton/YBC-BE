package com.ybc.ybc.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.KakaoUserProfile;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import com.ybc.ybc.auth.service.KakaoApiClient;
import com.ybc.ybc.support.TestMailConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({AuthControllerIntegrationTest.MockConfig.class, TestMailConfig.class})
class AuthControllerIntegrationTest {
    private static final String LOCAL_FRONTEND_CALLBACK = "http://localhost:3000/auth/kakao/callback";
    private static final String DEPLOYED_FRONTEND_ORIGIN = "https://ybcbadminton.co.kr";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ClubMemberRepository clubMemberRepository;

    @Autowired
    private KakaoApiClient kakaoApiClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private final Map<String, String> refreshTokenStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        clubMemberRepository.deleteAll();
        memberRepository.deleteAll();
        refreshTokenStore.clear();

        Mockito.reset(kakaoApiClient, stringRedisTemplate);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        doAnswer(invocation -> {
            refreshTokenStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> refreshTokenStore.get(invocation.getArgument(0)));
        when(stringRedisTemplate.delete(anyString()))
                .thenAnswer(invocation -> refreshTokenStore.remove(invocation.getArgument(0)) != null);
    }

    @Test
    void loginWithKakaoCreatesMemberAndReturnsTokens() throws Exception {
        saveClubMember("양배추", "cabbage@example.com");

        when(kakaoApiClient.fetchUserProfile("auth-code"))
                .thenReturn(new KakaoUserProfile("123456789", "양배추", "cabbage@example.com"));

        String responseBody = mockMvc.perform(post("/api/v1/auth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "auth-code"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstLogin").value(true))
                .andExpect(jsonPath("$.tokens.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.tokens.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokens.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.member.provider").value("KAKAO"))
                .andExpect(jsonPath("$.member.socialId").value("123456789"))
                .andExpect(jsonPath("$.member.term").value("10기"))
                .andExpect(jsonPath("$.member.isAdmin").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        Long memberId = json.get("member").get("id").asLong();
        String refreshToken = json.get("tokens").get("refreshToken").asText();

        Optional<Member> savedMember = memberRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, "123456789");
        assertThat(savedMember).isPresent();
        assertThat(savedMember.get().getNickname()).isEqualTo("양배추");
        assertThat(savedMember.get().getEmail()).isEqualTo("cabbage@example.com");
        assertThat(savedMember.get().isAdmin()).isFalse();
        assertThat(refreshTokenStore).containsEntry("auth:refresh:" + memberId, refreshToken);
    }

    @Test
    void loginWithKakaoReturnsAdminFlagForExistingAdminMember() throws Exception {
        saveClubMember("운영진닉네임", "staff@example.com");
        Member existingMember = memberRepository.save(Member.createSocialMember(
                SocialProvider.KAKAO,
                "123456789",
                "기존운영진",
                "staff@example.com",
                true
        ));

        when(kakaoApiClient.fetchUserProfile("auth-code"))
                .thenReturn(new KakaoUserProfile("123456789", "운영진닉네임", "staff@example.com"));

        mockMvc.perform(post("/api/v1/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", "auth-code"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstLogin").value(false))
                .andExpect(jsonPath("$.member.id").value(existingMember.getId()))
                .andExpect(jsonPath("$.member.term").value("10기"))
                .andExpect(jsonPath("$.member.isAdmin").value(true));
    }

    @Test
    void loginWithKakaoAllowsUserWhenClubMemberEmailDoesNotExist() throws Exception {
        when(kakaoApiClient.fetchUserProfile("auth-code"))
                .thenReturn(new KakaoUserProfile("123456789", "외부인", "outsider@example.com"));

        String responseBody = mockMvc.perform(post("/api/v1/auth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "auth-code"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstLogin").value(true))
                .andExpect(jsonPath("$.member.nickname").value("외부인"))
                .andExpect(jsonPath("$.member.email").value("outsider@example.com"))
                .andExpect(jsonPath("$.member.isAdmin").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        Long memberId = json.get("member").get("id").asLong();
        assertThat(json.get("member").get("term").isNull()).isTrue();

        Optional<Member> savedMember = memberRepository.findBySocialProviderAndSocialId(SocialProvider.KAKAO, "123456789");
        assertThat(savedMember).isPresent();
        assertThat(savedMember.get().getId()).isEqualTo(memberId);
        assertThat(savedMember.get().getEmail()).isEqualTo("outsider@example.com");
    }

    @Test
    void loginWithKakaoRejectsUserWhenKakaoEmailIsMissing() throws Exception {
        when(kakaoApiClient.fetchUserProfile("auth-code"))
                .thenReturn(new KakaoUserProfile("123456789", "이메일없음", null));

        mockMvc.perform(post("/api/v1/auth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "auth-code"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("KAKAO_EMAIL_REQUIRED"))
                .andExpect(jsonPath("$.message").value("카카오 계정 이메일 정보가 필요합니다."));
    }

    @Test
    void getKakaoLoginUrlIncludesRequestedFrontendRedirectUriInState() throws Exception {
        String responseBody = mockMvc.perform(get("/api/v1/auth/kakao/login-url")
                        .param("frontendRedirectUri", LOCAL_FRONTEND_CALLBACK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("KAKAO"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        String loginUrl = json.get("loginUrl").asText();
        String state = UriComponentsBuilder.fromUriString(loginUrl).build().getQueryParams().getFirst("state");

        assertThat(state).isNotBlank();
        assertThat(new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8))
                .isEqualTo(LOCAL_FRONTEND_CALLBACK);
    }

    @Test
    void getKakaoLoginUrlRejectsUnknownFrontendRedirectUri() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/login-url")
                        .param("frontendRedirectUri", "https://evil.example.com/auth/kakao/callback"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_FRONTEND_REDIRECT_URI"));
    }

    @Test
    void kakaoCallbackRedirectsToFrontendWithTokensInQueryString() throws Exception {
        saveClubMember("양배추", "cabbage@example.com");

        when(kakaoApiClient.fetchUserProfile("auth-code"))
                .thenReturn(new KakaoUserProfile("123456789", "양배추", "cabbage@example.com"));

        String location = mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("code", "auth-code"))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(location).startsWith("https://ybc-fe.vercel.app/auth/kakao/callback");
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("accessToken"))
                .isNotBlank();
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("refreshToken"))
                .isNotBlank();
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("firstLogin"))
                .isEqualTo("true");
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("isAdmin"))
                .isEqualTo("false");
    }

    @Test
    void kakaoCallbackRedirectsToRequestedFrontendWithTokensInQueryStringWhenStateProvided() throws Exception {
        saveClubMember("양배추", "cabbage@example.com");

        when(kakaoApiClient.fetchUserProfile("auth-code"))
                .thenReturn(new KakaoUserProfile("123456789", "양배추", "cabbage@example.com"));

        String state = fetchKakaoLoginState(LOCAL_FRONTEND_CALLBACK);

        String location = mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("code", "auth-code")
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(location).startsWith(LOCAL_FRONTEND_CALLBACK);
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("accessToken"))
                .isNotBlank();
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("refreshToken"))
                .isNotBlank();
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("firstLogin"))
                .isEqualTo("true");
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("isAdmin"))
                .isEqualTo("false");
    }

    @Test
    void kakaoCallbackRedirectsToFrontendWithTokensWhenClubMemberEmailDoesNotExist() throws Exception {
        when(kakaoApiClient.fetchUserProfile("auth-code"))
                .thenReturn(new KakaoUserProfile("123456789", "외부인", "outsider@example.com"));

        String location = mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("code", "auth-code"))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(location).startsWith("https://ybc-fe.vercel.app/auth/kakao/callback");
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("accessToken"))
                .isNotBlank();
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("refreshToken"))
                .isNotBlank();
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("firstLogin"))
                .isEqualTo("true");
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("isAdmin"))
                .isEqualTo("false");
    }

    @Test
    void kakaoCallbackRedirectsToDefaultFrontendWithErrorQueryWhenStateIsInvalid() throws Exception {
        String invalidState = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("https://evil.example.com/auth/kakao/callback".getBytes(StandardCharsets.UTF_8));

        String location = mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("code", "auth-code")
                        .param("state", invalidState))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(location).startsWith("https://ybc-fe.vercel.app/auth/kakao/callback");
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("error"))
                .isEqualTo("INVALID_FRONTEND_REDIRECT_URI");
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("status"))
                .isEqualTo("400");
    }

    @Test
    void getCurrentMemberReturnsMemberInfoWhenAccessTokenIsValid() throws Exception {
        LoginContext loginContext = login("auth-code", new KakaoUserProfile("123456789", "양배추", "cabbage@example.com"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + loginContext.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(loginContext.memberId()))
                .andExpect(jsonPath("$.provider").value("KAKAO"))
                .andExpect(jsonPath("$.socialId").value("123456789"))
                .andExpect(jsonPath("$.nickname").value("양배추"))
                .andExpect(jsonPath("$.email").value("cabbage@example.com"))
                .andExpect(jsonPath("$.term").value("10기"))
                .andExpect(jsonPath("$.isAdmin").value(false));
    }

    @Test
    void refreshReissuesTokensWhenRefreshTokenIsValid() throws Exception {
        LoginContext loginContext = login("auth-code", new KakaoUserProfile("123456789", "양배추", "cabbage@example.com"));

        String responseBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", loginContext.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        String reissuedAccessToken = json.get("accessToken").asText();
        String reissuedRefreshToken = json.get("refreshToken").asText();

        assertThat(reissuedAccessToken).isNotEqualTo(loginContext.accessToken());
        assertThat(reissuedRefreshToken).isNotEqualTo(loginContext.refreshToken());
        assertThat(refreshTokenStore).containsEntry("auth:refresh:" + loginContext.memberId(), reissuedRefreshToken);
    }

    @Test
    void preflightRequestAllowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/kakao/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void preflightRequestAllowsDeployedFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/kakao/login")
                        .header("Origin", DEPLOYED_FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", DEPLOYED_FRONTEND_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void logoutDeletesRefreshTokenAndBlocksReissue() throws Exception {
        LoginContext loginContext = login("auth-code", new KakaoUserProfile("123456789", "양배추", "cabbage@example.com"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + loginContext.accessToken()))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenStore).doesNotContainKey("auth:refresh:" + loginContext.memberId());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", loginContext.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("REFRESH_TOKEN_NOT_FOUND"));
    }

    private String fetchKakaoLoginState(String frontendRedirectUri) throws Exception {
        String responseBody = mockMvc.perform(get("/api/v1/auth/kakao/login-url")
                        .param("frontendRedirectUri", frontendRedirectUri))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        String loginUrl = json.get("loginUrl").asText();
        return UriComponentsBuilder.fromUriString(loginUrl).build().getQueryParams().getFirst("state");
    }

    private LoginContext login(String code, KakaoUserProfile kakaoUserProfile) throws Exception {
        saveClubMember(kakaoUserProfile.nickname(), kakaoUserProfile.email());
        when(kakaoApiClient.fetchUserProfile(code)).thenReturn(kakaoUserProfile);

        String responseBody = mockMvc.perform(post("/api/v1/auth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);

        return new LoginContext(
                json.get("member").get("id").asLong(),
                json.get("tokens").get("accessToken").asText(),
                json.get("tokens").get("refreshToken").asText()
        );
    }

    private record LoginContext(
            Long memberId,
            String accessToken,
            String refreshToken
    ) {
    }

    private ClubMember saveClubMember(String name, String email) {
        return clubMemberRepository.save(
                ClubMember.create(
                        name,
                        "이화여자대학교",
                        "010-1234-5678",
                        "03",
                        "10기",
                        email,
                        null
                )
        );
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        KakaoApiClient kakaoApiClient() {
            return mock(KakaoApiClient.class);
        }

        @Bean
        @Primary
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }
}
