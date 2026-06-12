package com.ybc.ybc.vote.service;

import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.dto.request.AdminVoteCreateRequest;
import com.ybc.ybc.vote.dto.response.AdminCompletedVoteResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteListItemResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteListResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteQueueResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteReservationResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteStatus;
import com.ybc.ybc.vote.repository.GuestRepository;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.VoteMatchGroupRepository;
import com.ybc.ybc.vote.repository.VoteMatchRepository;
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
import java.util.Optional;
import java.util.Set;

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

    @Mock
    private VoteMatchRepository voteMatchRepository;

    @Mock
    private VoteMatchGroupRepository voteMatchGroupRepository;

    private AdminVoteService adminVoteService;

    @BeforeEach
    void setUp() {
        adminVoteService = new AdminVoteService(voteRepository, voteAttendanceRepository, guestRepository, voteMatchRepository, voteMatchGroupRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("투표 예약 시 요청 정원으로 저장한다")
    void createVoteUsesRequestedCapacity() {
        AdminVoteCreateRequest request = new AdminVoteCreateRequest(
                "FLASH",
                "금요일 번개 운동",
                LocalDate.of(2026, 4, 11),
                "19:00 ~ 21:00",
                "망원체육관",
                "라켓 지참",
                LocalDateTime.of(2026, 4, 10, 18, 0),
                LocalDateTime.of(2026, 4, 10, 23, 0),
                20
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
        assertThat(response.activityType()).isEqualTo(ActivityType.FLASH);
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
    @DisplayName("투표 예약 수정 시 현재 참석 집계와 상태를 함께 반환한다")
    void updateVoteReturnsManagementItemWithAttendance() {
        Vote vote = vote(1L, "기존 번개 운동",
                LocalDate.of(2026, 4, 10),
                LocalDateTime.of(2026, 4, 10, 9, 0),
                LocalDateTime.of(2026, 4, 10, 21, 0),
                ActivityType.FLASH,
                20);
        AdminVoteCreateRequest request = new AdminVoteCreateRequest(
                "REGULAR",
                "수정된 정기 운동",
                LocalDate.of(2026, 4, 10),
                "15:00 ~ 18:00",
                "마포구민체육센터",
                "장소 변경 안내",
                LocalDateTime.of(2026, 4, 10, 11, 0),
                LocalDateTime.of(2026, 4, 10, 18, 0),
                30
        );

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(12L);
        when(guestRepository.countByVote_Id(1L)).thenReturn(2L);

        AdminVoteListItemResponse response = adminVoteService.updateVote(1L, request);

        assertThat(vote.getActivityType()).isEqualTo(ActivityType.REGULAR);
        assertThat(vote.getTitle()).isEqualTo("수정된 정기 운동");
        assertThat(vote.getLocation()).isEqualTo("마포구민체육센터");
        assertThat(vote.getCapacity()).isEqualTo(30);
        assertThat(response.status()).isEqualTo(AdminVoteStatus.IN_PROGRESS);
        assertThat(response.capacity()).isEqualTo(30);
        assertThat(response.attendance().currentAttendees()).isEqualTo(12L);
        assertThat(response.attendance().currentGuests()).isEqualTo(2L);
        assertThat(response.attendance().totalParticipants()).isEqualTo(12L);
    }

    @Test
    @DisplayName("투표 예약 삭제 시 참석자와 게스트를 먼저 정리한 뒤 투표를 삭제한다")
    void deleteVoteRemovesRelatedDataBeforeDeletingVote() {
        Vote vote = vote(1L, "삭제할 투표",
                LocalDate.of(2026, 4, 10),
                LocalDateTime.of(2026, 4, 10, 9, 0),
                LocalDateTime.of(2026, 4, 10, 21, 0),
                ActivityType.REGULAR,
                20);

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));

        adminVoteService.deleteVote(1L);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(voteRepository, voteMatchRepository, voteMatchGroupRepository, guestRepository, voteAttendanceRepository);
        inOrder.verify(voteRepository).findById(1L);
        inOrder.verify(voteMatchRepository).deleteAllByVote_IdIn(java.util.Set.of(1L));
        inOrder.verify(voteMatchGroupRepository).deleteAllByVote_IdIn(java.util.Set.of(1L));
        inOrder.verify(guestRepository).deleteAllByVote_Id(1L);
        inOrder.verify(voteAttendanceRepository).deleteAllByVote_Id(1L);
        inOrder.verify(voteRepository).delete(vote);
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
                ActivityType.FLASH,
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
                ActivityType.FLASH,
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
        assertThat(response.data().get(0).capacity()).isEqualTo(20);
        assertThat(response.data().get(1).capacity()).isEqualTo(20);
        assertThat(response.data().get(2).capacity()).isEqualTo(30);
        assertThat(response.data().get(1).attendance().currentAttendees()).isEqualTo(12L);
        assertThat(response.data().get(1).attendance().currentGuests()).isEqualTo(2L);
        assertThat(response.data().get(1).attendance().totalParticipants()).isEqualTo(12L);
    }

    @Test
    @DisplayName("투표 완료된 운동 리스트 조회 시 REGULAR, EVENT만 반환하고 화요일 운동은 제외한다")
    void getCompletedVotesReturnsOnlyVisibleActivities() {
        Vote includedRegularVote = vote(1L, "지난 정기 운동",
                LocalDate.of(2026, 4, 8),
                LocalDateTime.of(2026, 4, 8, 9, 0),
                LocalDateTime.of(2026, 4, 8, 12, 0),
                ActivityType.REGULAR,
                20);
        Vote excludedFlashVote = vote(2L, "지난 번개 운동",
                LocalDate.of(2026, 4, 9),
                LocalDateTime.of(2026, 4, 9, 9, 0),
                LocalDateTime.of(2026, 4, 9, 12, 0),
                ActivityType.FLASH,
                20);
        Vote excludedTuesdayEventVote = vote(3L, "화요일 이벤트 운동",
                LocalDate.of(2026, 4, 7),
                LocalDateTime.of(2026, 4, 7, 9, 0),
                LocalDateTime.of(2026, 4, 7, 12, 0),
                ActivityType.EVENT,
                20);
        Vote inProgressVote = vote(4L, "오늘 번개 운동",
                LocalDate.of(2026, 4, 10),
                LocalDateTime.of(2026, 4, 10, 11, 0),
                LocalDateTime.of(2026, 4, 10, 20, 0),
                ActivityType.FLASH,
                20);
        when(voteRepository.findAllByVoteEndAtLessThanEqualOrderByVoteEndAtAscIdAsc(LocalDateTime.of(2026, 4, 10, 12, 0)))
                .thenReturn(List.of());
        when(voteRepository.findAll(any(Sort.class))).thenReturn(List.of(
                inProgressVote,
                includedRegularVote,
                excludedFlashVote,
                excludedTuesdayEventVote
        ));
        when(voteAttendanceRepository.countAttendancesByVoteIds(List.of(1L)))
                .thenReturn(List.of(new VoteAttendanceCountProjection(1L, 12L)));
        when(guestRepository.countGuestsByVoteIds(List.of(1L)))
                .thenReturn(List.of(new VoteGuestCountProjection(1L, 2L)));
        when(voteMatchGroupRepository.findAllByVote_IdIn(Set.of(1L)))
                .thenReturn(List.of(com.ybc.ybc.vote.domain.VoteMatchGroup.create(includedRegularVote)));

        List<AdminCompletedVoteResponse> response = adminVoteService.getCompletedVotes();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).voteId()).isEqualTo(1L);
        assertThat(response.get(0).title()).isEqualTo("지난 정기 운동");
        assertThat(response.get(0).activityDay()).isEqualTo("수요일");
        assertThat(response.get(0).attendance().currentAttendees()).isEqualTo(12L);
        assertThat(response.get(0).attendance().currentGuests()).isEqualTo(2L);
        assertThat(response.get(0).attendance().totalParticipants()).isEqualTo(12L);
        assertThat(response.get(0).matchRegistered()).isTrue();
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
                .thenReturn(List.of(new VoteAttendanceCountProjection(1L, 17L)));

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
    @DisplayName("정규운동 공석이 4자리 이상이면 추가참여 투표를 만들지 않는다")
    void reconcileAdditionalVotesSkipsWhenVacancyExceedsThree() {
        Vote endedVote = vote(1L, "목요 정기 운동",
                LocalDate.of(2026, 4, 10),
                LocalDateTime.of(2026, 4, 9, 12, 0),
                LocalDateTime.of(2026, 4, 10, 11, 0),
                ActivityType.REGULAR,
                20);

        when(voteRepository.findAllByVoteEndAtLessThanEqualOrderByVoteEndAtAscIdAsc(LocalDateTime.of(2026, 4, 10, 12, 0)))
                .thenReturn(List.of(endedVote));
        when(voteAttendanceRepository.countAttendancesByVoteIds(List.of(1L)))
                .thenReturn(List.of(new VoteAttendanceCountProjection(1L, 16L)));

        adminVoteService.reconcileAdditionalVotes();

        verify(voteRepository, org.mockito.Mockito.never()).saveAll(any());
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
                .thenReturn(List.of(new VoteAttendanceCountProjection(1L, 17L)));

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
                ActivityType.FLASH,
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
