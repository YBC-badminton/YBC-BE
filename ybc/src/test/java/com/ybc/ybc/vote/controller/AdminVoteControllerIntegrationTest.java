package com.ybc.ybc.vote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.DiscoverySource;
import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.applications.domain.Status;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.repository.GuestRepository;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.VoteMatchGroupRepository;
import com.ybc.ybc.vote.repository.VoteMatchRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminVoteControllerIntegrationTest {
    private static final String ADMIN_VOTES_API = "/admin/votes";
    private static final String ADMIN_MATCHES_API = "/admin/matches";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private VoteAttendanceRepository voteAttendanceRepository;

    @Autowired
    private VoteMatchRepository voteMatchRepository;

    @Autowired
    private VoteMatchGroupRepository voteMatchGroupRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ClubMemberRepository clubMemberRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @BeforeEach
    void setUp() {
        voteMatchRepository.deleteAll();
        voteMatchGroupRepository.deleteAll();
        guestRepository.deleteAll();
        voteAttendanceRepository.deleteAll();
        voteRepository.deleteAll();
        clubMemberRepository.deleteAll();
        applicationRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사용자는 투표를 예약할 수 있다")
    void createVoteReturnsReservationResponseWhenAuthenticated() throws Exception {
        mockMvc.perform(post(ADMIN_VOTES_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityType", "FLASH",
                                "title", "금요일 퇴근 후 번개 운동",
                                "activityDate", "2026-04-11",
                                "activityTime", "19:00 ~ 21:00",
                                "location", "망원체육관",
                                "memo", "가볍게 칠 분 모집",
                                "voteStartAt", "2026-04-10T18:00:00",
                                "voteEndAt", "2026-04-10T23:00:00",
                                "capacity", 20
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voteId").isNumber())
                .andExpect(jsonPath("$.activityType").value("FLASH"))
                .andExpect(jsonPath("$.title").value("금요일 퇴근 후 번개 운동"))
                .andExpect(jsonPath("$.createdAt").exists());

        Vote savedVote = voteRepository.findAll().get(0);
        assertThat(savedVote.getCapacity()).isEqualTo(20);
        assertThat(savedVote.getActivityType()).isEqualTo(ActivityType.FLASH);
    }

    @Test
    @DisplayName("인증된 사용자는 투표 예약을 수정할 수 있다")
    void updateVoteReturnsUpdatedVoteWhenAuthenticated() throws Exception {
        Vote vote = voteRepository.save(vote(
                "3월 4주차 정기 배드민턴 투표",
                LocalDate.now(),
                ActivityType.FLASH,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().plusHours(2),
                20
        ));
        addAttendance(vote, true, "2001");
        addAttendance(vote, true, "2002");
        addGuest(vote, "host-update", "게스트");

        mockMvc.perform(patch(ADMIN_VOTES_API + "/{voteId}", vote.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityType", "REGULAR",
                                "title", "[장소변경] 3월 4주차 정기 배드민턴 투표",
                                "activityDate", LocalDate.now(),
                                "activityTime", "15:00 ~ 18:00",
                                "location", "마포구민체육센터",
                                "memo", "체육관 대관 사정으로 장소와 시간이 변경되었습니다. 확인 부탁드립니다!",
                                "voteStartAt", LocalDateTime.now().minusHours(1),
                                "voteEndAt", LocalDateTime.now().plusHours(3),
                                "capacity", 20
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteId").value(vote.getId()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.activityType").value("REGULAR"))
                .andExpect(jsonPath("$.title").value("[장소변경] 3월 4주차 정기 배드민턴 투표"))
                .andExpect(jsonPath("$.activityTime").value("15:00 ~ 18:00"))
                .andExpect(jsonPath("$.location").value("마포구민체육센터"))
                .andExpect(jsonPath("$.attendance.currentAttendees").value(2))
                .andExpect(jsonPath("$.attendance.currentGuests").value(1))
                .andExpect(jsonPath("$.attendance.totalParticipants").value(3));

        Vote updatedVote = voteRepository.findById(vote.getId()).orElseThrow();
        assertThat(updatedVote.getActivityType()).isEqualTo(ActivityType.REGULAR);
        assertThat(updatedVote.getTitle()).isEqualTo("[장소변경] 3월 4주차 정기 배드민턴 투표");
        assertThat(updatedVote.getLocation()).isEqualTo("마포구민체육센터");
        assertThat(updatedVote.getCapacity()).isEqualTo(20);
    }

    @Test
    @DisplayName("인증된 사용자는 투표 예약 대기열을 조회할 수 있다")
    void getVoteQueueReturnsUpcomingVotesWhenAuthenticated() throws Exception {
        voteRepository.save(vote(
                "오늘 번개 운동",
                LocalDate.now(),
                ActivityType.FLASH,
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
                ActivityType.FLASH,
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
    @DisplayName("인증된 사용자는 투표 완료된 운동 리스트를 조회할 수 있다")
    void getCompletedVotesReturnsCompletedVotesWhenAuthenticated() throws Exception {
        Vote latestCompletedVote = voteRepository.save(vote(
                "3월 4주차 정기 배드민턴 투표",
                LocalDate.of(2026, 3, 28),
                ActivityType.REGULAR,
                LocalDateTime.of(2026, 3, 25, 9, 0),
                LocalDateTime.of(2026, 3, 27, 18, 0),
                20
        ));
        Vote secondCompletedVote = voteRepository.save(vote(
                "평일 야간 번개 운동",
                LocalDate.of(2026, 3, 25),
                ActivityType.FLASH,
                LocalDateTime.of(2026, 3, 23, 9, 0),
                LocalDateTime.of(2026, 3, 24, 18, 0),
                20
        ));
        voteRepository.save(vote(
                "다음주 정기 운동",
                LocalDate.now().plusDays(1),
                ActivityType.REGULAR,
                LocalDateTime.now().plusHours(3),
                LocalDateTime.now().plusDays(1),
                20
        ));

        addAttendance(latestCompletedVote, true, "completed-1001");
        addAttendance(latestCompletedVote, true, "completed-1002");
        addGuest(latestCompletedVote, "completed-host1", "게스트1");
        addGuest(latestCompletedVote, "completed-host2", "게스트2");
        addAttendance(secondCompletedVote, true, "completed-2001");

        mockMvc.perform(get(ADMIN_VOTES_API)
                        .param("status", "completed")
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].voteId").value(latestCompletedVote.getId()))
                .andExpect(jsonPath("$[0].title").value("3월 4주차 정기 배드민턴 투표"))
                .andExpect(jsonPath("$[0].activityDay").value("토요일"))
                .andExpect(jsonPath("$[0].activityDate").value("2026-03-28"))
                .andExpect(jsonPath("$[0].location").value("망원체육관"))
                .andExpect(jsonPath("$[0].attendance.currentAttendees").value(2))
                .andExpect(jsonPath("$[0].attendance.currentGuests").value(2))
                .andExpect(jsonPath("$[0].attendance.totalParticipants").value(4))
                .andExpect(jsonPath("$[1].voteId").value(secondCompletedVote.getId()))
                .andExpect(jsonPath("$[1].activityDay").value("수요일"))
                .andExpect(jsonPath("$[1].attendance.totalParticipants").value(1));
    }

    @Test
    @DisplayName("인증된 사용자는 대진 등록용 참석 인원을 이름과 성별로 조회할 수 있다")
    void getMatchParticipantsReturnsMemberAndGuestProfilesWhenAuthenticated() throws Exception {
        Vote vote = voteRepository.save(vote(
                "참석 인원 조회용 정기 운동",
                LocalDate.now().minusDays(1),
                ActivityType.REGULAR,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(1),
                20
        ));
        VoteAttendance attendance = addAttendance(vote, true, "participant-1001", "카카오닉네임");
        addAttendance(vote, false, "participant-1002", "불참회원");
        saveClubMember("김철수", "participant-1001@example.com", Gender.MALE);
        addGuest(vote, "participant-host1", "게스트1");

        mockMvc.perform(get(ADMIN_VOTES_API + "/{voteId}/matches", vote.getId())
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteId").value(vote.getId()))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.participants[0].participantId").value(attendance.getId()))
                .andExpect(jsonPath("$.participants[0].participantType").value("MEMBER"))
                .andExpect(jsonPath("$.participants[0].name").value("김철수"))
                .andExpect(jsonPath("$.participants[0].gender").value("MALE"))
                .andExpect(jsonPath("$.participants[1].participantType").value("GUEST"))
                .andExpect(jsonPath("$.participants[1].name").value("게스트1"))
                .andExpect(jsonPath("$.participants[1].gender").value("FEMALE"));
    }

    @Test
    @DisplayName("기존 자동 생성 부원에 이메일과 성별이 비어 있어도 참석 인원 조회 시 보정한다")
    void getMatchParticipantsReconcilesFinalPassMemberProfile() throws Exception {
        Vote vote = voteRepository.save(vote(
                "참석 인원 동기화용 정기 운동",
                LocalDate.now().minusDays(1),
                ActivityType.REGULAR,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(1),
                20
        ));
        Application application = saveApplication("김철수", "sync-1001@example.com", Gender.MALE);
        clubMemberRepository.saveAndFlush(
                ClubMember.createFromApplication(
                        application.getId(),
                        "김철수",
                        "한국대학교",
                        "010-1234-5678",
                        "03"
                )
        );
        VoteAttendance attendance = addAttendance(vote, true, "sync-1001", "카카오닉네임");

        mockMvc.perform(get(ADMIN_VOTES_API + "/{voteId}/matches", vote.getId())
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].participantId").value(attendance.getId()))
                .andExpect(jsonPath("$.participants[0].name").value("김철수"))
                .andExpect(jsonPath("$.participants[0].gender").value("MALE"));
    }

    @Test
    @DisplayName("인증된 사용자는 투표 참석자로 대진을 등록할 수 있다")
    void registerMatchesReturnsMatchResponseWhenAuthenticated() throws Exception {
        Vote vote = voteRepository.save(vote(
                "대진 등록용 정기 운동",
                LocalDate.now().minusDays(1),
                ActivityType.REGULAR,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(1),
                20
        ));
        VoteAttendance first = addAttendance(vote, true, "match-1001", "김철수");
        VoteAttendance second = addAttendance(vote, true, "match-1002", "이영희");
        VoteAttendance third = addAttendance(vote, true, "match-1003", "박민수");
        VoteAttendance fourth = addAttendance(vote, true, "match-1004", "최지우");
        saveClubMember("김철수", "match-1001@example.com", Gender.MALE);
        saveClubMember("이영희", "match-1002@example.com", Gender.FEMALE);
        saveClubMember("박민수", "match-1003@example.com", Gender.MALE);
        saveClubMember("최지우", "match-1004@example.com", Gender.FEMALE);
        List<Map<String, Object>> requestBody = List.of(Map.of(
                "courtNumber", 1,
                "courtMatches", List.of(Map.of(
                        "matchNumber", 1,
                        "team1", List.of(first.getId(), second.getId()),
                        "team2", List.of(third.getId(), fourth.getId())
                ))
        ));

        mockMvc.perform(post(ADMIN_VOTES_API + "/{voteId}/matches", vote.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteId").value(vote.getId()))
                .andExpect(jsonPath("$.matchId").isNumber())
                .andExpect(jsonPath("$.courtCount").value(1))
                .andExpect(jsonPath("$.matches[0].courtNumber").value(1))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].matchNumber").value(1))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[0].name").value("김철수"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[0].gender").value("MALE"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[1].name").value("이영희"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[1].gender").value("FEMALE"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team2[0].name").value("박민수"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team2[0].gender").value("MALE"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team2[1].name").value("최지우"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team2[1].gender").value("FEMALE"));

        assertThat(voteMatchRepository.findAll()).hasSize(4);
        assertThat(voteMatchGroupRepository.findAll()).hasSize(1);
        Long matchId = voteMatchGroupRepository.findAll().get(0).getId();

        mockMvc.perform(post(ADMIN_VOTES_API + "/{voteId}/matches", vote.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(matchId));

        assertThat(voteMatchRepository.findAll()).hasSize(4);
        assertThat(voteMatchGroupRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("투표 참석자가 아닌 식별자로 대진 등록 시 400을 반환한다")
    void registerMatchesReturnsBadRequestWhenParticipantDoesNotExist() throws Exception {
        Vote vote = voteRepository.save(vote(
                "대진 등록용 정기 운동",
                LocalDate.now().minusDays(1),
                ActivityType.REGULAR,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(1),
                20
        ));
        VoteAttendance first = addAttendance(vote, true, "invalid-match-1001", "김철수");
        VoteAttendance second = addAttendance(vote, true, "invalid-match-1002", "이영희");
        VoteAttendance third = addAttendance(vote, true, "invalid-match-1003", "박민수");

        mockMvc.perform(post(ADMIN_VOTES_API + "/{voteId}/matches", vote.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(Map.of(
                                "courtNumber", 1,
                                "courtMatches", List.of(Map.of(
                                        "matchNumber", 1,
                                        "team1", List.of(first.getId(), second.getId()),
                                        "team2", List.of(third.getId(), 99999L)
                                ))
                        )))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MATCH_PARTICIPANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증된 사용자는 matchId로 등록된 대진을 조회할 수 있다")
    void getMatchReturnsRegisteredMatchesWhenAuthenticated() throws Exception {
        Vote vote = voteRepository.save(vote(
                "대진 조회용 정기 운동",
                LocalDate.now().minusDays(1),
                ActivityType.REGULAR,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(1),
                20
        ));
        VoteAttendance first = addMatchParticipant(vote, "get-match-1001", "김민준", Gender.MALE);
        VoteAttendance second = addMatchParticipant(vote, "get-match-1002", "이서연", Gender.FEMALE);
        VoteAttendance third = addMatchParticipant(vote, "get-match-1003", "박지훈", Gender.MALE);
        VoteAttendance fourth = addMatchParticipant(vote, "get-match-1004", "최하은", Gender.FEMALE);
        List<Map<String, Object>> requestBody = List.of(Map.of(
                "courtNumber", 1,
                "courtMatches", List.of(Map.of(
                        "matchNumber", 1,
                        "team1", List.of(first.getId(), second.getId()),
                        "team2", List.of(third.getId(), fourth.getId())
                ))
        ));

        mockMvc.perform(post(ADMIN_VOTES_API + "/{voteId}/matches", vote.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());

        Long matchId = voteMatchGroupRepository.findAll().get(0).getId();

        mockMvc.perform(get(ADMIN_MATCHES_API + "/{matchId}", matchId)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteId").value(vote.getId()))
                .andExpect(jsonPath("$.matchId").value(matchId))
                .andExpect(jsonPath("$.courtCount").value(1))
                .andExpect(jsonPath("$.matches[0].courtNumber").value(1))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].matchNumber").value(1))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[0].name").value("김민준"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[0].gender").value("MALE"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[1].name").value("이서연"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[1].gender").value("FEMALE"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team2[0].name").value("박지훈"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team2[1].name").value("최하은"));
    }

    @Test
    @DisplayName("인증된 사용자는 matchId로 특정 경기 대진을 수정하고 전체 대진을 조회할 수 있다")
    void updateMatchReplacesRequestedGameAndKeepsOtherGames() throws Exception {
        Vote vote = voteRepository.save(vote(
                "대진 수정용 정기 운동",
                LocalDate.now().minusDays(1),
                ActivityType.REGULAR,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(1),
                20
        ));
        VoteAttendance first = addMatchParticipant(vote, "patch-match-1001", "김민준", Gender.MALE);
        VoteAttendance second = addMatchParticipant(vote, "patch-match-1002", "이서연", Gender.FEMALE);
        VoteAttendance third = addMatchParticipant(vote, "patch-match-1003", "박지훈", Gender.MALE);
        VoteAttendance fourth = addMatchParticipant(vote, "patch-match-1004", "최하은", Gender.FEMALE);
        VoteAttendance fifth = addMatchParticipant(vote, "patch-match-1005", "정도윤", Gender.MALE);
        VoteAttendance sixth = addMatchParticipant(vote, "patch-match-1006", "강지우", Gender.FEMALE);
        VoteAttendance seventh = addMatchParticipant(vote, "patch-match-1007", "조현우", Gender.MALE);
        VoteAttendance eighth = addMatchParticipant(vote, "patch-match-1008", "윤서아", Gender.FEMALE);
        List<Map<String, Object>> originalRequest = List.of(Map.of(
                "courtNumber", 1,
                "courtMatches", List.of(
                        Map.of(
                                "matchNumber", 1,
                                "team1", List.of(first.getId(), second.getId()),
                                "team2", List.of(third.getId(), fourth.getId())
                        ),
                        Map.of(
                                "matchNumber", 2,
                                "team1", List.of(fifth.getId(), sixth.getId()),
                                "team2", List.of(seventh.getId(), eighth.getId())
                        )
                )
        ));
        mockMvc.perform(post(ADMIN_VOTES_API + "/{voteId}/matches", vote.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(originalRequest)))
                .andExpect(status().isOk());

        Long matchId = voteMatchGroupRepository.findAll().get(0).getId();
        List<Map<String, Object>> updateRequest = List.of(Map.of(
                "courtNumber", 1,
                "courtMatches", List.of(Map.of(
                        "matchNumber", 1,
                        "team1", List.of(fifth.getId(), sixth.getId()),
                        "team2", List.of(seventh.getId(), eighth.getId())
                ))
        ));

        mockMvc.perform(patch(ADMIN_MATCHES_API + "/{matchId}", matchId)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteId").value(vote.getId()))
                .andExpect(jsonPath("$.matchId").value(matchId))
                .andExpect(jsonPath("$.courtCount").value(1))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].matchNumber").value(1))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[0].name").value("정도윤"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[1].name").value("강지우"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team2[0].name").value("조현우"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team2[1].name").value("윤서아"))
                .andExpect(jsonPath("$.matches[0].courtMatches[1].matchNumber").value(2))
                .andExpect(jsonPath("$.matches[0].courtMatches[1].team1[0].name").value("정도윤"))
                .andExpect(jsonPath("$.matches[0].courtMatches[1].team2[1].name").value("윤서아"));

        assertThat(voteMatchRepository.findAll()).hasSize(8);
    }

    @Test
    @DisplayName("존재하지 않는 matchId로 대진 조회 시 404를 반환한다")
    void getMatchReturnsNotFoundWhenMatchDoesNotExist() throws Exception {
        mockMvc.perform(get(ADMIN_MATCHES_API + "/{matchId}", 9999L)
                        .with(authorizedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MATCH_NOT_FOUND"));
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
    void getVoteManagementDoesNotCreateAdditionalVoteForFlash() throws Exception {
        Vote endedVote = voteRepository.save(vote(
                "금요 번개 운동",
                nextWeekday(LocalDate.now().plusDays(1)),
                ActivityType.FLASH,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusHours(1),
                20
        ));

        for (int index = 0; index < 20; index++) {
            addAttendance(endedVote, true, "flash-member-" + index);
        }

        mockMvc.perform(get(ADMIN_VOTES_API)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalCount").value(1))
                .andExpect(jsonPath("$.summary.completedCount").value(1));

        assertThat(voteRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("PUT으로 투표 수정 요청 시 405를 반환한다")
    void updateVoteReturnsMethodNotAllowedWhenUsingPut() throws Exception {
        Vote vote = voteRepository.save(vote(
                "수정 대상 투표",
                LocalDate.now(),
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(2),
                20
        ));

        mockMvc.perform(put(ADMIN_VOTES_API + "/{voteId}", vote.getId())
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityType", "REGULAR",
                                "title", "PUT 수정 요청",
                                "activityDate", LocalDate.now(),
                                "activityTime", "15:00 ~ 18:00",
                                "location", "마포구민체육센터",
                                "memo", "PUT은 허용하지 않음",
                                "voteStartAt", LocalDateTime.now().minusHours(1),
                                "voteEndAt", LocalDateTime.now().plusHours(3),
                                "capacity", 20
                        ))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("인증된 사용자는 투표 예약을 삭제할 수 있다")
    void deleteVoteRemovesVoteAndRelatedDataWhenAuthenticated() throws Exception {
        Vote vote = voteRepository.save(vote(
                "삭제할 투표",
                LocalDate.now(),
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1),
                20
        ));
        addAttendance(vote, true, "3001");
        addGuest(vote, "host-delete", "삭제용 게스트");

        mockMvc.perform(delete(ADMIN_VOTES_API + "/{voteId}", vote.getId())
                        .with(authorizedUser()))
                .andExpect(status().isOk());

        assertThat(voteRepository.findById(vote.getId())).isEmpty();
        assertThat(voteAttendanceRepository.count()).isZero();
        assertThat(guestRepository.count()).isZero();
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
                                "voteEndAt", "2026-04-10T23:00:00",
                                "capacity", 20
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_VOTE_ACTIVITY_TYPE"));
    }

    @Test
    @DisplayName("투표 정원이 없으면 400을 반환한다")
    void createVoteReturnsBadRequestWhenCapacityIsMissing() throws Exception {
        mockMvc.perform(post(ADMIN_VOTES_API)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityType", "FLASH",
                                "title", "정원 없는 투표",
                                "activityDate", "2026-04-11",
                                "activityTime", "19:00 ~ 21:00",
                                "location", "망원체육관",
                                "voteStartAt", "2026-04-10T18:00:00",
                                "voteEndAt", "2026-04-10T23:00:00"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ILLEGAL_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("투표 정원을 입력해 주세요."));
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
                                "voteEndAt", "2026-04-10T23:00:00",
                                "capacity", 20
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_VOTE_ACTIVITY_TYPE"));
    }

    @Test
    @DisplayName("존재하지 않는 투표 수정 시 404를 반환한다")
    void updateVoteReturnsNotFoundWhenVoteDoesNotExist() throws Exception {
        mockMvc.perform(patch(ADMIN_VOTES_API + "/{voteId}", 9999L)
                        .with(authorizedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityType", "REGULAR",
                                "title", "없는 투표 수정",
                                "activityDate", LocalDate.now(),
                                "activityTime", "15:00 ~ 18:00",
                                "location", "마포구민체육센터",
                                "memo", "존재하지 않는 투표입니다.",
                                "voteStartAt", LocalDateTime.now().minusHours(1),
                                "voteEndAt", LocalDateTime.now().plusHours(3),
                                "capacity", 20
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VOTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("존재하지 않는 투표 삭제 시 404를 반환한다")
    void deleteVoteReturnsNotFoundWhenVoteDoesNotExist() throws Exception {
        mockMvc.perform(delete(ADMIN_VOTES_API + "/{voteId}", 9999L)
                        .with(authorizedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VOTE_NOT_FOUND"));
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

    private VoteAttendance addAttendance(Vote vote,
                                         boolean attendanceStatus,
                                         String socialId) {
        return addAttendance(vote, attendanceStatus, socialId, "member-" + socialId);
    }

    private VoteAttendance addAttendance(Vote vote,
                                         boolean attendanceStatus,
                                         String socialId,
                                         String nickname) {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, socialId, nickname, socialId + "@example.com")
        );
        return voteAttendanceRepository.saveAndFlush(VoteAttendance.builder()
                .vote(vote)
                .member(member)
                .attendanceStatus(attendanceStatus)
                .build());
    }

    private VoteAttendance addMatchParticipant(Vote vote, String socialId, String name, Gender gender) {
        VoteAttendance attendance = addAttendance(vote, true, socialId, name);
        saveClubMember(name, socialId + "@example.com", gender);
        return attendance;
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

    private ClubMember saveClubMember(String name, String email, Gender gender) {
        return clubMemberRepository.saveAndFlush(
                ClubMember.create(name, "한국대학교", "010-0000-0000", "03", "2기", 1, email, gender)
        );
    }

    private Application saveApplication(String name, String email, Gender gender) {
        return applicationRepository.saveAndFlush(Application.builder()
                .name(name)
                .address("서울 강서구")
                .gender(gender)
                .age(24)
                .phone("010-1234-5678")
                .university("한국대학교")
                .major("컴퓨터공학과")
                .introduction("성실하게 활동할 자신 있습니다.")
                .motivation("운동을 좋아합니다.")
                .equipment("개인 라켓 보유")
                .discoverySource(DiscoverySource.INSTAGRAM)
                .discoveryEtc(null)
                .wantsStaff(false)
                .finalComment("열심히 하겠습니다.")
                .status(Status.FINAL_PASS)
                .email(email)
                .build());
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
