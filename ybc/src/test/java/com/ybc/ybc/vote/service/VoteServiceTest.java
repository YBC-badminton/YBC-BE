package com.ybc.ybc.vote.service;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.members.service.ClubMemberLookupService;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.dto.request.FlashVoteCreateRequest;
import com.ybc.ybc.vote.dto.request.FlashVoteUpdateRequest;
import com.ybc.ybc.vote.dto.request.VoteAttendanceRequest;
import com.ybc.ybc.vote.dto.request.VoteGuestRequest;
import com.ybc.ybc.vote.dto.response.FlashVoteCreateResponse;
import com.ybc.ybc.vote.dto.response.RecentVoteListResponse;
import com.ybc.ybc.vote.dto.response.VoteAbsenteeListResponse;
import com.ybc.ybc.vote.dto.response.VoteAttendanceStatusResponse;
import com.ybc.ybc.vote.dto.response.VoteAttendeeListResponse;
import com.ybc.ybc.vote.dto.response.VoteDetailResponse;
import com.ybc.ybc.vote.dto.response.VoteGuestListResponse;
import com.ybc.ybc.vote.dto.response.VoteHistoryResponse;
import com.ybc.ybc.vote.dto.response.VoteListResponse;
import com.ybc.ybc.vote.dto.response.VoteSummaryResponse;
import com.ybc.ybc.vote.repository.GuestRepository;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.VoteMatchGroupRepository;
import com.ybc.ybc.vote.repository.VoteMatchRepository;
import com.ybc.ybc.vote.repository.VoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-04-10T03:00:00Z");

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private VoteAttendanceRepository voteAttendanceRepository;

    @Mock
    private GuestRepository guestRepository;

    @Mock
    private VoteMatchRepository voteMatchRepository;

    @Mock
    private VoteMatchGroupRepository voteMatchGroupRepository;

    @Mock
    private MemberService memberService;

    @Mock
    private ClubMemberLookupService clubMemberLookupService;

    @Mock
    private Clock clock;

    @InjectMocks
    private VoteService voteService;

    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(FIXED_INSTANT);
        lenient().when(clock.getZone()).thenReturn(KOREA);
        lenient().when(clubMemberLookupService.getClubMembersByMemberId(any())).thenReturn(java.util.Map.of());
        lenient().when(clubMemberLookupService.resolveDisplayName(any(Member.class), anyMap()))
                .thenAnswer(invocation -> invocation.getArgument(0, Member.class).getNickname());
    }

    @Test
    @DisplayName("open=true 투표 목록 조회 시 activityDate를 포함해 반환한다")
    void getVotesReturnsActivityDate() {
        VoteSummaryResponse summary = new VoteSummaryResponse(
                1L,
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDate.of(2026, 4, 10),
                "이화체육관",
                LocalDateTime.of(2026, 4, 9, 18, 0),
                LocalDateTime.of(2026, 4, 9, 23, 0),
                "19:00",
                20,
                3L,
                true
        );
        LocalDateTime now = currentTime();
        when(voteRepository.findOpenVoteSummaries(eq(now), eq(now.plusMinutes(30)), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 10), 1));

        VoteListResponse response = voteService.getVotes(true, 0, 10);

        assertThat(response.votes()).hasSize(1);
        assertThat(response.votes().get(0).activityDate()).isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(response.votes().get(0).matchRegistered()).isTrue();
        verify(voteRepository).findOpenVoteSummaries(eq(now), eq(now.plusMinutes(30)), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    @DisplayName("open=false 투표 목록 조회 시 마감된 투표 목록 저장소를 조회한다")
    void getVotesReturnsClosedVotesWhenOpenIsFalse() {
        LocalDateTime now = currentTime();
        when(voteRepository.findClosedVoteSummaries(eq(now), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        VoteListResponse response = voteService.getVotes(false, 0, 10);

        assertThat(response.votes()).isEmpty();
        verify(voteRepository).findClosedVoteSummaries(eq(now), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    @DisplayName("번개 모임 생성 시 투표 시작 시간을 현재 시각으로 저장하고 정원 없음 기본값을 사용한다")
    void createFlashVoteUsesCurrentTimeAndUnlimitedCapacity() {
        FlashVoteCreateRequest request = new FlashVoteCreateRequest(
                "오늘 번개 운동",
                LocalDate.of(2026, 4, 10),
                "19:00 ~ 20:00",
                "이화체육관",
                "입장료 3000원, 최소 인원",
                LocalDateTime.of(2026, 4, 10, 23, 0)
        );
        MemberPrincipal memberPrincipal = new MemberPrincipal(
                1L,
                "KAKAO",
                "3001",
                "3001@example.com",
                "member-3001",
                false
        );
        when(voteRepository.saveAndFlush(any(Vote.class))).thenAnswer(invocation -> {
            Vote vote = invocation.getArgument(0);
            ReflectionTestUtils.setField(vote, "id", 1L);
            ReflectionTestUtils.setField(vote, "createdAt", currentTime());
            return vote;
        });

        FlashVoteCreateResponse response = voteService.createFlashVote(request, memberPrincipal);

        ArgumentCaptor<Vote> voteCaptor = ArgumentCaptor.forClass(Vote.class);
        verify(voteRepository).saveAndFlush(voteCaptor.capture());
        Vote savedVote = voteCaptor.getValue();
        assertThat(savedVote.getVoteStartAt()).isEqualTo(currentTime());
        assertThat(savedVote.getVoteEndAt()).isEqualTo(LocalDateTime.of(2026, 4, 10, 23, 0));
        assertThat(savedVote.getCapacity()).isZero();
        assertThat(savedVote.getOpenedByMemberId()).isEqualTo(1L);
        assertThat(savedVote.getOpenedByNickname()).isEqualTo("member-3001");
        assertThat(response.voteStartAt()).isEqualTo(currentTime());
        assertThat(response.openedByMemberId()).isEqualTo(1L);
        assertThat(response.openedByNickname()).isEqualTo("member-3001");
    }

    @Test
    @DisplayName("번개 모임 생성 시 투표 마감 시간이 현재보다 이전이어도 생성할 수 있다")
    void createFlashVoteAllowsPastVoteEndAt() {
        FlashVoteCreateRequest request = new FlashVoteCreateRequest(
                "이미 마감된 번개 운동",
                LocalDate.of(2026, 4, 10),
                "19:00 ~ 20:00",
                "이화체육관",
                "마감 시간만 별도로 관리",
                currentTime().minusMinutes(10)
        );
        MemberPrincipal memberPrincipal = new MemberPrincipal(
                1L,
                "KAKAO",
                "3001",
                "3001@example.com",
                "member-3001",
                false
        );
        when(voteRepository.saveAndFlush(any(Vote.class))).thenAnswer(invocation -> {
            Vote vote = invocation.getArgument(0);
            ReflectionTestUtils.setField(vote, "id", 2L);
            ReflectionTestUtils.setField(vote, "createdAt", currentTime());
            return vote;
        });

        FlashVoteCreateResponse response = voteService.createFlashVote(request, memberPrincipal);

        ArgumentCaptor<Vote> voteCaptor = ArgumentCaptor.forClass(Vote.class);
        verify(voteRepository).saveAndFlush(voteCaptor.capture());
        Vote savedVote = voteCaptor.getValue();
        assertThat(savedVote.getVoteStartAt()).isEqualTo(currentTime());
        assertThat(savedVote.getVoteEndAt()).isEqualTo(currentTime().minusMinutes(10));
        assertThat(response.voteEndAt()).isEqualTo(currentTime().minusMinutes(10));
    }

    @Test
    @DisplayName("번개 모임 개설자는 본인이 생성한 번개 모임을 수정할 수 있다")
    void updateFlashVoteUpdatesOwnFlashVote() {
        Vote vote = savedVote(1L, "기존 번개 운동");
        ReflectionTestUtils.setField(vote, "activityType", ActivityType.FLASH);
        ReflectionTestUtils.setField(vote, "openedByMemberId", 5L);
        ReflectionTestUtils.setField(vote, "openedByNickname", "member-5001");
        ReflectionTestUtils.setField(vote, "capacity", 0);
        LocalDateTime originalVoteStartAt = vote.getVoteStartAt();

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));

        voteService.updateFlashVote(1L, 5L, new FlashVoteUpdateRequest(
                "수정된 번개 운동",
                LocalDate.of(2026, 4, 12),
                "20:00 ~ 21:00",
                "신촌체육관",
                "수정 메모",
                LocalDateTime.of(2026, 4, 10, 22, 0)
        ));

        assertThat(vote.getTitle()).isEqualTo("수정된 번개 운동");
        assertThat(vote.getActivityDate()).isEqualTo(LocalDate.of(2026, 4, 12));
        assertThat(vote.getActivityTime()).isEqualTo("20:00 ~ 21:00");
        assertThat(vote.getLocation()).isEqualTo("신촌체육관");
        assertThat(vote.getMemo()).isEqualTo("수정 메모");
        assertThat(vote.getVoteEndAt()).isEqualTo(LocalDateTime.of(2026, 4, 10, 22, 0));
        assertThat(vote.getVoteStartAt()).isEqualTo(originalVoteStartAt);
        assertThat(vote.getCapacity()).isZero();
    }

    @Test
    @DisplayName("번개 모임 개설자가 아니면 번개 모임을 수정할 수 없다")
    void updateFlashVoteThrowsWhenRequesterIsNotOpener() {
        Vote vote = savedVote(1L, "기존 번개 운동");
        ReflectionTestUtils.setField(vote, "activityType", ActivityType.FLASH);
        ReflectionTestUtils.setField(vote, "openedByMemberId", 5L);

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));

        assertThatThrownBy(() -> voteService.updateFlashVote(1L, 7L, new FlashVoteUpdateRequest(
                "수정된 번개 운동",
                LocalDate.of(2026, 4, 12),
                "20:00 ~ 21:00",
                "신촌체육관",
                "수정 메모",
                LocalDateTime.of(2026, 4, 10, 22, 0)
        )))
                .isInstanceOfSatisfying(YbcException.class,
                        exception -> assertThat(exception.getExceptionCodeName()).isEqualTo("FLASH_VOTE_UPDATE_FORBIDDEN"));
    }

    @Test
    @DisplayName("번개 모임 수정 API로 일반 투표를 수정할 수 없다")
    void updateFlashVoteThrowsWhenVoteIsNotFlash() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "openedByMemberId", 5L);

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));

        assertThatThrownBy(() -> voteService.updateFlashVote(1L, 5L, new FlashVoteUpdateRequest(
                "수정된 정기 운동",
                LocalDate.of(2026, 4, 12),
                "20:00 ~ 21:00",
                "신촌체육관",
                "수정 메모",
                LocalDateTime.of(2026, 4, 10, 22, 0)
        )))
                .isInstanceOfSatisfying(YbcException.class,
                        exception -> assertThat(exception.getExceptionCodeName()).isEqualTo("INVALID_VOTE_ACTIVITY_TYPE"));
    }

    @Test
    @DisplayName("존재하지 않는 번개 모임은 수정할 수 없다")
    void updateFlashVoteThrowsWhenVoteIsMissing() {
        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voteService.updateFlashVote(1L, 5L, new FlashVoteUpdateRequest(
                "수정된 번개 운동",
                LocalDate.of(2026, 4, 12),
                "20:00 ~ 21:00",
                "신촌체육관",
                "수정 메모",
                LocalDateTime.of(2026, 4, 10, 22, 0)
        )))
                .isInstanceOfSatisfying(YbcException.class,
                        exception -> assertThat(exception.getExceptionCodeName()).isEqualTo("VOTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("번개 모임 개설자는 본인이 생성한 번개 모임을 삭제할 수 있다")
    void deleteFlashVoteDeletesOwnFlashVote() {
        Vote vote = savedVote(1L, "기존 번개 운동");
        ReflectionTestUtils.setField(vote, "activityType", ActivityType.FLASH);
        ReflectionTestUtils.setField(vote, "openedByMemberId", 5L);

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));

        voteService.deleteFlashVote(1L, 5L);

        verify(voteMatchRepository).deleteAllByVote_Id(1L);
        verify(voteMatchGroupRepository).deleteAllByVote_Id(1L);
        verify(guestRepository).deleteAllByVote_Id(1L);
        verify(voteAttendanceRepository).deleteAllByVote_Id(1L);
        verify(voteRepository).delete(vote);
    }

    @Test
    @DisplayName("번개 모임 개설자가 아니면 번개 모임을 삭제할 수 없다")
    void deleteFlashVoteThrowsWhenRequesterIsNotOpener() {
        Vote vote = savedVote(1L, "기존 번개 운동");
        ReflectionTestUtils.setField(vote, "activityType", ActivityType.FLASH);
        ReflectionTestUtils.setField(vote, "openedByMemberId", 5L);

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));

        assertThatThrownBy(() -> voteService.deleteFlashVote(1L, 7L))
                .isInstanceOfSatisfying(YbcException.class,
                        exception -> assertThat(exception.getExceptionCodeName()).isEqualTo("FLASH_VOTE_DELETE_FORBIDDEN"));
    }

    @Test
    @DisplayName("번개 모임 삭제 API로 일반 투표를 삭제할 수 없다")
    void deleteFlashVoteThrowsWhenVoteIsNotFlash() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "openedByMemberId", 5L);

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));

        assertThatThrownBy(() -> voteService.deleteFlashVote(1L, 5L))
                .isInstanceOfSatisfying(YbcException.class,
                        exception -> assertThat(exception.getExceptionCodeName()).isEqualTo("INVALID_VOTE_ACTIVITY_TYPE"));
    }

    @Test
    @DisplayName("존재하지 않는 번개 모임은 삭제할 수 없다")
    void deleteFlashVoteThrowsWhenVoteIsMissing() {
        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voteService.deleteFlashVote(1L, 5L))
                .isInstanceOfSatisfying(YbcException.class,
                        exception -> assertThat(exception.getExceptionCodeName()).isEqualTo("VOTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("로그인 사용자의 진행 중인 최근 투표 3개 조회 시 참여 인원 수를 함께 반환한다")
    void getRecentVotesReturnsParticipantCountsForAuthenticatedUser() {
        Vote firstVote = savedVote(1L, "가장 최근 투표", LocalDate.of(2026, 4, 12));
        Vote secondVote = savedVote(2L, "두 번째 최근 투표", LocalDate.of(2026, 4, 11));
        Vote thirdVote = savedVote(3L, "세 번째 최근 투표", LocalDate.of(2026, 4, 10));
        LocalDateTime now = currentTime();
        LocalDate today = now.toLocalDate();
        when(voteRepository.findRecentOngoingVotes(eq(now), eq(today), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(firstVote, secondVote, thirdVote));
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(3L);
        when(voteAttendanceRepository.countAttendancesByVoteId(2L)).thenReturn(2L);
        when(voteAttendanceRepository.countAttendancesByVoteId(3L)).thenReturn(1L);

        RecentVoteListResponse response = voteService.getRecentVotes(true);

        assertThat(response.count()).isEqualTo(3);
        assertThat(response.votes()).hasSize(3);
        assertThat(response.votes().get(0).name()).isEqualTo("가장 최근 투표");
        assertThat(response.votes().get(0).currentParticipantCount()).isEqualTo(3L);
        assertThat(response.votes().get(0).participantCountVisible()).isTrue();
        assertThat(response.votes().get(1).name()).isEqualTo("두 번째 최근 투표");
        assertThat(response.votes().get(2).currentParticipantCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("비로그인 사용자의 진행 중인 최근 투표 3개 조회 시 참여 인원 수를 숨긴다")
    void getRecentVotesHidesParticipantCountsForUnauthenticatedUser() {
        Vote firstVote = savedVote(1L, "가장 최근 투표", LocalDate.of(2026, 4, 12));
        Vote secondVote = savedVote(2L, "두 번째 최근 투표", LocalDate.of(2026, 4, 11));
        LocalDateTime now = currentTime();
        LocalDate today = now.toLocalDate();
        when(voteRepository.findRecentOngoingVotes(eq(now), eq(today), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(firstVote, secondVote));

        RecentVoteListResponse response = voteService.getRecentVotes(false);

        assertThat(response.count()).isEqualTo(2);
        assertThat(response.votes()).hasSize(2);
        assertThat(response.votes().get(0).name()).isEqualTo("가장 최근 투표");
        assertThat(response.votes().get(0).currentParticipantCount()).isNull();
        assertThat(response.votes().get(0).participantCountVisible()).isFalse();
        verify(voteAttendanceRepository, never()).countAttendancesByVoteId(any(Long.class));
    }

    @Test
    @DisplayName("투표 상세 조회 시 투표 정보와 현재 참석 인원 수를 반환한다")
    void getVoteReturnsDetailResponse() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(2L);

        VoteDetailResponse response = voteService.getVote(1L);

        assertThat(response.voteId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("목요 정기 운동");
        assertThat(response.type()).isEqualTo(ActivityType.REGULAR);
        assertThat(response.openedByMemberId()).isNull();
        assertThat(response.openedByNickname()).isNull();
        assertThat(response.activityDate()).isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(response.location()).isEqualTo("이화체육관");
        assertThat(response.memo()).isEqualTo("테스트 모임");
        assertThat(response.currentParticipantCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("번개 모임 상세 조회 시 개설자 정보를 함께 반환한다")
    void getVoteReturnsOpenerForFlashVote() {
        Vote vote = savedVote(1L, "번개 운동");
        ReflectionTestUtils.setField(vote, "activityType", ActivityType.FLASH);
        ReflectionTestUtils.setField(vote, "openedByMemberId", 5L);
        ReflectionTestUtils.setField(vote, "openedByNickname", "member-5001");
        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(2L);

        VoteDetailResponse response = voteService.getVote(1L);

        assertThat(response.openedByMemberId()).isEqualTo(5L);
        assertThat(response.openedByNickname()).isEqualTo("member-5001");
    }

    @Test
    @DisplayName("특정 투표의 게스트 목록을 반환한다")
    void getVoteGuestsReturnsGuestList() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "activityDate", LocalDate.of(2026, 4, 7));
        Member inviter = savedMember(2L, "2001");
        ReflectionTestUtils.setField(inviter, "nickname", "host-2001");

        Guest guest = Guest.builder()
                .vote(vote)
                .member(inviter)
                .name("게스트")
                .gender(Gender.FEMALE)
                .level("A")
                .build();
        ReflectionTestUtils.setField(guest, "id", 10L);

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(anyCollection())).thenReturn(List.of(guest));
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteIds(anyCollection())).thenReturn(List.of());

        VoteGuestListResponse response = voteService.getVoteGuests(1L);

        assertThat(response.totalGuestCount()).isEqualTo(1L);
        assertThat(response.confirmedGuestCount()).isEqualTo(1L);
        assertThat(response.waitingGuestCount()).isZero();
        assertThat(response.guestQuota()).isEqualTo(3);
        assertThat(response.guests()).hasSize(1);
        assertThat(response.guests().get(0).guestId()).isEqualTo(10L);
        assertThat(response.guests().get(0).voteId()).isEqualTo(1L);
        assertThat(response.guests().get(0).inviterName()).isEqualTo("host-2001");
        assertThat(response.guests().get(0).guestName()).isEqualTo("게스트");
        assertThat(response.guests().get(0).gender()).isEqualTo(Gender.FEMALE);
        assertThat(response.guests().get(0).level()).isEqualTo("A");
        assertThat(response.guests().get(0).isWaiting()).isFalse();
    }

    @Test
    @DisplayName("추가참여 투표 게스트 조회 시 기존 투표 게스트는 제외한다")
    void getVoteGuestsExcludesBaseVoteGuestsForAdditionalVote() {
        Vote baseVote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(baseVote, "activityDate", LocalDate.of(2026, 4, 7));
        Vote additionalVote = Vote.createAdditionalVote(
                baseVote,
                currentTime().minusMinutes(30),
                currentTime().plusHours(1),
                2
        );
        ReflectionTestUtils.setField(additionalVote, "id", 2L);

        Guest baseGuest = guest(baseVote, savedMember(2L, "2001"), 10L, "기존 게스트");
        Guest additionalGuest = guest(additionalVote, savedMember(3L, "2002"), 11L, "추가 게스트");

        when(voteRepository.findById(2L)).thenReturn(Optional.of(additionalVote));
        when(voteRepository.findAllByOriginalVote_IdOrderByIdAsc(1L)).thenReturn(List.of(additionalVote));
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteIds(anyCollection())).thenReturn(List.of());
        when(guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(anyCollection()))
                .thenReturn(List.of(baseGuest, additionalGuest));

        VoteGuestListResponse response = voteService.getVoteGuests(2L);

        assertThat(response.totalGuestCount()).isEqualTo(1L);
        assertThat(response.confirmedGuestCount()).isEqualTo(1L);
        assertThat(response.waitingGuestCount()).isZero();
        assertThat(response.guests()).hasSize(1);
        assertThat(response.guests().get(0).guestId()).isEqualTo(11L);
        assertThat(response.guests().get(0).voteId()).isEqualTo(2L);
        assertThat(response.guests().get(0).guestName()).isEqualTo("추가 게스트");
    }

    @Test
    @DisplayName("화요일 정원보다 많은 게스트는 대기 상태로 반환한다")
    void getVoteGuestsMarksOverflowGuestsAsWaiting() {
        Vote vote = savedVote(1L, "화요 정기 운동");
        ReflectionTestUtils.setField(vote, "activityDate", LocalDate.of(2026, 4, 7));

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteIds(anyCollection())).thenReturn(List.of());
        when(guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(anyCollection())).thenReturn(List.of(
                guest(vote, savedMember(2L, "2001"), 10L, "게스트1"),
                guest(vote, savedMember(3L, "2002"), 11L, "게스트2"),
                guest(vote, savedMember(4L, "2003"), 12L, "게스트3"),
                guest(vote, savedMember(5L, "2004"), 13L, "게스트4")
        ));

        VoteGuestListResponse response = voteService.getVoteGuests(1L);

        assertThat(response.totalGuestCount()).isEqualTo(4L);
        assertThat(response.confirmedGuestCount()).isEqualTo(3L);
        assertThat(response.waitingGuestCount()).isEqualTo(1L);
        assertThat(response.guestQuota()).isEqualTo(3);
        assertThat(response.guests()).hasSize(4);
        assertThat(response.guests().get(0).isWaiting()).isFalse();
        assertThat(response.guests().get(1).isWaiting()).isFalse();
        assertThat(response.guests().get(2).isWaiting()).isFalse();
        assertThat(response.guests().get(3).isWaiting()).isTrue();
    }

    @Test
    @DisplayName("주말 정기 운동은 기본 게스트 quota가 2명이다")
    void getVoteGuestsUsesWeekendQuotaForRegularVote() {
        Vote vote = savedVote(1L, "토요 정기 운동");
        ReflectionTestUtils.setField(vote, "activityDate", LocalDate.of(2026, 4, 11));

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteIds(anyCollection())).thenReturn(List.of());
        when(guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(anyCollection())).thenReturn(List.of(
                guest(vote, savedMember(2L, "2001"), 10L, "게스트1"),
                guest(vote, savedMember(3L, "2002"), 11L, "게스트2"),
                guest(vote, savedMember(4L, "2003"), 12L, "게스트3")
        ));

        VoteGuestListResponse response = voteService.getVoteGuests(1L);

        assertThat(response.confirmedGuestCount()).isEqualTo(2L);
        assertThat(response.waitingGuestCount()).isEqualTo(1L);
        assertThat(response.guestQuota()).isEqualTo(2);
        assertThat(response.guests().get(0).isWaiting()).isFalse();
        assertThat(response.guests().get(1).isWaiting()).isFalse();
        assertThat(response.guests().get(2).isWaiting()).isTrue();
    }

    @Test
    @DisplayName("번개 운동 게스트는 제한 없이 모두 확정된다")
    void getVoteGuestsConfirmsAllFlashGuests() {
        Vote vote = savedVote(1L, "번개 운동");
        ReflectionTestUtils.setField(vote, "activityType", ActivityType.FLASH);
        ReflectionTestUtils.setField(vote, "capacity", 1);

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteIds(anyCollection())).thenReturn(List.of());
        when(guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(anyCollection())).thenReturn(List.of(
                guest(vote, savedMember(2L, "2001"), 10L, "게스트1"),
                guest(vote, savedMember(3L, "2002"), 11L, "게스트2"),
                guest(vote, savedMember(4L, "2003"), 12L, "게스트3")
        ));

        VoteGuestListResponse response = voteService.getVoteGuests(1L);

        assertThat(response.confirmedGuestCount()).isEqualTo(3L);
        assertThat(response.waitingGuestCount()).isZero();
        assertThat(response.guestQuota()).isEqualTo(3);
        assertThat(response.guests()).allMatch(guestSummary -> !guestSummary.isWaiting());
    }

    @Test
    @DisplayName("이벤트 운동 게스트는 모두 대기 상태로 반환한다")
    void getVoteGuestsMarksAllEventGuestsAsWaiting() {
        Vote vote = savedVote(1L, "행사 운동");
        ReflectionTestUtils.setField(vote, "activityType", ActivityType.EVENT);

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteIds(anyCollection())).thenReturn(List.of());
        when(guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(anyCollection())).thenReturn(List.of(
                guest(vote, savedMember(2L, "2001"), 10L, "게스트1"),
                guest(vote, savedMember(3L, "2002"), 11L, "게스트2")
        ));

        VoteGuestListResponse response = voteService.getVoteGuests(1L);

        assertThat(response.confirmedGuestCount()).isZero();
        assertThat(response.waitingGuestCount()).isEqualTo(2L);
        assertThat(response.guestQuota()).isZero();
        assertThat(response.guests()).allMatch(guestSummary -> guestSummary.isWaiting());
    }

    @Test
    @DisplayName("현재 로그인한 회원의 투표 참석 여부를 반환한다")
    void getVoteAttendanceReturnsAttendanceStatus() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member member = savedMember(2L, "2001");
        VoteAttendance attendance = VoteAttendance.builder()
                .vote(vote)
                .member(member)
                .attendanceStatus(true)
                .build();

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.of(attendance));

        VoteAttendanceStatusResponse response = voteService.getVoteAttendance(1L, 2L);

        assertThat(response.attendanceStatus()).isTrue();
    }

    @Test
    @DisplayName("아직 응답하지 않은 회원의 투표 참석 여부는 null을 반환한다")
    void getVoteAttendanceReturnsNullWhenAttendanceIsMissing() {
        Vote vote = savedVote(1L, "목요 정기 운동");

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.empty());

        VoteAttendanceStatusResponse response = voteService.getVoteAttendance(1L, 2L);

        assertThat(response.attendanceStatus()).isNull();
    }

    @Test
    @DisplayName("특정 투표에서 참석 상태가 true인 회원 목록을 참석 순서대로 반환하고 초과 인원은 대기 처리한다")
    void getVoteAttendeesReturnsAttendeeList() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "capacity", 1);
        Member firstMember = savedMember(2L, "2001", true);
        Member secondMember = savedMember(3L, "2002");
        ReflectionTestUtils.setField(firstMember, "nickname", "member-2001");
        ReflectionTestUtils.setField(secondMember, "nickname", "member-2002");

        VoteAttendance firstAttendance = VoteAttendance.builder()
                .vote(vote)
                .member(firstMember)
                .attendanceStatus(true)
                .build();
        VoteAttendance secondAttendance = VoteAttendance.builder()
                .vote(vote)
                .member(secondMember)
                .attendanceStatus(true)
                .build();
        ReflectionTestUtils.setField(firstAttendance, "attendingAt", LocalDateTime.of(2026, 6, 12, 10, 0));
        ReflectionTestUtils.setField(secondAttendance, "attendingAt", LocalDateTime.of(2026, 6, 12, 10, 5));

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteId(1L))
                .thenReturn(List.of(firstAttendance, secondAttendance));

        VoteAttendeeListResponse response = voteService.getVoteAttendees(1L);

        assertThat(response.totalAttendeeCount()).isEqualTo(2L);
        assertThat(response.attendees()).hasSize(2);
        assertThat(response.attendees().get(0).attendeeNumber()).isEqualTo(1);
        assertThat(response.attendees().get(0).memberId()).isEqualTo(2L);
        assertThat(response.attendees().get(0).nickname()).isEqualTo("member-2001");
        assertThat(response.attendees().get(0).isWaiting()).isFalse();
        assertThat(response.attendees().get(0).isAdmin()).isTrue();
        assertThat(response.attendees().get(1).attendeeNumber()).isEqualTo(2);
        assertThat(response.attendees().get(1).memberId()).isEqualTo(3L);
        assertThat(response.attendees().get(1).nickname()).isEqualTo("member-2002");
        assertThat(response.attendees().get(1).isWaiting()).isTrue();
        assertThat(response.attendees().get(1).isAdmin()).isFalse();
    }

    @Test
    @DisplayName("참석자 목록 조회 시 부원명이 있으면 닉네임 대신 부원명을 반환한다")
    void getVoteAttendeesUsesClubMemberNameWhenAvailable() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member member = savedMember(2L, "2001");
        ReflectionTestUtils.setField(member, "nickname", "member-2001");

        VoteAttendance attendance = VoteAttendance.builder()
                .vote(vote)
                .member(member)
                .attendanceStatus(true)
                .build();
        ReflectionTestUtils.setField(attendance, "attendingAt", LocalDateTime.of(2026, 6, 12, 10, 0));

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteId(1L)).thenReturn(List.of(attendance));
        when(clubMemberLookupService.resolveDisplayName(eq(member), anyMap())).thenReturn("강해린");

        VoteAttendeeListResponse response = voteService.getVoteAttendees(1L);

        assertThat(response.attendees()).hasSize(1);
        assertThat(response.attendees().get(0).nickname()).isEqualTo("강해린");
    }

    @Test
    @DisplayName("번개 운동 참석자는 정원값과 무관하게 모두 확정된다")
    void getVoteAttendeesConfirmsAllFlashAttendees() {
        Vote vote = savedVote(1L, "번개 운동");
        ReflectionTestUtils.setField(vote, "activityType", ActivityType.FLASH);
        ReflectionTestUtils.setField(vote, "capacity", 0);
        Member firstMember = savedMember(2L, "2001");
        Member secondMember = savedMember(3L, "2002");
        ReflectionTestUtils.setField(firstMember, "nickname", "member-2001");
        ReflectionTestUtils.setField(secondMember, "nickname", "member-2002");

        VoteAttendance firstAttendance = VoteAttendance.builder()
                .vote(vote)
                .member(firstMember)
                .attendanceStatus(true)
                .build();
        VoteAttendance secondAttendance = VoteAttendance.builder()
                .vote(vote)
                .member(secondMember)
                .attendanceStatus(true)
                .build();
        ReflectionTestUtils.setField(firstAttendance, "attendingAt", LocalDateTime.of(2026, 6, 12, 10, 0));
        ReflectionTestUtils.setField(secondAttendance, "attendingAt", LocalDateTime.of(2026, 6, 12, 10, 5));

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteId(1L))
                .thenReturn(List.of(firstAttendance, secondAttendance));

        VoteAttendeeListResponse response = voteService.getVoteAttendees(1L);

        assertThat(response.totalAttendeeCount()).isEqualTo(2L);
        assertThat(response.attendees()).allMatch(attendee -> !attendee.isWaiting());
    }

    @Test
    @DisplayName("특정 투표에서 참석 상태가 false인 회원 목록과 총원을 반환한다")
    void getVoteAbsenteesReturnsAbsenteeList() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member firstMember = savedMember(2L, "2001", true);
        Member secondMember = savedMember(3L, "2002");
        ReflectionTestUtils.setField(firstMember, "nickname", "member-2001");
        ReflectionTestUtils.setField(secondMember, "nickname", "member-2002");

        VoteAttendance firstAttendance = VoteAttendance.builder()
                .vote(vote)
                .member(firstMember)
                .attendanceStatus(false)
                .build();
        VoteAttendance secondAttendance = VoteAttendance.builder()
                .vote(vote)
                .member(secondMember)
                .attendanceStatus(false)
                .build();
        LocalDateTime firstVotedAt = LocalDateTime.of(2026, 4, 10, 10, 0);
        LocalDateTime secondVotedAt = LocalDateTime.of(2026, 4, 10, 10, 5);
        ReflectionTestUtils.setField(firstAttendance, "votedAt", firstVotedAt);
        ReflectionTestUtils.setField(secondAttendance, "votedAt", secondVotedAt);

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findAllInactiveWithMemberByVoteId(1L))
                .thenReturn(List.of(secondAttendance, firstAttendance));

        VoteAbsenteeListResponse response = voteService.getVoteAbsentees(1L);

        assertThat(response.totalAbsenteeCount()).isEqualTo(2L);
        assertThat(response.absentees()).hasSize(2);
        assertThat(response.absentees().get(0).memberId()).isEqualTo(2L);
        assertThat(response.absentees().get(0).nickname()).isEqualTo("member-2001");
        assertThat(response.absentees().get(0).votedAt()).isEqualTo(firstVotedAt);
        assertThat(response.absentees().get(0).isAdmin()).isTrue();
        assertThat(response.absentees().get(1).memberId()).isEqualTo(3L);
        assertThat(response.absentees().get(1).nickname()).isEqualTo("member-2002");
        assertThat(response.absentees().get(1).votedAt()).isEqualTo(secondVotedAt);
        assertThat(response.absentees().get(1).isAdmin()).isFalse();
    }

    @Test
    @DisplayName("게스트 참여 정보를 생성한다")
    void createGuestSavesGuest() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member member = savedMember(2L, "2001");
        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);

        voteService.createGuest(1L, 2L, new VoteGuestRequest("게스트", Gender.FEMALE, "A"));

        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(guestRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("게스트");
        assertThat(captor.getValue().getGender()).isEqualTo(Gender.FEMALE);
        assertThat(captor.getValue().getLevel()).isEqualTo("A");
    }

    @Test
    @DisplayName("게스트 신청은 정원이 꽉 차도 저장된다")
    void createGuestAllowsWaitlistWhenQuotaIsFull() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "capacity", 2);
        ReflectionTestUtils.setField(vote, "activityDate", LocalDate.of(2026, 4, 7));
        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(savedMember(2L, "2001"));

        voteService.createGuest(1L, 2L, new VoteGuestRequest("게스트", Gender.FEMALE, "A"));

        verify(guestRepository).save(any(Guest.class));
    }

    @Test
    @DisplayName("게스트를 추가한 사람은 본인 게스트를 수정할 수 있다")
    void updateGuestUpdatesGuest() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member inviter = savedMember(2L, "2001");
        Guest guest = Guest.builder()
                .vote(vote)
                .member(inviter)
                .name("기존 게스트")
                .gender(Gender.FEMALE)
                .level("A")
                .build();

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(guestRepository.findByIdAndVote_IdIn(eq(10L), anyCollection())).thenReturn(Optional.of(guest));

        voteService.updateGuest(1L, 10L, 2L, new VoteGuestRequest("수정 게스트", Gender.MALE, "B"));

        assertThat(guest.getName()).isEqualTo("수정 게스트");
        assertThat(guest.getGender()).isEqualTo(Gender.MALE);
        assertThat(guest.getLevel()).isEqualTo("B");
    }

    @Test
    @DisplayName("게스트를 추가한 사람만 해당 게스트를 수정할 수 있다")
    void updateGuestThrowsWhenRequesterIsNotInviter() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member inviter = savedMember(2L, "2001");
        Guest guest = Guest.builder()
                .vote(vote)
                .member(inviter)
                .name("기존 게스트")
                .gender(Gender.FEMALE)
                .level("A")
                .build();

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(guestRepository.findByIdAndVote_IdIn(eq(10L), anyCollection())).thenReturn(Optional.of(guest));

        assertThatThrownBy(() -> voteService.updateGuest(1L, 10L, 3L, new VoteGuestRequest("수정 게스트", Gender.MALE, "B")))
                .isInstanceOfSatisfying(YbcException.class,
                        exception -> assertThat(exception.getExceptionCodeName()).isEqualTo("GUEST_UPDATE_FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 게스트는 수정할 수 없다")
    void updateGuestThrowsWhenGuestIsMissing() {
        Vote vote = savedVote(1L, "목요 정기 운동");

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(guestRepository.findByIdAndVote_IdIn(eq(10L), anyCollection())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voteService.updateGuest(1L, 10L, 2L, new VoteGuestRequest("수정 게스트", Gender.MALE, "B")))
                .isInstanceOfSatisfying(YbcException.class,
                        exception -> assertThat(exception.getExceptionCodeName()).isEqualTo("GUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("투표 참여 정보가 없으면 새로 생성한다")
    void saveVoteAttendanceCreatesNewAttendance() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member member = savedMember(2L, "2001");
        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.empty());

        voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(true));

        ArgumentCaptor<VoteAttendance> captor = ArgumentCaptor.forClass(VoteAttendance.class);
        verify(voteAttendanceRepository).save(captor.capture());
        assertThat(captor.getValue().isAttendanceStatus()).isTrue();
        assertThat(captor.getValue().getVote()).isEqualTo(vote);
        assertThat(captor.getValue().getMember()).isEqualTo(member);
    }

    @Test
    @DisplayName("기존 투표 참여 정보가 있으면 참석 여부만 수정한다")
    void saveVoteAttendanceUpdatesExistingAttendance() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member member = savedMember(2L, "2001");
        VoteAttendance attendance = VoteAttendance.builder()
                .vote(vote)
                .member(member)
                .attendanceStatus(true)
                .build();

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.of(attendance));

        voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(false));

        assertThat(attendance.isAttendanceStatus()).isFalse();
        verify(voteAttendanceRepository, never()).save(any(VoteAttendance.class));
    }

    @Test
    @DisplayName("마감된 투표에는 참석 여부를 저장할 수 없다")
    void saveVoteAttendanceThrowsWhenVoteIsClosed() {
        Vote vote = Vote.builder()
                .title("목요 정기 운동")
                .activityType(ActivityType.REGULAR)
                .activityDate(LocalDate.of(2026, 4, 10))
                .activityTime("19:00")
                .location("이화체육관")
                .memo("테스트 모임")
                .voteStartAt(currentTime().minusDays(2))
                .voteEndAt(currentTime().minusMinutes(1))
                .capacity(20)
                .build();
        ReflectionTestUtils.setField(vote, "id", 1L);

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));

        assertThatThrownBy(() -> voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(true)))
                .isInstanceOf(YbcException.class)
                .hasMessage("마감된 투표에는 참여할 수 없습니다.");
    }

    @Test
    @DisplayName("투표 시작 전에는 참석 여부를 저장할 수 없다")
    void saveVoteAttendanceThrowsWhenVoteIsNotOpenYet() {
        Vote vote = Vote.builder()
                .title("목요 정기 운동")
                .activityType(ActivityType.REGULAR)
                .activityDate(LocalDate.of(2026, 4, 10))
                .activityTime("19:00")
                .location("이화체육관")
                .memo("테스트 모임")
                .voteStartAt(currentTime().plusMinutes(10))
                .voteEndAt(currentTime().plusHours(2))
                .capacity(20)
                .build();
        ReflectionTestUtils.setField(vote, "id", 1L);

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));

        assertThatThrownBy(() -> voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(true)))
                .isInstanceOf(YbcException.class)
                .hasMessage("투표 시작 시간 이후에만 참여할 수 있습니다.");
    }

    @Test
    @DisplayName("정원이 가득 차도 새 참석 투표는 저장할 수 있다")
    void saveVoteAttendanceAllowsWaitlistOnCreate() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "capacity", 2);
        Member member = savedMember(2L, "2001");

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.empty());

        voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(true));

        verify(voteAttendanceRepository).save(any(VoteAttendance.class));
    }

    @Test
    @DisplayName("같은 운동의 추가 투표에 이미 참석한 회원은 다시 참석할 수 없다")
    void saveVoteAttendanceThrowsWhenMemberAlreadyJoinedRelatedVote() {
        Vote baseVote = savedVote(1L, "목요 정기 운동");
        Vote additionalVote = Vote.createAdditionalVote(
                baseVote,
                currentTime().minusMinutes(30),
                currentTime().plusHours(1),
                5
        );
        ReflectionTestUtils.setField(additionalVote, "id", 2L);

        Member member = savedMember(2L, "2001");

        when(voteRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(additionalVote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(2L, 2L)).thenReturn(Optional.empty());
        when(voteRepository.findAllByOriginalVote_IdOrderByIdAsc(1L)).thenReturn(List.of(additionalVote));
        when(voteRepository.findAllByActivityTypeAndActivityDateAndActivityTimeAndLocationAndVoteStartAtAndTitleAndOriginalVoteIsNullOrderByIdAsc(
                ActivityType.REGULAR,
                baseVote.getActivityDate(),
                baseVote.getActivityTime(),
                baseVote.getLocation(),
                baseVote.getVoteEndAt(),
                baseVote.getTitle() + Vote.ADDITIONAL_PARTICIPATION_SUFFIX
        )).thenReturn(List.of());
        when(voteAttendanceRepository.existsByVote_IdInAndMember_IdAndAttendanceStatusTrue(anyCollection(), eq(2L)))
                .thenReturn(true);

        assertThatThrownBy(() -> voteService.saveVoteAttendance(2L, 2L, new VoteAttendanceRequest(true)))
                .isInstanceOf(YbcException.class)
                .hasMessage("같은 운동에는 한 번만 참여할 수 있습니다.");
    }

    @Test
    @DisplayName("정원이 가득 차도 불참을 참석으로 바꾸면 대기열에 포함된다")
    void saveVoteAttendanceAllowsWaitlistOnUpdate() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "capacity", 2);
        Member member = savedMember(2L, "2001");
        VoteAttendance attendance = VoteAttendance.builder()
                .vote(vote)
                .member(member)
                .attendanceStatus(false)
                .build();

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.of(attendance));

        voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(true));

        assertThat(attendance.isAttendanceStatus()).isTrue();
        assertThat(attendance.getAttendingAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 투표를 상세 조회하면 예외가 발생한다")
    void getVoteThrowsWhenVoteIsMissing() {
        when(voteRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voteService.getVote(999L))
                .isInstanceOf(YbcException.class)
                .hasMessage("존재하지 않는 투표입니다.");
    }

    @Test
    @DisplayName("투표 히스토리 조회 시 현재 진행 중인 투표 수와 마감된 일반 투표 수를 반환한다")
    void getVoteHistoryReturnsJoinableAndThisYearVoteCounts() {
        when(voteRepository.countByVoteStartAtLessThanEqualAndVoteEndAtAfter(currentTime(), currentTime())).thenReturn(2L);
        when(voteRepository.countCompletedVotesExcludingAdditional(currentTime(), Vote.ADDITIONAL_PARTICIPATION_SUFFIX))
                .thenReturn(18L);

        VoteHistoryResponse response = voteService.getVoteHistory();

        assertThat(response.joinableVotes()).isEqualTo(2L);
        assertThat(response.thisYearVotes()).isEqualTo(18L);
    }

    @Test
    @DisplayName("투표 히스토리 조회 시 현재 시각과 추가참여 suffix로 집계 조건을 전달한다")
    void getVoteHistoryUsesCurrentTimeAndAdditionalSuffix() {
        when(voteRepository.countByVoteStartAtLessThanEqualAndVoteEndAtAfter(currentTime(), currentTime())).thenReturn(0L);
        when(voteRepository.countCompletedVotesExcludingAdditional(currentTime(), Vote.ADDITIONAL_PARTICIPATION_SUFFIX))
                .thenReturn(0L);

        voteService.getVoteHistory();

        verify(voteRepository).countByVoteStartAtLessThanEqualAndVoteEndAtAfter(currentTime(), currentTime());
        verify(voteRepository).countCompletedVotesExcludingAdditional(currentTime(), Vote.ADDITIONAL_PARTICIPATION_SUFFIX);
    }

    private Vote savedVote(Long id, String title) {
        return savedVote(id, title, LocalDate.of(2026, 4, 10));
    }

    private Vote savedVote(Long id, String title, LocalDate activityDate) {
        LocalDateTime now = currentTime();
        Vote vote = Vote.builder()
                .title(title)
                .activityType(ActivityType.REGULAR)
                .activityDate(activityDate)
                .activityTime("19:00")
                .location("이화체육관")
                .memo("테스트 모임")
                .voteStartAt(now.minusHours(1))
                .voteEndAt(now.plusDays(1))
                .capacity(20)
                .build();
        ReflectionTestUtils.setField(vote, "id", id);
        return vote;
    }

    private Member savedMember(Long id, String socialId) {
        return savedMember(id, socialId, false);
    }

    private Member savedMember(Long id, String socialId, boolean isAdmin) {
        Member member = Member.createSocialMember(
                SocialProvider.KAKAO,
                socialId,
                "member-" + socialId,
                socialId + "@example.com",
                isAdmin
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Guest guest(Vote vote, Member inviter, Long id, String name) {
        Guest guest = Guest.builder()
                .vote(vote)
                .member(inviter)
                .name(name)
                .gender(Gender.FEMALE)
                .level("A")
                .build();
        ReflectionTestUtils.setField(guest, "id", id);
        return guest;
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(clock);
    }
}
