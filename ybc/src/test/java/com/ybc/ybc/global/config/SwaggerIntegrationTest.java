package com.ybc.ybc.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerUiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
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
    void openApiDocsAllowAdminUser() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .with(jwt().authorities(createAuthorityList("ROLE_USER", "ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.info.title").value("YBC API"))
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }
}
