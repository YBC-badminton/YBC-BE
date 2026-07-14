package com.ybc.ybc.contents.controller;

import com.ybc.ybc.contents.domain.ActivitiesContent;
import com.ybc.ybc.contents.domain.ActivitiesEvent;
import com.ybc.ybc.contents.repository.ActivitiesContentRepository;
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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestMailConfig.class)
class ActivitiesContentControllerIntegrationTest {
    private static final String ACTIVITIES_CONTENT_API = "/activities";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActivitiesContentRepository activitiesContentRepository;

    @BeforeEach
    void setUp() {
        activitiesContentRepository.deleteAll();
    }

    @Test
    @DisplayName("지난 활동 내용을 조회할 수 있다")
    void getActivitiesContentReturnsActivitiesContent() throws Exception {
        activitiesContentRepository.saveAndFlush(ActivitiesContent.create(
                "YBC의 지난 활동",
                "YBC의 다양한 활동을 소개합니다.",
                "매주 정기적으로 운동을 진행합니다.",
                List.of(
                        "https://storage.googleapis.com/ybc/images/regular1.jpg",
                        "https://storage.googleapis.com/ybc/images/regular2.jpg"
                ),
                List.of(
                        ActivitiesEvent.create(
                                "리그전",
                                "부원들과 함께하는 리그전",
                                "https://storage.googleapis.com/ybc/images/event1.jpg"
                        ),
                        ActivitiesEvent.create(
                                "MT",
                                "친목을 위한 MT",
                                "https://storage.googleapis.com/ybc/images/event2.jpg"
                        )
                ),
                List.of("운동 후 식사", "친목 활동", "뒤풀이 진행"),
                "https://storage.googleapis.com/ybc/images/after-party.jpg"
        ));

        mockMvc.perform(get(ACTIVITIES_CONTENT_API))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainTitle").value("YBC의 지난 활동"))
                .andExpect(jsonPath("$.regularMeeting.imageUrls[1]").value("https://storage.googleapis.com/ybc/images/regular2.jpg"))
                .andExpect(jsonPath("$.events[0].title").value("리그전"))
                .andExpect(jsonPath("$.afterParties.description[0]").value("운동 후 식사"))
                .andExpect(jsonPath("$.afterParties.imageUrl").value("https://storage.googleapis.com/ybc/images/after-party.jpg"));
    }

    @Test
    @DisplayName("지난 활동 내용이 없으면 404를 반환한다")
    void getActivitiesContentReturnsNotFoundWhenActivitiesContentDoesNotExist() throws Exception {
        mockMvc.perform(get(ACTIVITIES_CONTENT_API))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ACTIVITIES_CONTENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("지난 활동 내용을 찾을 수 없습니다."));
    }
}
