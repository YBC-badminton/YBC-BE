package com.ybc.ybc.vote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.support.TestMailConfig;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.domain.MatchParticipantType;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.domain.VoteMatch;
import com.ybc.ybc.vote.domain.VoteMatchGroup;
import com.ybc.ybc.vote.dto.request.FlashVoteCreateRequest;
import com.ybc.ybc.vote.dto.request.FlashVoteUpdateRequest;
import com.ybc.ybc.vote.dto.request.VoteAttendanceRequest;
import com.ybc.ybc.vote.dto.request.VoteGuestRequest;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
@Import(TestMailConfig.class)
class VoteControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private VoteAttendanceRepository voteAttendanceRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private VoteMatchRepository voteMatchRepository;

    @Autowired
    private VoteMatchGroupRepository voteMatchGroupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사용자는 번개 모임 투표를 생성할 수 있다")
    void createFlashVote() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );
        LocalDate activityDate = LocalDate.now().plusDays(1);
        LocalDateTime voteEndAt = LocalDateTime.now().plusHours(5);

        mockMvc.perform(post("/votes/flash")
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FlashVoteCreateRequest(
                                "오늘 번개 운동",
                                activityDate,
                                "19:00 ~ 20:00",
                                "이화체육관",
                                "준비 운동 후 게임",
                                voteEndAt
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activityType").value("FLASH"))
                .andExpect(jsonPath("$.title").value("오늘 번개 운동"))
                .andExpect(jsonPath("$.openedByMemberId").value(member.getId()))
                .andExpect(jsonPath("$.openedByNickname").value("member-3001"))
                .andExpect(jsonPath("$.location").value("이화체육관"))
                .andExpect(jsonPath("$.voteStartAt").exists())
                .andExpect(jsonPath("$.voteEndAt").value(voteEndAt.withNano(0).toString()));

        Vote savedVote = voteRepository.findAll().stream()
                .filter(vote -> vote.getTitle().equals("오늘 번개 운동"))
                .findFirst()
                .orElseThrow();
        assertThat(savedVote.getActivityType()).isEqualTo(ActivityType.FLASH);
        assertThat(savedVote.getActivityDate()).isEqualTo(activityDate);
        assertThat(savedVote.getVoteEndAt().withNano(0)).isEqualTo(voteEndAt.withNano(0));
        assertThat(savedVote.getCapacity()).isZero();
    }

    @Test
    @DisplayName("번개 모임 투표는 투표 종료 시간이 현재보다 이전이어도 생성할 수 있다")
    void createFlashVoteAllowsPastVoteEndAt() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3002", "member-3002", "3002@example.com")
        );
        LocalDate activityDate = LocalDate.now().plusDays(1);
        LocalDateTime voteEndAt = LocalDateTime.now().minusHours(1);

        mockMvc.perform(post("/votes/flash")
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FlashVoteCreateRequest(
                                "마감된 번개 운동",
                                activityDate,
                                "21:00 ~ 22:00",
                                "이화체육관",
                                "마감 시간 테스트",
                                voteEndAt
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activityType").value("FLASH"))
                .andExpect(jsonPath("$.title").value("마감된 번개 운동"))
                .andExpect(jsonPath("$.voteEndAt").exists());

        Vote savedVote = voteRepository.findAll().stream()
                .filter(vote -> vote.getTitle().equals("마감된 번개 운동"))
                .findFirst()
                .orElseThrow();
        assertThat(savedVote.getVoteEndAt().withNano(0)).isEqualTo(voteEndAt.withNano(0));
    }

    @Test
    @DisplayName("번개 모임 개설자는 본인이 생성한 번개 모임을 수정할 수 있다")
    void updateFlashVote() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3003", "member-3003", "3003@example.com")
        );
        LocalDateTime voteStartAt = LocalDateTime.now().minusMinutes(30);
        LocalDateTime originalVoteEndAt = LocalDateTime.now().plusHours(2);
        Vote vote = voteRepository.save(flashVote(
                "기존 번개 운동",
                member.getId(),
                "member-3003",
                LocalDate.now().plusDays(1),
                voteStartAt,
                originalVoteEndAt
        ));
        LocalDateTime updatedVoteEndAt = LocalDateTime.now().plusHours(5);

        mockMvc.perform(patch("/votes/flash/{voteId}", vote.getId())
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FlashVoteUpdateRequest(
                                "수정된 번개 운동",
                                LocalDate.now().plusDays(2),
                                "20:00 ~ 21:00",
                                "신촌체육관",
                                "수정 메모",
                                updatedVoteEndAt
                        ))))
                .andExpect(status().isOk());

        Vote updatedVote = voteRepository.findById(vote.getId()).orElseThrow();
        assertThat(updatedVote.getTitle()).isEqualTo("수정된 번개 운동");
        assertThat(updatedVote.getActivityDate()).isEqualTo(LocalDate.now().plusDays(2));
        assertThat(updatedVote.getActivityTime()).isEqualTo("20:00 ~ 21:00");
        assertThat(updatedVote.getLocation()).isEqualTo("신촌체육관");
        assertThat(updatedVote.getMemo()).isEqualTo("수정 메모");
        assertThat(updatedVote.getVoteEndAt().withNano(0)).isEqualTo(updatedVoteEndAt.withNano(0));
        assertThat(updatedVote.getVoteStartAt().withNano(0)).isEqualTo(voteStartAt.withNano(0));
        assertThat(updatedVote.getCapacity()).isZero();
        assertThat(updatedVote.getOpenedByMemberId()).isEqualTo(member.getId());
    }

    @Test
    @DisplayName("번개 모임 개설자가 아니면 번개 모임을 수정할 수 없다")
    void updateFlashVoteThrowsWhenRequesterIsNotOpener() throws Exception {
        Member opener = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3004", "member-3004", "3004@example.com")
        );
        Member anotherMember = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3005", "member-3005", "3005@example.com")
        );
        Vote vote = voteRepository.save(flashVote(
                "기존 번개 운동",
                opener.getId(),
                "member-3004",
                LocalDate.now().plusDays(1),
                LocalDateTime.now().minusMinutes(30),
                LocalDateTime.now().plusHours(2)
        ));

        mockMvc.perform(patch("/votes/flash/{voteId}", vote.getId())
                        .with(authenticatedMember(anotherMember))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FlashVoteUpdateRequest(
                                "수정된 번개 운동",
                                LocalDate.now().plusDays(2),
                                "20:00 ~ 21:00",
                                "신촌체육관",
                                "수정 메모",
                                LocalDateTime.now().plusHours(5)
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FLASH_VOTE_UPDATE_FORBIDDEN"));
    }

    @Test
    @DisplayName("번개 모임 수정 API로 일반 투표를 수정할 수 없다")
    void updateFlashVoteThrowsWhenVoteIsNotFlash() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3006", "member-3006", "3006@example.com")
        );
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDate.now().plusDays(1),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(3),
                20
        ));

        mockMvc.perform(patch("/votes/flash/{voteId}", vote.getId())
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FlashVoteUpdateRequest(
                                "수정된 정기 운동",
                                LocalDate.now().plusDays(2),
                                "20:00 ~ 21:00",
                                "신촌체육관",
                                "수정 메모",
                                LocalDateTime.now().plusHours(5)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_VOTE_ACTIVITY_TYPE"));
    }

    @Test
    @DisplayName("존재하지 않는 번개 모임은 수정할 수 없다")
    void updateFlashVoteThrowsWhenVoteIsMissing() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3007", "member-3007", "3007@example.com")
        );

        mockMvc.perform(patch("/votes/flash/{voteId}", 999L)
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FlashVoteUpdateRequest(
                                "수정된 번개 운동",
                                LocalDate.now().plusDays(2),
                                "20:00 ~ 21:00",
                                "신촌체육관",
                                "수정 메모",
                                LocalDateTime.now().plusHours(5)
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VOTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("번개 모임 개설자는 본인이 생성한 번개 모임을 삭제할 수 있다")
    void deleteFlashVote() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3008", "member-3008", "3008@example.com")
        );
        Vote vote = voteRepository.save(flashVote(
                "삭제할 번개 운동",
                member.getId(),
                "member-3008",
                LocalDate.now().plusDays(1),
                LocalDateTime.now().minusMinutes(30),
                LocalDateTime.now().plusHours(2)
        ));
        VoteAttendance attendance = voteAttendanceRepository.save(VoteAttendance.builder()
                .vote(vote)
                .member(member)
                .attendanceStatus(true)
                .build());
        Guest guest = guestRepository.save(Guest.builder()
                .vote(vote)
                .member(member)
                .name("게스트")
                .gender(Gender.FEMALE)
                .level("A")
                .build());
        VoteMatchGroup matchGroup = voteMatchGroupRepository.save(VoteMatchGroup.create(vote));
        voteMatchRepository.save(VoteMatch.builder()
                .vote(vote)
                .matchGroup(matchGroup)
                .participantType(MatchParticipantType.MEMBER)
                .voteAttendance(attendance)
                .courtNumber(1)
                .matchNumber(1)
                .teamNumber(1)
                .build());
        voteMatchRepository.save(VoteMatch.builder()
                .vote(vote)
                .matchGroup(matchGroup)
                .participantType(MatchParticipantType.GUEST)
                .guest(guest)
                .courtNumber(1)
                .matchNumber(1)
                .teamNumber(2)
                .build());

        mockMvc.perform(delete("/votes/flash/{voteId}", vote.getId())
                        .with(authenticatedMember(member)))
                .andExpect(status().isNoContent());

        assertThat(voteRepository.findById(vote.getId())).isEmpty();
        assertThat(voteAttendanceRepository.findAll()).isEmpty();
        assertThat(guestRepository.findAll()).isEmpty();
        assertThat(voteMatchGroupRepository.findAll()).isEmpty();
        assertThat(voteMatchRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("번개 모임 개설자가 아니면 번개 모임을 삭제할 수 없다")
    void deleteFlashVoteThrowsWhenRequesterIsNotOpener() throws Exception {
        Member opener = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3009", "member-3009", "3009@example.com")
        );
        Member anotherMember = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3010", "member-3010", "3010@example.com")
        );
        Vote vote = voteRepository.save(flashVote(
                "삭제할 번개 운동",
                opener.getId(),
                "member-3009",
                LocalDate.now().plusDays(1),
                LocalDateTime.now().minusMinutes(30),
                LocalDateTime.now().plusHours(2)
        ));

        mockMvc.perform(delete("/votes/flash/{voteId}", vote.getId())
                        .with(authenticatedMember(anotherMember)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FLASH_VOTE_DELETE_FORBIDDEN"));
    }

    @Test
    @DisplayName("번개 모임 삭제 API로 일반 투표를 삭제할 수 없다")
    void deleteFlashVoteThrowsWhenVoteIsNotFlash() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3011", "member-3011", "3011@example.com")
        );
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDate.now().plusDays(1),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(3),
                20
        ));

        mockMvc.perform(delete("/votes/flash/{voteId}", vote.getId())
                        .with(authenticatedMember(member)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_VOTE_ACTIVITY_TYPE"));
    }

    @Test
    @DisplayName("존재하지 않는 번개 모임은 삭제할 수 없다")
    void deleteFlashVoteThrowsWhenVoteIsMissing() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3012", "member-3012", "3012@example.com")
        );

        mockMvc.perform(delete("/votes/flash/{voteId}", 999L)
                        .with(authenticatedMember(member)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VOTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("open=true 조회 시 activityDate 오름차순, 같은 날짜면 생성 시각 오름차순으로 조회한다")
    void getOpenVotes() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();

        Vote firstCreatedSameDateVote = vote(
                "같은 날짜 먼저 생성된 투표",
                ActivityType.REGULAR,
                today.plusDays(1),
                now.plusMinutes(20),
                now.plusHours(3),
                20
        );
        firstCreatedSameDateVote = voteRepository.save(firstCreatedSameDateVote);
        Vote laterCreatedSameDateVote = voteRepository.save(vote(
                "같은 날짜 나중에 생성된 투표",
                ActivityType.REGULAR,
                today.plusDays(1),
                now.minusHours(1),
                now.plusDays(1),
                18
        ));
        Vote laterActivityVote = voteRepository.save(vote(
                "더 늦은 활동 날짜 투표",
                ActivityType.REGULAR,
                today.plusDays(2),
                now.minusMinutes(30),
                now.plusHours(5),
                16
        ));
        Vote closedVote = voteRepository.save(vote(
                "지난 번개 운동",
                ActivityType.FLASH,
                now.minusDays(2),
                now.minusDays(1),
                12
        ));

        updateCreatedAt(firstCreatedSameDateVote.getId(), now.minusDays(3));
        updateCreatedAt(laterCreatedSameDateVote.getId(), now.minusDays(2));
        updateCreatedAt(laterActivityVote.getId(), now.minusDays(4));

        addAttendance(laterCreatedSameDateVote, true, "1001");
        addAttendance(laterCreatedSameDateVote, true, "1002");
        addGuest(laterCreatedSameDateVote, "host1", "게스트");
        addAttendance(laterCreatedSameDateVote, false, "1003");
        addAttendance(closedVote, true, "2001");
        jdbcTemplate.update("insert into vote_match_group (vote_id, created_at) values (?, current_timestamp)", laterCreatedSameDateVote.getId());

        mockMvc.perform(get("/votes")
                        .with(jwt())
                        .param("open", "true")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.votes.length()").value(3))
                .andExpect(jsonPath("$.votes[0].voteId").value(firstCreatedSameDateVote.getId()))
                .andExpect(jsonPath("$.votes[0].currentParticipantCount").value(0))
                .andExpect(jsonPath("$.votes[0].matchRegistered").value(false))
                .andExpect(jsonPath("$.votes[1].voteId").value(laterCreatedSameDateVote.getId()))
                .andExpect(jsonPath("$.votes[1].currentParticipantCount").value(2))
                .andExpect(jsonPath("$.votes[1].matchRegistered").value(true))
                .andExpect(jsonPath("$.votes[2].voteId").value(laterActivityVote.getId()))
                .andExpect(jsonPath("$.votes[2].currentParticipantCount").value(0))
                .andExpect(jsonPath("$.votes[2].matchRegistered").value(false));
    }

    @Test
    @DisplayName("비로그인 사용자는 오늘 기준 가장 가까운 활동 날짜와 타입 우선순위에 따라 진행 중인 투표 3개를 조회한다")
    void getRecentVotesHidesParticipantCountsForUnauthenticatedUser() throws Exception {
        LocalDate today = LocalDate.now();
        voteRepository.save(vote(
                "더 늦은 일정",
                ActivityType.REGULAR,
                today.plusDays(3),
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        voteRepository.save(vote(
                "지난 일정이지만 아직 열린 투표",
                ActivityType.EVENT,
                today.minusDays(1),
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().plusHours(3),
                18
        ));
        Vote regularVote = voteRepository.save(vote(
                "가장 가까운 정기 운동",
                ActivityType.REGULAR,
                today.plusDays(1),
                LocalDateTime.now().minusHours(3),
                LocalDateTime.now().plusDays(1),
                20
        ));
        Vote eventVote = voteRepository.save(vote(
                "가장 가까운 이벤트",
                ActivityType.EVENT,
                today.plusDays(1),
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().plusDays(2),
                18
        ));
        Vote flashVote = voteRepository.save(vote(
                "그 다음 번개",
                ActivityType.FLASH,
                today.plusDays(2),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(3),
                12
        ));
        addAttendance(regularVote, true, "recent-1001");
        addAttendance(eventVote, true, "recent-1002");
        addAttendance(flashVote, true, "recent-1003");

        mockMvc.perform(get("/votes/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.votes[0].name").value("가장 가까운 정기 운동"))
                .andExpect(jsonPath("$.votes[0].type").value("REGULAR"))
                .andExpect(jsonPath("$.votes[0].participantCountVisible").value(false))
                .andExpect(jsonPath("$.votes[0].currentParticipantCount").doesNotExist())
                .andExpect(jsonPath("$.votes[1].name").value("가장 가까운 이벤트"))
                .andExpect(jsonPath("$.votes[1].type").value("EVENT"))
                .andExpect(jsonPath("$.votes[2].name").value("그 다음 번개"))
                .andExpect(jsonPath("$.votes[2].type").value("FLASH"));
    }

    @Test
    @DisplayName("로그인 사용자는 진행 중인 최근 투표만 참여 인원 수와 함께 볼 수 있다")
    void getRecentVotesShowsParticipantCountsForAuthenticatedUser() throws Exception {
        voteRepository.save(vote(
                "아직 시작 전 투표",
                ActivityType.EVENT,
                LocalDate.of(2026, 4, 20),
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusDays(1),
                10
        ));
        Vote vote = voteRepository.save(vote(
                "진행 중 투표",
                ActivityType.REGULAR,
                LocalDate.of(2026, 4, 13),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        addAttendance(vote, true, "recent-auth-1001");
        addAttendance(vote, true, "recent-auth-1002");

        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );

        mockMvc.perform(get("/votes/recent").with(authenticatedMember(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.votes[0].name").value("진행 중 투표"))
                .andExpect(jsonPath("$.votes[0].participantCountVisible").value(true))
                .andExpect(jsonPath("$.votes[0].currentParticipantCount").value(2));
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
        addGuest(vote, "host1", "게스트");
        addAttendance(vote, false, "1003");

        mockMvc.perform(get("/votes/{voteId}", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteId").value(vote.getId()))
                .andExpect(jsonPath("$.openedByMemberId").doesNotExist())
                .andExpect(jsonPath("$.openedByNickname").doesNotExist())
                .andExpect(jsonPath("$.currentParticipantCount").value(2));
    }

    @Test
    @DisplayName("번개 모임 상세 조회 시 개설자 정보를 함께 조회한다")
    void getFlashVoteIncludesOpener() throws Exception {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );
        LocalDate activityDate = LocalDate.now().plusDays(1);
        LocalDateTime voteEndAt = LocalDateTime.now().plusHours(5);

        mockMvc.perform(post("/votes/flash")
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FlashVoteCreateRequest(
                                "오늘 번개 운동",
                                activityDate,
                                "19:00 ~ 20:00",
                                "이화체육관",
                                "준비 운동 후 게임",
                                voteEndAt
                        ))))
                .andExpect(status().isCreated());

        Vote savedVote = voteRepository.findAll().stream()
                .filter(vote -> vote.getTitle().equals("오늘 번개 운동"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/votes/{voteId}", savedVote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteId").value(savedVote.getId()))
                .andExpect(jsonPath("$.openedByMemberId").value(member.getId()))
                .andExpect(jsonPath("$.openedByNickname").value("member-3001"));
    }

    @Test
    @DisplayName("특정 투표의 게스트 목록을 조회한다")
    void getVoteGuests() throws Exception {
        Vote vote = vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        );
        ReflectionTestUtils.setField(vote, "activityDate", LocalDate.of(2026, 4, 7));
        vote = voteRepository.save(vote);
        addGuest(vote, "host1", "게스트1");
        addGuest(vote, "host2", "게스트2");

        mockMvc.perform(get("/votes/{voteId}/guests", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGuestCount").value(2))
                .andExpect(jsonPath("$.confirmedGuestCount").value(2))
                .andExpect(jsonPath("$.waitingGuestCount").value(0))
                .andExpect(jsonPath("$.guestQuota").value(3))
                .andExpect(jsonPath("$.guests.length()").value(2))
                .andExpect(jsonPath("$.guests[0].voteId").value(vote.getId()))
                .andExpect(jsonPath("$.guests[0].inviterId").isNumber())
                .andExpect(jsonPath("$.guests[0].inviterName").value("member-host1"))
                .andExpect(jsonPath("$.guests[0].guestName").value("게스트1"))
                .andExpect(jsonPath("$.guests[0].gender").value("FEMALE"))
                .andExpect(jsonPath("$.guests[0].level").value("A"))
                .andExpect(jsonPath("$.guests[0].isWaiting").value(false))
                .andExpect(jsonPath("$.guests[1].inviterId").isNumber())
                .andExpect(jsonPath("$.guests[1].inviterName").value("member-host2"))
                .andExpect(jsonPath("$.guests[1].guestName").value("게스트2"))
                .andExpect(jsonPath("$.guests[1].isWaiting").value(false));
    }

    @Test
    @DisplayName("추가참여 투표 게스트 조회 시 기존 투표 게스트는 제외한다")
    void getVoteGuestsExcludesBaseVoteGuestsForAdditionalVote() throws Exception {
        Vote baseVote = vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusHours(1),
                20
        );
        ReflectionTestUtils.setField(baseVote, "activityDate", LocalDate.of(2026, 4, 7));
        baseVote = voteRepository.save(baseVote);
        Vote additionalVote = voteRepository.saveAndFlush(
                Vote.createAdditionalVote(
                        baseVote,
                        LocalDateTime.now().minusMinutes(30),
                        LocalDateTime.now().plusHours(1),
                        2
                )
        );

        addGuest(baseVote, "base-host", "기존 게스트");
        addGuest(additionalVote, "additional-host", "추가 게스트");

        mockMvc.perform(get("/votes/{voteId}/guests", additionalVote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGuestCount").value(1))
                .andExpect(jsonPath("$.confirmedGuestCount").value(1))
                .andExpect(jsonPath("$.waitingGuestCount").value(0))
                .andExpect(jsonPath("$.guests.length()").value(1))
                .andExpect(jsonPath("$.guests[0].voteId").value(additionalVote.getId()))
                .andExpect(jsonPath("$.guests[0].inviterName").value("member-additional-host"))
                .andExpect(jsonPath("$.guests[0].guestName").value("추가 게스트"));
    }

    @Test
    @DisplayName("게스트 신청은 모두 저장되지만 정원 초과분은 대기 상태로 조회한다")
    void getVoteGuestsMarksOverflowGuestsAsWaiting() throws Exception {
        Vote vote = vote(
                "화요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        );
        ReflectionTestUtils.setField(vote, "activityDate", LocalDate.of(2026, 4, 7));
        vote = voteRepository.save(vote);
        addGuest(vote, "guest-waiting-host1", "게스트1");
        addGuest(vote, "guest-waiting-host2", "게스트2");
        addGuest(vote, "guest-waiting-host3", "게스트3");
        addGuest(vote, "guest-waiting-host4", "게스트4");

        mockMvc.perform(get("/votes/{voteId}/guests", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGuestCount").value(4))
                .andExpect(jsonPath("$.confirmedGuestCount").value(3))
                .andExpect(jsonPath("$.waitingGuestCount").value(1))
                .andExpect(jsonPath("$.guestQuota").value(3))
                .andExpect(jsonPath("$.guests[0].guestName").value("게스트1"))
                .andExpect(jsonPath("$.guests[0].isWaiting").value(false))
                .andExpect(jsonPath("$.guests[1].guestName").value("게스트2"))
                .andExpect(jsonPath("$.guests[1].isWaiting").value(false))
                .andExpect(jsonPath("$.guests[2].guestName").value("게스트3"))
                .andExpect(jsonPath("$.guests[2].isWaiting").value(false))
                .andExpect(jsonPath("$.guests[3].guestName").value("게스트4"))
                .andExpect(jsonPath("$.guests[3].isWaiting").value(true));
    }

    @Test
    @DisplayName("주말 정기 운동은 게스트 2명까지만 확정된다")
    void getVoteGuestsUsesWeekendQuotaForRegularVote() throws Exception {
        Vote vote = vote(
                "토요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        );
        ReflectionTestUtils.setField(vote, "activityDate", LocalDate.of(2026, 4, 11));
        vote = voteRepository.save(vote);
        addGuest(vote, "weekend-host1", "게스트1");
        addGuest(vote, "weekend-host2", "게스트2");
        addGuest(vote, "weekend-host3", "게스트3");

        mockMvc.perform(get("/votes/{voteId}/guests", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedGuestCount").value(2))
                .andExpect(jsonPath("$.waitingGuestCount").value(1))
                .andExpect(jsonPath("$.guestQuota").value(2))
                .andExpect(jsonPath("$.guests[0].isWaiting").value(false))
                .andExpect(jsonPath("$.guests[1].isWaiting").value(false))
                .andExpect(jsonPath("$.guests[2].isWaiting").value(true));
    }

    @Test
    @DisplayName("번개 운동 게스트는 모두 확정된다")
    void getVoteGuestsConfirmsAllFlashGuests() throws Exception {
        Vote vote = voteRepository.save(vote(
                "금요 번개 운동",
                ActivityType.FLASH,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                1
        ));
        addGuest(vote, "flash-host1", "게스트1");
        addGuest(vote, "flash-host2", "게스트2");
        addGuest(vote, "flash-host3", "게스트3");

        mockMvc.perform(get("/votes/{voteId}/guests", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedGuestCount").value(3))
                .andExpect(jsonPath("$.waitingGuestCount").value(0))
                .andExpect(jsonPath("$.guestQuota").value(3))
                .andExpect(jsonPath("$.guests[0].isWaiting").value(false))
                .andExpect(jsonPath("$.guests[1].isWaiting").value(false))
                .andExpect(jsonPath("$.guests[2].isWaiting").value(false));
    }

    @Test
    @DisplayName("이벤트 운동 게스트는 모두 대기 상태다")
    void getVoteGuestsMarksAllEventGuestsAsWaiting() throws Exception {
        Vote vote = voteRepository.save(vote(
                "행사 운동",
                ActivityType.EVENT,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        addGuest(vote, "event-host1", "게스트1");
        addGuest(vote, "event-host2", "게스트2");

        mockMvc.perform(get("/votes/{voteId}/guests", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedGuestCount").value(0))
                .andExpect(jsonPath("$.waitingGuestCount").value(2))
                .andExpect(jsonPath("$.guestQuota").value(0))
                .andExpect(jsonPath("$.guests[0].isWaiting").value(true))
                .andExpect(jsonPath("$.guests[1].isWaiting").value(true));
    }

    @Test
    @DisplayName("인증된 사용자는 등록된 대진을 조회할 수 있다")
    void getVoteMatch() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        VoteAttendance first = saveAttendance(vote, true, "1001");
        VoteAttendance second = saveAttendance(vote, true, "1002");
        VoteAttendance third = saveAttendance(vote, true, "1003");
        VoteAttendance fourth = saveAttendance(vote, true, "1004");

        VoteMatchGroup matchGroup = voteMatchGroupRepository.saveAndFlush(VoteMatchGroup.create(vote));
        voteMatchRepository.saveAllAndFlush(java.util.List.of(
                memberMatch(vote, matchGroup, first, 1, 1, 1),
                memberMatch(vote, matchGroup, second, 1, 1, 1),
                memberMatch(vote, matchGroup, third, 1, 1, 2),
                memberMatch(vote, matchGroup, fourth, 1, 1, 2)
        ));

        mockMvc.perform(get("/votes/{voteId}/matches", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteId").value(vote.getId()))
                .andExpect(jsonPath("$.matchId").value(matchGroup.getId()))
                .andExpect(jsonPath("$.courtCount").value(1))
                .andExpect(jsonPath("$.matches[0].courtNumber").value(1))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].matchNumber").value(1))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[0].name").value("member-1001"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[1].name").value("member-1002"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team2[0].name").value("member-1003"))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team2[1].name").value("member-1004"));
    }

    @Test
    @DisplayName("추가참여 투표로 대진 조회 시 원본 투표 대진을 반환한다")
    void getVoteMatchReturnsBaseMatchWhenAdditionalVoteRequested() throws Exception {
        Vote baseVote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().plusHours(1),
                20
        ));
        Vote additionalVote = voteRepository.saveAndFlush(
                Vote.createAdditionalVote(
                        baseVote,
                        LocalDateTime.now().plusHours(1),
                        LocalDateTime.now().plusHours(2),
                        5
                )
        );
        VoteAttendance first = saveAttendance(baseVote, true, "2001");
        VoteAttendance second = saveAttendance(baseVote, true, "2002");
        VoteAttendance third = saveAttendance(baseVote, true, "2003");
        VoteAttendance fourth = saveAttendance(baseVote, true, "2004");

        VoteMatchGroup matchGroup = voteMatchGroupRepository.saveAndFlush(VoteMatchGroup.create(baseVote));
        voteMatchRepository.saveAllAndFlush(java.util.List.of(
                memberMatch(baseVote, matchGroup, first, 1, 1, 1),
                memberMatch(baseVote, matchGroup, second, 1, 1, 1),
                memberMatch(baseVote, matchGroup, third, 1, 1, 2),
                memberMatch(baseVote, matchGroup, fourth, 1, 1, 2)
        ));

        mockMvc.perform(get("/votes/{voteId}/matches", additionalVote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voteId").value(baseVote.getId()))
                .andExpect(jsonPath("$.matchId").value(matchGroup.getId()))
                .andExpect(jsonPath("$.matches[0].courtMatches[0].team1[0].name").value("member-2001"));
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
    @DisplayName("특정 투표에서 참석 상태가 true인 회원 목록을 순서대로 조회하고 초과 인원은 대기 처리한다")
    void getVoteAttendees() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                1
        ));
        addAttendance(vote, true, "3001", true);
        addAttendance(vote, true, "3002");
        addAttendance(vote, false, "3003");

        mockMvc.perform(get("/votes/{voteId}/attendees", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttendeeCount").value(2))
                .andExpect(jsonPath("$.attendees.length()").value(2))
                .andExpect(jsonPath("$.attendees[0].attendeeNumber").value(1))
                .andExpect(jsonPath("$.attendees[0].nickname").value("member-3001"))
                .andExpect(jsonPath("$.attendees[0].memberId").isNumber())
                .andExpect(jsonPath("$.attendees[0].votedAt").exists())
                .andExpect(jsonPath("$.attendees[0].isWaiting").value(false))
                .andExpect(jsonPath("$.attendees[0].isAdmin").value(true))
                .andExpect(jsonPath("$.attendees[1].attendeeNumber").value(2))
                .andExpect(jsonPath("$.attendees[1].nickname").value("member-3002"))
                .andExpect(jsonPath("$.attendees[1].memberId").isNumber())
                .andExpect(jsonPath("$.attendees[1].votedAt").exists())
                .andExpect(jsonPath("$.attendees[1].isWaiting").value(true))
                .andExpect(jsonPath("$.attendees[1].isAdmin").value(false));
    }

    @Test
    @DisplayName("특정 투표에서 참석 상태가 false인 회원 목록과 총원을 조회한다")
    void getVoteAbsentees() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        VoteAttendance laterUpdatedAbsentee = saveAttendance(vote, false, "3001", true);
        VoteAttendance earlierUpdatedAbsentee = saveAttendance(vote, false, "3002");
        addAttendance(vote, true, "3003");
        updateAttendanceVotedAt(laterUpdatedAbsentee, LocalDateTime.of(2026, 8, 11, 10, 5));
        updateAttendanceVotedAt(earlierUpdatedAbsentee, LocalDateTime.of(2026, 8, 11, 10, 0));

        mockMvc.perform(get("/votes/{voteId}/absentees", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAbsenteeCount").value(2))
                .andExpect(jsonPath("$.absentees.length()").value(2))
                .andExpect(jsonPath("$.absentees[0].nickname").value("member-3002"))
                .andExpect(jsonPath("$.absentees[0].memberId").isNumber())
                .andExpect(jsonPath("$.absentees[0].votedAt").exists())
                .andExpect(jsonPath("$.absentees[0].isAdmin").value(false))
                .andExpect(jsonPath("$.absentees[1].nickname").value("member-3001"))
                .andExpect(jsonPath("$.absentees[1].memberId").isNumber())
                .andExpect(jsonPath("$.absentees[1].votedAt").exists())
                .andExpect(jsonPath("$.absentees[1].isAdmin").value(true));
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
    @DisplayName("현재 로그인한 회원의 투표 참석 여부를 저장한다")
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
        assertThat(voteAttendance.isAttendanceStatus()).isTrue();
    }

    @Test
    @DisplayName("같은 운동의 원본 투표에 이미 참석했다면 추가 투표에는 다시 참석할 수 없다")
    void saveVoteAttendanceThrowsWhenMemberAlreadyJoinedRelatedVote() throws Exception {
        Vote baseVote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().plusHours(1),
                20
        ));
        Vote additionalVote = voteRepository.saveAndFlush(
                Vote.createAdditionalVote(
                        baseVote,
                        LocalDateTime.now().minusMinutes(30),
                        LocalDateTime.now().plusHours(2),
                        5
                )
        );
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );
        voteAttendanceRepository.save(
                VoteAttendance.builder()
                        .vote(baseVote)
                        .member(member)
                        .attendanceStatus(true)
                        .build()
        );

        mockMvc.perform(put("/votes/{voteId}/attendance", additionalVote.getId())
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteAttendanceRequest(true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DUPLICATE_VOTE_ATTENDANCE"));
    }

    @Test
    @DisplayName("게스트 참여 정보를 생성한다")
    void createGuest() throws Exception {
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

        mockMvc.perform(post("/votes/{voteId}/guests", vote.getId())
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteGuestRequest("게스트", Gender.FEMALE, "A"))))
                .andExpect(status().isCreated());

        Guest guest = guestRepository.findAll().get(0);
        assertThat(guest.getName()).isEqualTo("게스트");
        assertThat(guest.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(guest.getLevel()).isEqualTo("A");
    }

    @Test
    @DisplayName("게스트 신청은 정원이 가득 차도 저장된다")
    void createGuestAllowsWaitlistWhenSessionIsFull() throws Exception {
        Vote vote = vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                2
        );
        ReflectionTestUtils.setField(vote, "activityDate", LocalDate.of(2026, 4, 7));
        vote = voteRepository.save(vote);
        addAttendance(vote, true, "1001");
        addGuest(vote, "host1", "게스트");

        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );

        mockMvc.perform(post("/votes/{voteId}/guests", vote.getId())
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteGuestRequest("게스트", Gender.FEMALE, "A"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/votes/{voteId}/guests", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGuestCount").value(2))
                .andExpect(jsonPath("$.confirmedGuestCount").value(2))
                .andExpect(jsonPath("$.waitingGuestCount").value(0));
    }

    @Test
    @DisplayName("마감된 투표에는 게스트 참여를 저장할 수 없다")
    void createGuestThrowsWhenVoteIsClosed() throws Exception {
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

        mockMvc.perform(post("/votes/{voteId}/guests", vote.getId())
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteGuestRequest("게스트", Gender.FEMALE, "A"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VOTE_CLOSED"));
    }

    @Test
    @DisplayName("투표 시작 전에는 게스트 참여를 저장할 수 없다")
    void createGuestThrowsWhenVoteIsNotOpenYet() throws Exception {
        Vote vote = voteRepository.save(vote(
                "곧 열릴 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now().plusHours(2),
                20
        ));
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );

        mockMvc.perform(post("/votes/{voteId}/guests", vote.getId())
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteGuestRequest("게스트", Gender.FEMALE, "A"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VOTE_NOT_OPEN"));
    }

    @Test
    @DisplayName("게스트를 추가한 사람은 본인 게스트를 수정할 수 있다")
    void updateGuest() throws Exception {
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
        Guest guest = guestRepository.save(
                Guest.builder()
                        .vote(vote)
                        .member(member)
                        .name("게스트")
                        .gender(Gender.FEMALE)
                        .level("A")
                        .build()
        );

        mockMvc.perform(patch("/votes/{voteId}/guests/{guestId}", vote.getId(), guest.getId())
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteGuestRequest("수정 게스트", Gender.MALE, "B"))))
                .andExpect(status().isOk());

        Guest updatedGuest = guestRepository.findById(guest.getId()).orElseThrow();
        assertThat(updatedGuest.getName()).isEqualTo("수정 게스트");
        assertThat(updatedGuest.getGender()).isEqualTo(Gender.MALE);
        assertThat(updatedGuest.getLevel()).isEqualTo("B");
    }

    @Test
    @DisplayName("게스트를 추가한 사람만 해당 게스트를 수정할 수 있다")
    void updateGuestThrowsWhenRequesterIsNotInviter() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        Member inviter = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );
        Member anotherMember = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3002", "member-3002", "3002@example.com")
        );
        Guest guest = guestRepository.save(
                Guest.builder()
                        .vote(vote)
                        .member(inviter)
                        .name("게스트")
                        .gender(Gender.FEMALE)
                        .level("A")
                        .build()
        );

        mockMvc.perform(patch("/votes/{voteId}/guests/{guestId}", vote.getId(), guest.getId())
                        .with(authenticatedMember(anotherMember))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteGuestRequest("수정 게스트", Gender.MALE, "B"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("GUEST_UPDATE_FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 게스트를 수정하면 예외가 발생한다")
    void updateGuestThrowsWhenGuestIsMissing() throws Exception {
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

        mockMvc.perform(patch("/votes/{voteId}/guests/{guestId}", vote.getId(), 999L)
                        .with(authenticatedMember(member))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteGuestRequest("수정 게스트", Gender.MALE, "B"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("GUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("게스트를 추가한 사람은 본인 게스트를 삭제할 수 있다")
    void deleteGuest() throws Exception {
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
        Guest guest = guestRepository.save(
                Guest.builder()
                        .vote(vote)
                        .member(member)
                        .name("게스트")
                        .gender(Gender.FEMALE)
                        .level("A")
                        .build()
        );

        mockMvc.perform(delete("/votes/{voteId}/guests/{guestId}", vote.getId(), guest.getId())
                        .with(authenticatedMember(member)))
                .andExpect(status().isNoContent());

        assertThat(guestRepository.findById(guest.getId())).isEmpty();
    }

    @Test
    @DisplayName("게스트를 추가한 사람만 해당 게스트를 삭제할 수 있다")
    void deleteGuestThrowsWhenRequesterIsNotInviter() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));
        Member inviter = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3001", "member-3001", "3001@example.com")
        );
        Member anotherMember = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "3002", "member-3002", "3002@example.com")
        );
        Guest guest = guestRepository.save(
                Guest.builder()
                        .vote(vote)
                        .member(inviter)
                        .name("게스트")
                        .gender(Gender.FEMALE)
                        .level("A")
                        .build()
        );

        mockMvc.perform(delete("/votes/{voteId}/guests/{guestId}", vote.getId(), guest.getId())
                        .with(authenticatedMember(anotherMember)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("GUEST_DELETE_FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 게스트를 삭제하면 예외가 발생한다")
    void deleteGuestThrowsWhenGuestIsMissing() throws Exception {
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

        mockMvc.perform(delete("/votes/{voteId}/guests/{guestId}", vote.getId(), 999L)
                        .with(authenticatedMember(member)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("GUEST_NOT_FOUND"));
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
        assertThat(voteAttendance.isAttendanceStatus()).isFalse();
    }

    @Test
    @DisplayName("정원이 가득 차도 참석 투표는 저장되고 대기열에 들어간다")
    void saveVoteAttendanceAllowsWaitlistWhenSessionIsFull() throws Exception {
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
                .andExpect(status().isOk());

        VoteAttendance voteAttendance = voteAttendanceRepository.findByVote_IdAndMember_Id(vote.getId(), member.getId()).orElseThrow();
        assertThat(voteAttendance.isAttendanceStatus()).isTrue();
    }

    @Test
    @DisplayName("앞선 참석자가 빠지면 대기자는 자동으로 참석 확정 상태가 된다")
    void getVoteAttendeesRemovesWaitingWhenEarlierAttendeeCancels() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                1
        ));
        Member firstMember = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "4001", "member-4001", "4001@example.com")
        );
        Member secondMember = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, "4002", "member-4002", "4002@example.com")
        );
        voteAttendanceRepository.save(
                VoteAttendance.builder()
                        .vote(vote)
                        .member(firstMember)
                        .attendanceStatus(true)
                        .build()
        );
        voteAttendanceRepository.save(
                VoteAttendance.builder()
                        .vote(vote)
                        .member(secondMember)
                        .attendanceStatus(true)
                        .build()
        );

        mockMvc.perform(get("/votes/{voteId}/attendees", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendees[0].attendeeNumber").value(1))
                .andExpect(jsonPath("$.attendees[0].nickname").value("member-4001"))
                .andExpect(jsonPath("$.attendees[0].isWaiting").value(false))
                .andExpect(jsonPath("$.attendees[1].attendeeNumber").value(2))
                .andExpect(jsonPath("$.attendees[1].nickname").value("member-4002"))
                .andExpect(jsonPath("$.attendees[1].isWaiting").value(true));

        mockMvc.perform(put("/votes/{voteId}/attendance", vote.getId())
                        .with(authenticatedMember(firstMember))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteAttendanceRequest(false))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/votes/{voteId}/attendees", vote.getId()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttendeeCount").value(1))
                .andExpect(jsonPath("$.attendees.length()").value(1))
                .andExpect(jsonPath("$.attendees[0].attendeeNumber").value(1))
                .andExpect(jsonPath("$.attendees[0].nickname").value("member-4002"))
                .andExpect(jsonPath("$.attendees[0].isWaiting").value(false));
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
    @DisplayName("투표 시작 전에는 참석 여부를 저장할 수 없다")
    void saveVoteAttendanceThrowsWhenVoteIsNotOpenYet() throws Exception {
        Vote vote = voteRepository.save(vote(
                "곧 열릴 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now().plusHours(2),
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
                .andExpect(jsonPath("$.error").value("VOTE_NOT_OPEN"));
    }

    @Test
    @DisplayName("존재하지 않는 투표를 상세 조회하면 예외가 발생한다")
    void getVoteThrowsWhenVoteIsMissing() throws Exception {
        mockMvc.perform(get("/votes/{voteId}", 999L).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VOTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("open=false 조회 시 지난 모임을 최근 활동순으로 조회한다")
    void getClosedVotes() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Vote oldestActivityVote = voteRepository.save(vote(
                "더 이전 정기 운동",
                ActivityType.REGULAR,
                now.toLocalDate().minusDays(10),
                now.minusDays(11),
                now.minusDays(9),
                20
        ));
        Vote firstCreatedRecentVote = voteRepository.save(vote(
                "최근 이벤트 운동",
                ActivityType.EVENT,
                now.toLocalDate().minusDays(3),
                now.minusDays(4),
                now.minusDays(2),
                30
        ));
        Vote laterCreatedRecentVote = voteRepository.save(vote(
                "가장 최근 정기 운동",
                ActivityType.REGULAR,
                now.toLocalDate().minusDays(3),
                now.minusDays(4),
                now.minusDays(2),
                18
        ));
        voteRepository.save(vote(
                "오늘 정기 운동",
                ActivityType.REGULAR,
                now.minusHours(2),
                now.plusHours(5),
                18
        ));
        voteRepository.save(vote(
                "곧 열릴 정기 운동",
                ActivityType.REGULAR,
                now.plusMinutes(20),
                now.plusHours(5),
                18
        ));
        updateCreatedAt(oldestActivityVote.getId(), now.minusDays(8));
        updateCreatedAt(firstCreatedRecentVote.getId(), now.minusDays(2));
        updateCreatedAt(laterCreatedRecentVote.getId(), now.minusDays(1));

        mockMvc.perform(get("/votes")
                        .with(jwt())
                        .param("open", "false")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.votes.length()").value(3))
                .andExpect(jsonPath("$.votes[0].voteId").value(laterCreatedRecentVote.getId()))
                .andExpect(jsonPath("$.votes[0].name").value("가장 최근 정기 운동"))
                .andExpect(jsonPath("$.votes[1].voteId").value(firstCreatedRecentVote.getId()))
                .andExpect(jsonPath("$.votes[1].name").value("최근 이벤트 운동"))
                .andExpect(jsonPath("$.votes[2].voteId").value(oldestActivityVote.getId()))
                .andExpect(jsonPath("$.votes[2].name").value("더 이전 정기 운동"))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 번개 모임 투표를 생성할 수 없다")
    void createFlashVoteRequiresAuthentication() throws Exception {
        LocalDate activityDate = LocalDate.now().plusDays(1);
        LocalDateTime voteEndAt = LocalDateTime.now().plusHours(5);

        mockMvc.perform(post("/votes/flash")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FlashVoteCreateRequest(
                                "오늘 번개 운동",
                                activityDate,
                                "19:00 ~ 20:00",
                                "이화체육관",
                                "준비 운동 후 게임",
                                voteEndAt
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 번개 모임을 수정할 수 없다")
    void updateFlashVoteRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/votes/flash/{voteId}", 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FlashVoteUpdateRequest(
                                "수정된 번개 운동",
                                LocalDate.now().plusDays(2),
                                "20:00 ~ 21:00",
                                "신촌체육관",
                                "수정 메모",
                                LocalDateTime.now().plusHours(5)
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 번개 모임을 삭제할 수 없다")
    void deleteFlashVoteRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/votes/flash/{voteId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 투표 목록을 조회할 수 없다")
    void getVotesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/votes")
                        .param("open", "true")
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
    @DisplayName("인증되지 않은 사용자는 게스트 목록을 조회할 수 없다")
    void getVoteGuestsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/votes/{voteId}/guests", 1L))
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
    @DisplayName("인증되지 않은 사용자는 참석 회원 목록을 조회할 수 없다")
    void getVoteAttendeesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/votes/{voteId}/attendees", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 불참 회원 목록을 조회할 수 없다")
    void getVoteAbsenteesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/votes/{voteId}/absentees", 1L))
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
    @DisplayName("인증되지 않은 사용자는 게스트 참여를 저장할 수 없다")
    void createGuestRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/votes/{voteId}/guests", 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteGuestRequest("게스트", Gender.FEMALE, "A"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 게스트를 수정할 수 없다")
    void updateGuestRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/votes/{voteId}/guests/{guestId}", 1L, 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new VoteGuestRequest("게스트", Gender.FEMALE, "A"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 게스트를 삭제할 수 없다")
    void deleteGuestRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/votes/{voteId}/guests/{guestId}", 1L, 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("현재 진행 중인 투표 수와 마감된 일반 투표 수를 함께 조회한다")
    void getVoteHistory() throws Exception {
        voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().plusDays(1),
                20
        ));
        voteRepository.save(vote(
                "번개 운동",
                ActivityType.FLASH,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(3),
                12
        ));
        voteRepository.save(vote(
                "아직 시작 전 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(4),
                18
        ));
        Vote completedRegularVote = voteRepository.save(vote(
                "지난 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(1),
                20
        ));
        voteRepository.save(vote(
                "지난 행사",
                ActivityType.EVENT,
                LocalDateTime.now().minusDays(3),
                LocalDateTime.now().minusDays(1),
                30
        ));
        voteRepository.saveAndFlush(
                Vote.createAdditionalVote(
                        completedRegularVote,
                        LocalDateTime.now().minusHours(10),
                        LocalDateTime.now().minusHours(1),
                        5
                )
        );

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

    @Test
    @DisplayName("인증되지 않은 사용자는 대진을 조회할 수 없다")
    void getVoteMatchRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/votes/{voteId}/matches", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("등록된 대진이 없으면 대진 조회 시 예외가 발생한다")
    void getVoteMatchThrowsWhenMatchIsMissing() throws Exception {
        Vote vote = voteRepository.save(vote(
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1),
                20
        ));

        mockMvc.perform(get("/votes/{voteId}/matches", vote.getId()).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("MATCH_NOT_FOUND"));
    }

    private Vote vote(String title,
                      ActivityType activityType,
                      LocalDate activityDate,
                      LocalDateTime voteStartAt,
                      LocalDateTime voteEndAt,
                      int capacity) {
        return Vote.builder()
                .title(title)
                .activityType(activityType)
                .activityDate(activityDate)
                .activityTime("19:00")
                .location("이화체육관")
                .memo("테스트 모임")
                .voteStartAt(voteStartAt)
                .voteEndAt(voteEndAt)
                .capacity(capacity)
                .build();
    }

    private Vote flashVote(String title,
                           Long openedByMemberId,
                           String openedByNickname,
                           LocalDate activityDate,
                           LocalDateTime voteStartAt,
                           LocalDateTime voteEndAt) {
        return Vote.builder()
                .title(title)
                .activityType(ActivityType.FLASH)
                .activityDate(activityDate)
                .activityTime("19:00 ~ 20:00")
                .location("이화체육관")
                .memo("기존 메모")
                .voteStartAt(voteStartAt)
                .voteEndAt(voteEndAt)
                .capacity(0)
                .openedByMemberId(openedByMemberId)
                .openedByNickname(openedByNickname)
                .build();
    }

    private Vote vote(String title,
                      ActivityType activityType,
                      LocalDateTime voteStartAt,
                      LocalDateTime voteEndAt,
                      int capacity) {
        return vote(title, activityType, LocalDate.of(2026, 4, 10), voteStartAt, voteEndAt, capacity);
    }

    private void addAttendance(Vote vote,
                               boolean attendanceStatus,
                               String socialId) {
        saveAttendance(vote, attendanceStatus, socialId, false);
    }

    private void addAttendance(Vote vote,
                               boolean attendanceStatus,
                               String socialId,
                               boolean isAdmin) {
        saveAttendance(vote, attendanceStatus, socialId, isAdmin);
    }

    private void addGuest(Vote vote, String socialId, String guestName) {
        saveGuest(vote, socialId, guestName);
    }

    private void updateCreatedAt(Long voteId, LocalDateTime createdAt) {
        jdbcTemplate.update("update vote set created_at = ? where vote_id = ?", createdAt, voteId);
    }

    private void updateAttendanceVotedAt(VoteAttendance attendance, LocalDateTime votedAt) {
        ReflectionTestUtils.setField(attendance, "votedAt", votedAt);
        voteAttendanceRepository.saveAndFlush(attendance);
    }

    private RequestPostProcessor authenticatedMember(Member member) {
        MemberPrincipal principal = MemberPrincipal.from(MemberSummary.from(member));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private VoteAttendance saveAttendance(Vote vote,
                                          boolean attendanceStatus,
                                          String socialId) {
        return saveAttendance(vote, attendanceStatus, socialId, false);
    }

    private VoteAttendance saveAttendance(Vote vote,
                                          boolean attendanceStatus,
                                          String socialId,
                                          boolean isAdmin) {
        Member member = memberRepository.save(
                Member.createSocialMember(
                        SocialProvider.KAKAO,
                        socialId,
                        "member-" + socialId,
                        socialId + "@example.com",
                        isAdmin
                )
        );
        return voteAttendanceRepository.saveAndFlush(
                VoteAttendance.builder()
                        .vote(vote)
                        .member(member)
                        .attendanceStatus(attendanceStatus)
                        .build()
        );
    }

    private Guest saveGuest(Vote vote, String socialId, String guestName) {
        Member member = memberRepository.save(
                Member.createSocialMember(SocialProvider.KAKAO, socialId, "member-" + socialId, socialId + "@example.com")
        );
        return guestRepository.saveAndFlush(
                Guest.builder()
                        .vote(vote)
                        .member(member)
                        .name(guestName)
                        .gender(Gender.FEMALE)
                        .level("A")
                        .build()
        );
    }

    private VoteMatch memberMatch(Vote vote,
                                  VoteMatchGroup matchGroup,
                                  VoteAttendance voteAttendance,
                                  int courtNumber,
                                  int matchNumber,
                                  int teamNumber) {
        return VoteMatch.builder()
                .vote(vote)
                .matchGroup(matchGroup)
                .participantType(MatchParticipantType.MEMBER)
                .voteAttendance(voteAttendance)
                .courtNumber(courtNumber)
                .matchNumber(matchNumber)
                .teamNumber(teamNumber)
                .build();
    }
}
