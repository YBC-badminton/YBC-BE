package com.ybc.ybc.vote.controller;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.VoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
class VoteControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private VoteAttendanceRepository voteAttendanceRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("진행 중인 투표 목록과 페이지 정보를 함께 조회한다")
    void getJoinableVotes() throws Exception {
        Vote joinableVote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        Vote closedVote = voteRepository.save(vote(
                "지난 번개 운동",
                ActivityType.FLASH,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(1),
                12
        ));

        addAttendance(joinableVote, true, "1001");
        addAttendance(joinableVote, true, "1002");
        addAttendance(joinableVote, false, "1003");
        addAttendance(closedVote, true, "2001");

        mockMvc.perform(get("/votes")
                        .with(jwt())
                        .param("joinable", "true")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.votes.length()").value(1))
                .andExpect(jsonPath("$.votes[0].voteId").value(joinableVote.getId()))
                .andExpect(jsonPath("$.votes[0].name").value("목요 정기 운동"))
                .andExpect(jsonPath("$.votes[0].type").value("REGULAR"))
                .andExpect(jsonPath("$.votes[0].location").value("이화체육관"))
                .andExpect(jsonPath("$.votes[0].activityTime").value("19:00"))
                .andExpect(jsonPath("$.votes[0].capacity").value(20))
                .andExpect(jsonPath("$.votes[0].currentParticipantCount").value(2))
                .andExpect(jsonPath("$.isLast").value(true))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("마감된 투표 목록만 조회한다")
    void getClosedVotes() throws Exception {
        voteRepository.save(vote(
                "주말 이벤트 운동",
                ActivityType.EVENT,
                LocalDateTime.now().minusDays(3),
                LocalDateTime.now().minusDays(1),
                30
        ));
        voteRepository.save(vote(
                "오늘 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().plusHours(5),
                18
        ));

        mockMvc.perform(get("/votes")
                        .with(jwt())
                        .param("joinable", "false")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.votes.length()").value(1))
                .andExpect(jsonPath("$.votes[0].name").value("주말 이벤트 운동"))
                .andExpect(jsonPath("$.votes[0].type").value("EVENT"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 투표 목록을 조회할 수 없다")
    void getVotesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/votes")
                        .param("joinable", "true")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("현재 열려 있는 투표 수와 올해 생성된 투표 수를 함께 조회한다")
    void getVoteHistory() throws Exception {
        Vote joinableVote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().plusDays(1),
                20
        ));
        Vote anotherJoinableVote = voteRepository.save(vote(
                "번개 운동",
                ActivityType.FLASH,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(3),
                12
        ));
        Vote lastYearVote = voteRepository.save(vote(
                "작년 행사",
                ActivityType.EVENT,
                LocalDateTime.now().minusDays(3),
                LocalDateTime.now().minusDays(1),
                30
        ));

        updateCreatedAt(joinableVote.getId(), LocalDateTime.now().minusDays(10));
        updateCreatedAt(anotherJoinableVote.getId(), LocalDateTime.now().minusMonths(1));
        updateCreatedAt(lastYearVote.getId(), LocalDate.of(LocalDate.now().getYear() - 1, 12, 31).atTime(23, 59));

        mockMvc.perform(get("/votes/history").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinableVotes").value(2))
                .andExpect(jsonPath("$.thisYearVotes").value(2));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 투표 히스토리를 조회할 수 없다")
    void getVoteHistoryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/votes/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    private Vote vote(String title,
                      ActivityType activityType,
                      LocalDateTime voteStartAt,
                      LocalDateTime voteEndAt,
                      int capacity) {
        return Vote.builder()
                .title(title)
                .activityType(activityType)
                .activityDate(LocalDate.of(2026, 4, 10))
                .activityTime("19:00")
                .location("이화체육관")
                .memo("테스트 모임")
                .voteStartAt(voteStartAt)
                .voteEndAt(voteEndAt)
                .capacity(capacity)
                .build();
    }

    private void addAttendance(Vote vote,
                               boolean attendanceStatus,
                               String socialId) {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, socialId, "member-" + socialId, socialId + "@example.com")
        );
        voteAttendanceRepository.save(
                VoteAttendance.builder()
                        .vote(vote)
                        .member(member)
                        .attendanceStatus(attendanceStatus)
                        .build()
        );
    }

    private void updateCreatedAt(Long voteId, LocalDateTime createdAt) {
        jdbcTemplate.update("update vote set created_at = ? where vote_id = ?", createdAt, voteId);
    }
}
