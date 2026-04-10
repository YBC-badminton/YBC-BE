package com.ybc.ybc.vote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.repository.GuestRepository;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminVoteControllerIntegrationTest {
    private static final String ADMIN_VOTES_API = "/admin/votes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private VoteAttendanceRepository voteAttendanceRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private MemberRepository memberRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @BeforeEach
    void setUp() {
        guestRepository.deleteAll();
        voteAttendanceRepository.deleteAll();
        voteRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사용자는 투표를 예약할 수 있다")
    void createVoteReturnsReservationResponseWhenAuthenticated() throws Exception {
        mockMvc.perform(post(ADMIN_VOTES_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityType", "FLUSH",
                                "title", "금요일 퇴근 후 번개 운동",
                                "activityDate", "2026-04-11",
                                "activityTime", "19:00 ~ 21:00",
                                "location", "망원체육관",
                                "memo", "가볍게 칠 분 모집",
                                "voteStartAt", "2026-04-10T18:00:00",
                                "voteEndAt", "2026-04-10T23:00:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteId").isNumber())
                .andExpect(jsonPath("$.activityType").value("FLUSH"))
                .andExpect(jsonPath("$.title").value("금요일 퇴근 후 번개 운동"))
                .andExpect(jsonPath("$.createdAt").exists());

        Vote savedVote = voteRepository.findAll().get(0);
        assertThat(savedVote.getCapacity()).isEqualTo(20);
        assertThat(savedVote.getActivityType()).isEqualTo(ActivityType.FLUSH);
    }

    @Test
    @DisplayName("인증된 사용자는 투표 예약 대기열을 조회할 수 있다")
    void getVoteQueueReturnsUpcomingVotesWhenAuthenticated() throws Exception {
        voteRepository.save(vote(
                "오늘 번개 운동",
                LocalDate.now(),
                ActivityType.FLUSH,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(4),
                20
        ));
        Vote upcomingVote = voteRepository.save(vote(
                "다음주 정기 운동",
                LocalDate.now().plusDays(1),
                ActivityType.REGULAR,
                LocalDateTime.now().plusHours(3),
                LocalDateTime.now().plusDays(1),
                20
        ));

        mockMvc.perform(get(ADMIN_VOTES_API + "/queue")
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].voteId").value(upcomingVote.getId()))
                .andExpect(jsonPath("$.data[0].activityType").value("REGULAR"))
                .andExpect(jsonPath("$.data[0].title").value("다음주 정기 운동"));
    }

    @Test
    @DisplayName("인증된 사용자는 투표 현황 리스트를 조회할 수 있다")
    void getVoteManagementReturnsSummaryAndAttendanceWhenAuthenticated() throws Exception {
        Vote upcomingVote = voteRepository.save(vote(
                "다음주 정기 운동",
                LocalDate.now().plusDays(1),
                ActivityType.REGULAR,
                LocalDateTime.now().plusHours(3),
                LocalDateTime.now().plusDays(1),
                20
        ));
        Vote inProgressVote = voteRepository.save(vote(
                "오늘 번개 운동",
                LocalDate.now(),
                ActivityType.FLUSH,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(4),
                20
        ));
        voteRepository.save(vote(
                "지난 이벤트 운동",
                LocalDate.now().minusDays(1),
                ActivityType.EVENT,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(1),
                30
        ));

        addAttendance(inProgressVote, true, "1001");
        addAttendance(inProgressVote, true, "1002");
        addGuest(inProgressVote, "host1", "게스트");

        mockMvc.perform(get(ADMIN_VOTES_API)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalCount").value(3))
                .andExpect(jsonPath("$.summary.upcomingCount").value(1))
                .andExpect(jsonPath("$.summary.inProgressCount").value(1))
                .andExpect(jsonPath("$.summary.completedCount").value(1))
                .andExpect(jsonPath("$.data[0].voteId").value(upcomingVote.getId()))
                .andExpect(jsonPath("$.data[0].status").value("UPCOMING"))
                .andExpect(jsonPath("$.data[1].voteId").value(inProgressVote.getId()))
                .andExpect(jsonPath("$.data[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data[1].attendance.currentAttendees").value(2))
                .andExpect(jsonPath("$.data[1].attendance.currentGuests").value(1))
                .andExpect(jsonPath("$.data[1].attendance.totalParticipants").value(3));
    }

    @Test
    @DisplayName("정규운동이 마감되면 현황 조회 시 추가참여 투표가 자동 생성된다")
    void getVoteManagementCreatesAdditionalVoteWhenThresholdIsReached() throws Exception {
        Vote endedVote = voteRepository.save(vote(
                "수요 정기 운동",
                nextWeekday(LocalDate.now().plusDays(1)),
                ActivityType.REGULAR,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusHours(1),
                20
        ));

        for (int index = 0; index < 16; index++) {
            addAttendance(endedVote, true, "member-" + index);
        }
        addGuest(endedVote, "host-extra", "게스트");

        mockMvc.perform(get(ADMIN_VOTES_API)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalCount").value(2))
                .andExpect(jsonPath("$.summary.inProgressCount").value(1))
                .andExpect(jsonPath("$.summary.completedCount").value(1));

        List<Vote> votes = voteRepository.findAll();
        assertThat(votes).hasSize(2);
        Vote additionalVote = votes.stream()
                .filter(Vote::isAdditionalParticipationVote)
                .findFirst()
                .orElseThrow();
        assertThat(additionalVote.getTitle()).isEqualTo("수요 정기 운동-추가참여");
        assertThat(additionalVote.getCapacity()).isEqualTo(5);
        assertThat(additionalVote.getMemo()).contains("추가참여비 4000원");
        assertThat(additionalVote.getVoteStartAt()).isEqualTo(endedVote.getVoteEndAt());
        assertThat(additionalVote.getVoteEndAt()).isEqualTo(additionalVote.getActivityDate().atTime(12, 0));
    }

    @Test
    @DisplayName("번개 운동은 인원이 많아도 추가참여 투표가 생성되지 않는다")
    void getVoteManagementDoesNotCreateAdditionalVoteForFlush() throws Exception {
        Vote endedVote = voteRepository.save(vote(
                "금요 번개 운동",
                nextWeekday(LocalDate.now().plusDays(1)),
                ActivityType.FLUSH,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusHours(1),
                20
        ));

        for (int index = 0; index < 20; index++) {
            addAttendance(endedVote, true, "flush-member-" + index);
        }

        mockMvc.perform(get(ADMIN_VOTES_API)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalCount").value(1))
                .andExpect(jsonPath("$.summary.completedCount").value(1));

        assertThat(voteRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("활동 유형이 잘못되면 400을 반환한다")
    void createVoteReturnsBadRequestWhenActivityTypeIsInvalid() throws Exception {
        mockMvc.perform(post(ADMIN_VOTES_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityType", "WRONG",
                                "title", "잘못된 투표",
                                "activityDate", "2026-04-11",
                                "activityTime", "19:00 ~ 21:00",
                                "location", "망원체육관",
                                "voteStartAt", "2026-04-10T18:00:00",
                                "voteEndAt", "2026-04-10T23:00:00"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_VOTE_ACTIVITY_TYPE"));
    }

    @Test
    @DisplayName("이전 번개 운동 타입 LIGHTNING 요청은 더 이상 허용하지 않는다")
    void createVoteReturnsBadRequestWhenLegacyLightningTypeIsRequested() throws Exception {
        mockMvc.perform(post(ADMIN_VOTES_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityType", "LIGHTNING",
                                "title", "예전 타입 요청",
                                "activityDate", "2026-04-11",
                                "activityTime", "19:00 ~ 21:00",
                                "location", "망원체육관",
                                "voteStartAt", "2026-04-10T18:00:00",
                                "voteEndAt", "2026-04-10T23:00:00"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_VOTE_ACTIVITY_TYPE"));
    }

    @Test
    @DisplayName("인증 정보가 없으면 투표 현황 조회는 401을 반환한다")
    void getVoteManagementReturnsUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(get(ADMIN_VOTES_API))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    private Vote vote(String title,
                      LocalDate activityDate,
                      ActivityType activityType,
                      LocalDateTime voteStartAt,
                      LocalDateTime voteEndAt,
                      int capacity) {
        return Vote.builder()
                .title(title)
                .activityType(activityType)
                .activityDate(activityDate)
                .activityTime("19:00 ~ 21:00")
                .location("망원체육관")
                .memo("테스트 메모")
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

    private void addGuest(Vote vote, String socialId, String guestName) {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, socialId, "member-" + socialId, socialId + "@example.com")
        );
        guestRepository.save(
                Guest.builder()
                        .vote(vote)
                        .member(member)
                        .name(guestName)
                        .gender(Gender.FEMALE)
                        .level("A")
                        .build()
        );
    }

    private JwtRequestPostProcessor authorizedUser() {
        return jwt().jwt(jwt -> jwt
                .subject("1")
                .claim("tokenType", "access")
                .claim("provider", "KAKAO")
                .claim("socialId", "123456789")
                .claim("email", "admin@example.com")
                .claim("nickname", "관리자"));
    }

    private LocalDate nextWeekday(LocalDate baseDate) {
        LocalDate candidate = baseDate;
        while (candidate.getDayOfWeek().getValue() >= 6) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }
}
