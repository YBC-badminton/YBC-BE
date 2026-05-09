package com.ybc.ybc.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.support.TestMailConfig;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.KakaoUserProfile;
import com.ybc.ybc.auth.repository.MemberRepository;
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

import java.time.Duration;
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
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

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
        assertThat(refreshTokenStore).containsEntry("auth:refresh:" + memberId, refreshToken);
    }

    @Test
    void kakaoCallbackRedirectsToFrontendWithTokensInQueryString() throws Exception {
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
                .andExpect(jsonPath("$.email").value("cabbage@example.com"));
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
                        .header("Origin", "https://ybckk.vercel.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://ybckk.vercel.app"))
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

    private LoginContext login(String code, KakaoUserProfile kakaoUserProfile) throws Exception {
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
