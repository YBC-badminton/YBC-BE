package com.ybc.ybc.contents.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.contents.domain.ActivitiesContent;
import com.ybc.ybc.contents.repository.ActivitiesContentRepository;
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

import java.util.List;
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
class AdminActivitiesContentControllerIntegrationTest {
    private static final String ADMIN_ACTIVITIES_CONTENT_API = "/admin/content/activities";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActivitiesContentRepository activitiesContentRepository;

    @Autowired
    private MemberRepository memberRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private Member adminMember;
    private Member nonAdminMember;

    @BeforeEach
    void setUp() {
        activitiesContentRepository.deleteAll();
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
    @DisplayName("관리자는 지난 활동 내용을 수정할 수 있다")
    void updateActivitiesContentReturnsUpdatedActivitiesContentWhenAdmin() throws Exception {
        mockMvc.perform(patch(ADMIN_ACTIVITIES_CONTENT_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainTitle").value("YBC의 지난 활동"))
                .andExpect(jsonPath("$.subDescription").value("YBC의 다양한 활동을 소개합니다."))
                .andExpect(jsonPath("$.regularMeeting.description").value("매주 정기적으로 운동을 진행합니다."))
                .andExpect(jsonPath("$.regularMeeting.imageUrls[0]").value("https://storage.googleapis.com/ybc/images/regular1.jpg"))
                .andExpect(jsonPath("$.events[0].title").value("리그전"))
                .andExpect(jsonPath("$.events[1].description").value("친목을 위한 MT"))
                .andExpect(jsonPath("$.afterParties.description[2]").value("뒤풀이 진행"))
                .andExpect(jsonPath("$.afterParties.imageUrl").value("https://storage.googleapis.com/ybc/images/after-party.jpg"));

        ActivitiesContent savedActivitiesContent = activitiesContentRepository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(savedActivitiesContent.getMainTitle()).isEqualTo("YBC의 지난 활동");
        assertThat(savedActivitiesContent.getRegularMeetingImageUrls()).containsExactly(
                "https://storage.googleapis.com/ybc/images/regular1.jpg",
                "https://storage.googleapis.com/ybc/images/regular2.jpg"
        );
        assertThat(savedActivitiesContent.getEvents()).hasSize(2);
        assertThat(savedActivitiesContent.getAfterPartyDescriptions()).containsExactly("운동 후 식사", "친목 활동", "뒤풀이 진행");
    }

    @Test
    @DisplayName("인증 정보가 없으면 지난 활동 내용 수정은 401을 반환한다")
    void updateActivitiesContentReturnsUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(patch(ADMIN_ACTIVITIES_CONTENT_API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestBody())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("관리자가 아니면 지난 활동 내용 수정은 401을 반환한다")
    void updateActivitiesContentReturnsUnauthorizedWhenUserIsNotAdmin() throws Exception {
        mockMvc.perform(patch(ADMIN_ACTIVITIES_CONTENT_API)
                        .with(authorizedNonAdminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestBody())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없는 회원입니다."));
    }

    @Test
    @DisplayName("필수값 개수가 맞지 않으면 지난 활동 내용 수정은 400을 반환한다")
    void updateActivitiesContentReturnsBadRequestWhenRequiredValueCountIsInvalid() throws Exception {
        mockMvc.perform(patch(ADMIN_ACTIVITIES_CONTENT_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mainTitle": "YBC의 지난 활동",
                                  "subDescription": "YBC의 다양한 활동을 소개합니다.",
                                  "regularMeeting": {
                                    "description": "매주 정기적으로 운동을 진행합니다.",
                                    "imageUrls": [
                                      "https://storage.googleapis.com/ybc/images/regular1.jpg"
                                    ]
                                  },
                                  "events": [
                                    {
                                      "title": "리그전",
                                      "description": "부원들과 함께하는 리그전",
                                      "imageUrl": "https://storage.googleapis.com/ybc/images/event1.jpg"
                                    },
                                    {
                                      "title": "MT",
                                      "description": "친목을 위한 MT",
                                      "imageUrl": "https://storage.googleapis.com/ybc/images/event2.jpg"
                                    }
                                  ],
                                  "afterParties": {
                                    "description": [
                                      "운동 후 식사",
                                      "친목 활동",
                                      "뒤풀이 진행"
                                    ],
                                    "imageUrl": "https://storage.googleapis.com/ybc/images/after-party.jpg"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ILLEGAL_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("필수 파라미터 누락"));
    }

    private Map<String, Object> validRequestBody() {
        return Map.of(
                "mainTitle", "YBC의 지난 활동",
                "subDescription", "YBC의 다양한 활동을 소개합니다.",
                "regularMeeting", Map.of(
                        "description", "매주 정기적으로 운동을 진행합니다.",
                        "imageUrls", List.of(
                                "https://storage.googleapis.com/ybc/images/regular1.jpg",
                                "https://storage.googleapis.com/ybc/images/regular2.jpg"
                        )
                ),
                "events", List.of(
                        Map.of(
                                "title", "리그전",
                                "description", "부원들과 함께하는 리그전",
                                "imageUrl", "https://storage.googleapis.com/ybc/images/event1.jpg"
                        ),
                        Map.of(
                                "title", "MT",
                                "description", "친목을 위한 MT",
                                "imageUrl", "https://storage.googleapis.com/ybc/images/event2.jpg"
                        )
                ),
                "afterParties", Map.of(
                        "description", List.of("운동 후 식사", "친목 활동", "뒤풀이 진행"),
                        "imageUrl", "https://storage.googleapis.com/ybc/images/after-party.jpg"
                )
        );
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
