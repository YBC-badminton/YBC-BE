package com.ybc.ybc.brands.controller;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.brands.domain.Brand;
import com.ybc.ybc.brands.repository.BrandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BrandControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("키워드로 브랜드를 검색한다")
    void getBrandsByKeyword() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );
        Brand yonex = brandRepository.save(Brand.create("YONEX"));
        brandRepository.save(Brand.create("WILSON"));

        mockMvc.perform(get("/brands")
                        .with(authenticatedMember(member))
                        .param("keyword", "yon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brands.length()").value(1))
                .andExpect(jsonPath("$.brands[0].brandId").value(yonex.getId()))
                .andExpect(jsonPath("$.brands[0].name").value("YONEX"));
    }

    @Test
    @DisplayName("키워드가 비어 있으면 전체 브랜드를 이름 오름차순으로 조회한다")
    void getBrandsWithoutKeyword() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "김민수", "3001@example.com")
        );
        Brand victor = brandRepository.save(Brand.create("VICTOR"));
        Brand yonex = brandRepository.save(Brand.create("YONEX"));

        mockMvc.perform(get("/brands")
                        .with(authenticatedMember(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brands.length()").value(2))
                .andExpect(jsonPath("$.brands[0].brandId").value(victor.getId()))
                .andExpect(jsonPath("$.brands[0].name").value("VICTOR"))
                .andExpect(jsonPath("$.brands[1].brandId").value(yonex.getId()))
                .andExpect(jsonPath("$.brands[1].name").value("YONEX"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 브랜드를 조회할 수 없다")
    void getBrandsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/brands"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    private RequestPostProcessor authenticatedMember(Member member) {
        MemberPrincipal principal = MemberPrincipal.from(MemberSummary.from(member));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
