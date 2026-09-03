package com.ybc.ybc.vote.service;

import com.ybc.ybc.vote.dto.response.ActiveVoteSummaryResponse;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.projection.ActiveVoteCandidateProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberActiveVoteServiceTest {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-03T11:30:00Z");

    @Mock
    private VoteAttendanceRepository voteAttendanceRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private MemberActiveVoteService memberActiveVoteService;

    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(FIXED_INSTANT);
        lenient().when(clock.getZone()).thenReturn(KOREA);
    }

    @Test
    @DisplayName("회원이 응답한 투표 중 ACTIVE 상태인 항목만 반환한다")
    void getParticipatedActiveVotesReturnsOnlyActiveVotes() {
        LocalDateTime now = currentTime();
        LocalDate today = now.toLocalDate();
        ActiveVoteCandidateProjection activeVote = new ActiveVoteCandidateProjection(
                10L,
                "정기모임 9월 1주차",
                LocalDateTime.of(2026, 9, 2, 10, 0),
                LocalDateTime.of(2026, 9, 3, 18, 0),
                LocalDate.of(2026, 9, 3),
                "19:00 ~ 21:00"
        );
        ActiveVoteCandidateProjection completedVote = new ActiveVoteCandidateProjection(
                11L,
                "정기모임 8월 5주차",
                LocalDateTime.of(2026, 9, 2, 10, 0),
                LocalDateTime.of(2026, 9, 3, 16, 0),
                LocalDate.of(2026, 9, 3),
                "17:00 ~ 18:00"
        );

        when(voteAttendanceRepository.findParticipatedVoteCandidates(1L, now, today))
                .thenReturn(List.of(activeVote, completedVote));

        List<ActiveVoteSummaryResponse> response = memberActiveVoteService.getParticipatedActiveVotes(1L);

        assertThat(response).containsExactly(new ActiveVoteSummaryResponse(10L, "정기모임 9월 1주차"));
        verify(voteAttendanceRepository).findParticipatedVoteCandidates(1L, now, today);
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
