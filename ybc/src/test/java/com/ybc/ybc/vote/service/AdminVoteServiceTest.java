package com.ybc.ybc.vote.service;

import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.dto.request.AdminVoteCreateRequest;
import com.ybc.ybc.vote.dto.response.AdminVoteListResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteQueueResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteReservationResponse;
import com.ybc.ybc.vote.repository.GuestRepository;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.VoteRepository;
import com.ybc.ybc.vote.repository.projection.VoteAttendanceCountProjection;
import com.ybc.ybc.vote.repository.projection.VoteGuestCountProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminVoteServiceTest {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-10T03:00:00Z"), KOREA);

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private VoteAttendanceRepository voteAttendanceRepository;

    @Mock
    private GuestRepository guestRepository;

    private AdminVoteService adminVoteService;

    @BeforeEach
    void setUp() {
        adminVoteService = new AdminVoteService(voteRepository, voteAttendanceRepository, guestRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("투표 예약 시 정원이 없으면 기본 정원 20명으로 저장한다")
    void createVoteUsesDefaultCapacityWhenMissing() {
        AdminVoteCreateRequest request = new AdminVoteCreateRequest(
                "FLUSH",
                "금요일 번개 운동",
                LocalDate.of(2026, 4, 11),
                "19:00 ~ 21:00",
                "망원체육관",
                "라켓 지참",
                LocalDateTime.of(2026, 4, 10, 18, 0),
                LocalDateTime.of(2026, 4, 10, 23, 0),
                null
        );

        when(voteRepository.saveAndFlush(any(Vote.class))).thenAnswer(invocation -> {
            Vote vote = invocation.getArgument(0);
            ReflectionTestUtils.setField(vote, "id", 1L);
            ReflectionTestUtils.setField(vote, "createdAt", LocalDateTime.of(2026, 4, 10, 12, 0));
            return vote;
        });

        AdminVoteReservationResponse response = adminVoteService.createVote(request);

        ArgumentCaptor<Vote> captor = ArgumentCaptor.forClass(Vote.class);
        verify(voteRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCapacity()).isEqualTo(20);
        assertThat(response.voteId()).isEqualTo(1L);
        assertThat(response.activityType()).isEqualTo(ActivityType.FLUSH);
        assertThat(response.title()).isEqualTo("금요일 번개 운동");
    }

    @Test
    @DisplayName("투표 예약 시 시작과 종료 시간이 뒤바뀌면 예외가 발생한다")
    void createVoteThrowsWhenVotePeriodIsInvalid() {
        AdminVoteCreateRequest request = new AdminVoteCreateRequest(
                "REGULAR",
                "정기 운동",
                LocalDate.of(2026, 4, 11),
                "14:00 ~ 17:00",
                "망원체육관",
                null,
                LocalDateTime.of(2026, 4, 10, 18, 0),
                LocalDateTime.of(2026, 4, 10, 17, 0),
                20
        );

        assertThatThrownBy(() -> adminVoteService.createVote(request))
                .isInstanceOf(YbcException.class)
                .hasMessage("투표 시작 시간과 종료 시간을 다시 확인해 주세요.");
    }

    @Test
    @DisplayName("투표 예약 대기열 조회 시 시작 전 투표만 반환한다")
    void getVoteQueueReturnsUpcomingVotesOnly() {
        Vote upcomingVote = vote(1L, "다음주 정기 운동",
                LocalDate.of(2026, 4, 11),
                LocalDateTime.of(2026, 4, 10, 15, 0),
                LocalDateTime.of(2026, 4, 10, 22, 0),
                ActivityType.REGULAR,
                20);
        Vote inProgressVote = vote(2L, "오늘 번개 운동",
                LocalDate.of(2026, 4, 10),
                LocalDateTime.of(2026, 4, 10, 11, 0),
                LocalDateTime.of(2026, 4, 10, 20, 0),
                ActivityType.FLUSH,
                20);

        when(voteRepository.findAllByVoteEndAtLessThanEqualOrderByVoteEndAtAscIdAsc(LocalDateTime.of(2026, 4, 10, 12, 0)))
                .thenReturn(List.of());
        when(voteRepository.findAll(any(Sort.class))).thenReturn(List.of(inProgressVote, upcomingVote));

        AdminVoteQueueResponse response = adminVoteService.getVoteQueue();

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).voteId()).isEqualTo(1L);
        assertThat(response.data().get(0).activityType()).isEqualTo(ActivityType.REGULAR);
    }

    @Test
    @DisplayName("투표 현황 조회 시 상태별 요약과 참석 집계를 반환한다")
    void getVoteManagementReturnsSummaryAndAttendance() {
        Vote upcomingVote = vote(1L, "다음주 정기 운동",
                LocalDate.of(2026, 4, 11),
                LocalDateTime.of(2026, 4, 10, 15, 0),
                LocalDateTime.of(2026, 4, 10, 22, 0),
                ActivityType.REGULAR,
                20);
        Vote inProgressVote = vote(2L, "오늘 번개 운동",
                LocalDate.of(2026, 4, 10),
                LocalDateTime.of(2026, 4, 10, 11, 0),
                LocalDateTime.of(2026, 4, 10, 20, 0),
                ActivityType.FLUSH,
                20);
        Vote completedVote = vote(3L, "지난 이벤트 운동",
                LocalDate.of(2026, 4, 9),
                LocalDateTime.of(2026, 4, 9, 11, 0),
                LocalDateTime.of(2026, 4, 9, 20, 0),
                ActivityType.EVENT,
                30);

        when(voteRepository.findAllByVoteEndAtLessThanEqualOrderByVoteEndAtAscIdAsc(LocalDateTime.of(2026, 4, 10, 12, 0)))
                .thenReturn(List.of());
        when(voteRepository.findAll(any(Sort.class))).thenReturn(List.of(upcomingVote, inProgressVote, completedVote));
        when(voteAttendanceRepository.countAttendancesByVoteIds(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(
                        new VoteAttendanceCountProjection(2L, 12L),
                        new VoteAttendanceCountProjection(3L, 5L)
                ));
        when(guestRepository.countGuestsByVoteIds(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(
                        new VoteGuestCountProjection(2L, 2L)
                ));

        AdminVoteListResponse response = adminVoteService.getVoteManagement();

        assertThat(response.summary().totalCount()).isEqualTo(3);
        assertThat(response.summary().upcomingCount()).isEqualTo(1);
        assertThat(response.summary().inProgressCount()).isEqualTo(1);
        assertThat(response.summary().completedCount()).isEqualTo(1);
        assertThat(response.data().get(1).attendance().currentAttendees()).isEqualTo(12L);
        assertThat(response.data().get(1).attendance().currentGuests()).isEqualTo(2L);
        assertThat(response.data().get(1).attendance().totalParticipants()).isEqualTo(14L);
    }

    @Test
    @DisplayName("평일 정규운동이 마감되면 추가참여 투표를 자동 생성한다")
    void reconcileAdditionalVotesCreatesAdditionalVote() {
        Vote endedVote = vote(1L, "수요 정기 운동",
                LocalDate.of(2026, 4, 10),
                LocalDateTime.of(2026, 4, 9, 12, 0),
                LocalDateTime.of(2026, 4, 10, 11, 0),
                ActivityType.REGULAR,
                20);

        when(voteRepository.findAllByVoteEndAtLessThanEqualOrderByVoteEndAtAscIdAsc(LocalDateTime.of(2026, 4, 10, 12, 0)))
                .thenReturn(List.of(endedVote));
        when(voteAttendanceRepository.countAttendancesByVoteIds(List.of(1L)))
                .thenReturn(List.of(new VoteAttendanceCountProjection(1L, 14L)));
        when(guestRepository.countGuestsByVoteIds(List.of(1L)))
                .thenReturn(List.of(new VoteGuestCountProjection(1L, 1L)));

        adminVoteService.reconcileAdditionalVotes();

        ArgumentCaptor<List<Vote>> captor = ArgumentCaptor.forClass(List.class);
        verify(voteRepository).saveAll(captor.capture());
        Vote additionalVote = captor.getValue().get(0);

        assertThat(additionalVote.getActivityType()).isEqualTo(ActivityType.REGULAR);
        assertThat(additionalVote.getTitle()).isEqualTo("수요 정기 운동-추가참여");
        assertThat(additionalVote.getVoteStartAt()).isEqualTo(LocalDateTime.of(2026, 4, 10, 11, 0));
        assertThat(additionalVote.getVoteEndAt()).isEqualTo(LocalDateTime.of(2026, 4, 10, 12, 0));
        assertThat(additionalVote.getCapacity()).isEqualTo(5);
        assertThat(additionalVote.getMemo()).contains("추가참여비 4000원");
    }

    @Test
    @DisplayName("주말 정규운동은 추가참여 정원이 3명이다")
    void reconcileAdditionalVotesUsesWeekendCapacity() {
        Vote endedVote = vote(1L, "토요 정기 운동",
                LocalDate.of(2026, 4, 11),
                LocalDateTime.of(2026, 4, 10, 9, 0),
                LocalDateTime.of(2026, 4, 10, 11, 0),
                ActivityType.REGULAR,
                20);

        when(voteRepository.findAllByVoteEndAtLessThanEqualOrderByVoteEndAtAscIdAsc(LocalDateTime.of(2026, 4, 10, 12, 0)))
                .thenReturn(List.of(endedVote));
        when(voteAttendanceRepository.countAttendancesByVoteIds(List.of(1L)))
                .thenReturn(List.of(new VoteAttendanceCountProjection(1L, 16L)));
        when(guestRepository.countGuestsByVoteIds(List.of(1L)))
                .thenReturn(List.of(new VoteGuestCountProjection(1L, 1L)));

        adminVoteService.reconcileAdditionalVotes();

        ArgumentCaptor<List<Vote>> captor = ArgumentCaptor.forClass(List.class);
        verify(voteRepository).saveAll(captor.capture());
        Vote additionalVote = captor.getValue().get(0);

        assertThat(additionalVote.getCapacity()).isEqualTo(3);
        assertThat(additionalVote.getVoteEndAt()).isEqualTo(LocalDateTime.of(2026, 4, 11, 12, 0));
        assertThat(additionalVote.getMemo()).contains("추가참여비 4000원");
    }

    @Test
    @DisplayName("정규운동이 아니면 추가참여 투표를 만들지 않는다")
    void reconcileAdditionalVotesSkipsNonRegularVote() {
        Vote endedVote = vote(1L, "금요 번개 운동",
                LocalDate.of(2026, 4, 10),
                LocalDateTime.of(2026, 4, 9, 9, 0),
                LocalDateTime.of(2026, 4, 10, 11, 0),
                ActivityType.FLUSH,
                20);

        when(voteRepository.findAllByVoteEndAtLessThanEqualOrderByVoteEndAtAscIdAsc(LocalDateTime.of(2026, 4, 10, 12, 0)))
                .thenReturn(List.of(endedVote));
        when(voteAttendanceRepository.countAttendancesByVoteIds(List.of(1L)))
                .thenReturn(List.of(new VoteAttendanceCountProjection(1L, 20L)));
        when(guestRepository.countGuestsByVoteIds(List.of(1L)))
                .thenReturn(List.of());

        adminVoteService.reconcileAdditionalVotes();

        verify(voteRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    private Vote vote(Long id,
                      String title,
                      LocalDate activityDate,
                      LocalDateTime voteStartAt,
                      LocalDateTime voteEndAt,
                      ActivityType activityType,
                      int capacity) {
        Vote vote = Vote.builder()
                .title(title)
                .activityType(activityType)
                .activityDate(activityDate)
                .activityTime("19:00 ~ 21:00")
                .location("망원체육관")
                .memo("테스트")
                .voteStartAt(voteStartAt)
                .voteEndAt(voteEndAt)
                .capacity(capacity)
                .build();
        ReflectionTestUtils.setField(vote, "id", id);
        ReflectionTestUtils.setField(vote, "createdAt", LocalDateTime.of(2026, 4, 8, 9, 0));
        return vote;
    }
}
