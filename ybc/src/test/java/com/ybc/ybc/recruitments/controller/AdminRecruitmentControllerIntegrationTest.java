package com.ybc.ybc.recruitments.controller;

import com.ybc.ybc.recruitments.domain.Recruitment;
import com.ybc.ybc.recruitments.repository.RecruitmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminRecruitmentControllerIntegrationTest {
    private static final String ADMIN_RECRUITMENTS_API = "/admin/recruitments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecruitmentRepository recruitmentRepository;

    @BeforeEach
    void setUp() {
        recruitmentRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사용자는 모집 기수 목록을 최신순으로 조회할 수 있다")
    void getRecruitmentsReturnsRecruitmentIdsAndTermsInDescendingOrder() throws Exception {
        Recruitment tenth = saveRecruitment("10기");
        Recruitment tenthHalf = saveRecruitment("10.5기");
        Recruitment eleventh = saveRecruitment("11기");
        Recruitment twelfth = saveRecruitment("12기");

        mockMvc.perform(get(ADMIN_RECRUITMENTS_API)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitments[0].recruitmentId").value(twelfth.getId()))
                .andExpect(jsonPath("$.recruitments[0].term").value("12기"))
                .andExpect(jsonPath("$.recruitments[1].recruitmentId").value(eleventh.getId()))
                .andExpect(jsonPath("$.recruitments[1].term").value("11기"))
                .andExpect(jsonPath("$.recruitments[2].recruitmentId").value(tenthHalf.getId()))
                .andExpect(jsonPath("$.recruitments[2].term").value("10.5기"))
                .andExpect(jsonPath("$.recruitments[3].recruitmentId").value(tenth.getId()))
                .andExpect(jsonPath("$.recruitments[3].term").value("10기"));
    }

    @Test
    @DisplayName("인증 정보가 없으면 모집 기수 목록 조회는 401을 반환한다")
    void getRecruitmentsReturnsUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(get(ADMIN_RECRUITMENTS_API))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    private Recruitment saveRecruitment(String term) {
        Recruitment recruitment = Recruitment.builder()
                .term(term)
                .startAt(LocalDateTime.of(2026, 4, 1, 9, 0))
                .endAt(LocalDateTime.of(2026, 4, 7, 18, 0))
                .firstResultDate(LocalDate.of(2026, 4, 10))
                .finalResultDate(LocalDate.of(2026, 4, 17))
                .interviewDate(LocalDate.of(2026, 4, 12))
                .interviewLocation("ECC")
                .interviewFirstTime("10:00")
                .interviewSecondTime("14:00")
                .otDate(LocalDate.of(2026, 4, 20))
                .membershipFee(30000)
                .activityPeriod(6)
                .build();
        Recruitment savedRecruitment = recruitmentRepository.saveAndFlush(recruitment);
        return recruitmentRepository.findById(savedRecruitment.getId()).orElseThrow();
    }

    private JwtRequestPostProcessor authorizedUser() {
        return jwt().jwt(jwt -> jwt
                .subject("1")
                .claim("tokenType", "access")
                .claim("provider", "KAKAO")
                .claim("socialId", "123456789")
                .claim("email", "admin@example.com")
                .claim("nickname", "admin"));
    }
}
