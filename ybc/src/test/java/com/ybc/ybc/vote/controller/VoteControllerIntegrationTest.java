package com.ybc.ybc.vote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.dto.request.VoteAttendanceRequest;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.VoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

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
    @DisplayName("투표 상세 정보를 조회한다")
    void getVote() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        addAttendance(vote, true, "1001");
        addAttendance(vote, true, "1002");
        addAttendance(vote, false, "1003");

        mockMvc.perform(get("/votes/{voteId}", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteId").value(vote.getId()))
                .andExpect(jsonPath("$.name").value("목요 정기 운동"))
                .andExpect(jsonPath("$.type").value("REGULAR"))
                .andExpect(jsonPath("$.activityDate").value("2026-04-10"))
                .andExpect(jsonPath("$.activityTime").value("19:00"))
                .andExpect(jsonPath("$.location").value("이화체육관"))
                .andExpect(jsonPath("$.memo").value("테스트 모임"))
                .andExpect(jsonPath("$.capacity").value(20))
                .andExpect(jsonPath("$.currentParticipantCount").value(2));
    }

    @Test
    @DisplayName("현재 로그인한 회원의 투표 참석 여부를 조회한다")
    void getVoteAttendance() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );
        voteAttendanceRepository.save(
                VoteAttendance.builder()
                        .vote(vote)
                        .member(member)
                        .attendanceStatus(true)
                        .build()
        );

        mockMvc.perform(get("/votes/{voteId}/attendance", vote.getId())
                        .with(authenticatedMember(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceStatus").value(true));
    }

    @Test
    @DisplayName("아직 응답하지 않은 회원의 투표 참석 여부는 null을 반환한다")
    void getVoteAttendanceReturnsNullWhenAttendanceIsMissing() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );

        mockMvc.perform(get("/votes/{voteId}/attendance", vote.getId())
                        .with(authenticatedMember(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceStatus").doesNotExist());
    }

    @Test
    @DisplayName("현재 로그인한 회원의 투표 참석 여부를 생성한다")
    void saveVoteAttendance() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );

        mockMvc.perform(put("/votes/{voteId}/attendance", vote.getId())
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteAttendanceRequest(true))))
                .andExpect(status().isOk());

        VoteAttendance voteAttendance = voteAttendanceRepository.findByVote_IdAndMember_Id(vote.getId(), member.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(voteAttendance.isAttendanceStatus()).isTrue();
    }

    @Test
    @DisplayName("현재 로그인한 회원의 기존 투표 참석 여부를 수정한다")
    void updateVoteAttendance() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );
        voteAttendanceRepository.save(
                VoteAttendance.builder()
                        .vote(vote)
                        .member(member)
                        .attendanceStatus(true)
                        .build()
        );

        mockMvc.perform(put("/votes/{voteId}/attendance", vote.getId())
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteAttendanceRequest(false))))
                .andExpect(status().isOk());

        VoteAttendance voteAttendance = voteAttendanceRepository.findByVote_IdAndMember_Id(vote.getId(), member.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(voteAttendance.isAttendanceStatus()).isFalse();
    }

    @Test
    @DisplayName("정원이 가득 찬 투표에는 추가 참석할 수 없다")
    void saveVoteAttendanceThrowsWhenSessionIsFull() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                2
        ));
        addAttendance(vote, true, "1001");
        addAttendance(vote, true, "1002");

        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );

        mockMvc.perform(put("/votes/{voteId}/attendance", vote.getId())
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteAttendanceRequest(true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SESSION_FULL"));
    }

    @Test
    @DisplayName("마감된 투표에는 참석 여부를 저장할 수 없다")
    void saveVoteAttendanceThrowsWhenVoteIsClosed() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusMinutes(1),
                20
        ));
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );

        mockMvc.perform(put("/votes/{voteId}/attendance", vote.getId())
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteAttendanceRequest(true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VOTE_CLOSED"));
    }

    @Test
    @DisplayName("존재하지 않는 투표 상세 조회 시 예외가 발생한다")
    void getVoteThrowsWhenVoteIsMissing() throws Exception {
        mockMvc.perform(get("/votes/{voteId}", 999L).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VOTE_NOT_FOUND"));
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
    @DisplayName("인증되지 않은 사용자는 투표 상세 정보를 조회할 수 없다")
    void getVoteRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/votes/{voteId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 투표 참석 여부를 조회할 수 없다")
    void getVoteAttendanceRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/votes/{voteId}/attendance", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 투표 참석 여부를 저장할 수 없다")
    void saveVoteAttendanceRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/votes/{voteId}/attendance", 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteAttendanceRequest(true))))
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

    private RequestPostProcessor authenticatedMember(Member member) {
        MemberPrincipal principal = MemberPrincipal.from(MemberSummary.from(member));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
