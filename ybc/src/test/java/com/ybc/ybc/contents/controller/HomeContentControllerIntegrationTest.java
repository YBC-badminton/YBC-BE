package com.ybc.ybc.contents.controller;

import com.ybc.ybc.contents.domain.HomeContent;
import com.ybc.ybc.contents.repository.HomeContentRepository;
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
class HomeContentControllerIntegrationTest {
    private static final String HOME_CONTENT_API = "/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HomeContentRepository homeContentRepository;

    @BeforeEach
    void setUp() {
        homeContentRepository.deleteAll();
    }

    @Test
    @DisplayName("메인페이지 내용을 조회할 수 있다")
    void getHomeContentReturnsHomeContent() throws Exception {
        homeContentRepository.saveAndFlush(HomeContent.create(
                "YBC는 배드민턴 동아리입니다.",
                "https://storage.googleapis.com/ybc/images/main.jpg",
                2,
                45
        ));

        mockMvc.perform(get(HOME_CONTENT_API))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clubIntroduction").value("YBC는 배드민턴 동아리입니다."))
                .andExpect(jsonPath("$.activityImageUrl").value("https://storage.googleapis.com/ybc/images/main.jpg"))
                .andExpect(jsonPath("$.regularMeetingCount").value(2))
                .andExpect(jsonPath("$.memberCount").value(45));
    }

    @Test
    @DisplayName("메인페이지 내용이 없으면 404를 반환한다")
    void getHomeContentReturnsNotFoundWhenHomeContentDoesNotExist() throws Exception {
        mockMvc.perform(get(HOME_CONTENT_API))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("HOME_CONTENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("메인페이지 내용을 찾을 수 없습니다."));
    }
}
