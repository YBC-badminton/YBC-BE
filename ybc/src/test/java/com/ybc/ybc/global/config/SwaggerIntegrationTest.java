package com.ybc.ybc.global.config;

import com.ybc.ybc.auth.config.JwtProperties;
import com.ybc.ybc.auth.service.SwaggerLoginService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void swaggerUiRedirectsToSwaggerLoginWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-login"));
    }

    @Test
    void swaggerLoginPageIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-login"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Swagger 로그인")));
    }

    @Test
    void openApiDocsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    void openApiDocsRejectNonAdminUser() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .with(jwt().authorities(createAuthorityList("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void swaggerLoginSetsCookieAndAllowsSwaggerAccess() throws Exception {
        String accessToken = createAccessToken(8L, false);

        mockMvc.perform(post("/swagger-login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("accessToken", accessToken))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-ui/index.html"))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString(SwaggerLoginService.SWAGGER_ACCESS_TOKEN_COOKIE_NAME + "=")
                ));

        mockMvc.perform(get("/v3/api-docs")
                        .cookie(new Cookie(SwaggerLoginService.SWAGGER_ACCESS_TOKEN_COOKIE_NAME, accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.info.title").value("YBC API"))
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test
    void swaggerLoginRejectsUnauthorizedUser() throws Exception {
        mockMvc.perform(post("/swagger-login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("accessToken", createAccessToken(7L, false)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-login?errorCode=UNAUTHORIZED_USER"));
    }

    @Test
    void swaggerLoginRejectsInvalidToken() throws Exception {
        mockMvc.perform(post("/swagger-login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("accessToken", "invalid-token"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/swagger-login?errorCode=INVALID_JWT_TOKEN"));
    }

    @Test
    void openApiDocsAllowMemberIdEightUser() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject("8"))
                                .authorities(createAuthorityList("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.info.title").value("YBC API"))
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test
    void openApiDocsAllowAdminUser() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .with(jwt().authorities(createAuthorityList("ROLE_USER", "ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.info.title").value("YBC API"))
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    private String createAccessToken(long memberId, boolean isAdmin) {
        Instant now = Instant.now();
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.accessTokenExpirationSeconds()))
                .subject(String.valueOf(memberId))
                .claim("tokenType", "access")
                .claim("provider", "KAKAO")
                .claim("socialId", "swagger-" + memberId)
                .claim("email", "swagger" + memberId + "@example.com")
                .claim("nickname", "swagger" + memberId)
                .claim("isAdmin", isAdmin)
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claimsSet)).getTokenValue();
    }
}
