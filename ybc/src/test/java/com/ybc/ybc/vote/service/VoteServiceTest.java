package com.ybc.ybc.vote.service;

import com.ybc.ybc.vote.dto.VoteHistoryResponse;
import com.ybc.ybc.vote.repository.VoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {
    @Mock
    private VoteRepository voteRepository;

    @InjectMocks
    private VoteService voteService;

    @Test
    @DisplayName("투표 히스토리 조회 시 현재 참여 가능한 투표 수와 올해 생성된 투표 수를 반환한다")
    void getVoteHistoryReturnsJoinableAndThisYearVoteCounts() {
        when(voteRepository.countByVoteEndAtAfter(org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(2L);
        when(voteRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(18L);

        VoteHistoryResponse response = voteService.getVoteHistory();

        assertThat(response.joinableVotes()).isEqualTo(2L);
        assertThat(response.thisYearVotes()).isEqualTo(18L);
    }

    @Test
    @DisplayName("투표 히스토리 조회 시 올해 생성 건수 집계를 위해 올해 시작 시각과 다음 해 시작 시각을 사용한다")
    void getVoteHistoryUsesThisYearRange() {
        when(voteRepository.countByVoteEndAtAfter(org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(0L);
        when(voteRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(0L);

        voteService.getVoteHistory();

        ArgumentCaptor<LocalDateTime> voteEndAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(voteRepository).countByVoteEndAtAfter(voteEndAtCaptor.capture());
        verify(voteRepository).countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(startCaptor.capture(), endCaptor.capture());

        LocalDateTime now = voteEndAtCaptor.getValue();
        LocalDateTime startOfYear = startCaptor.getValue();
        LocalDateTime startOfNextYear = endCaptor.getValue();

        assertThat(startOfYear).isEqualTo(LocalDateTime.of(now.getYear(), 1, 1, 0, 0));
        assertThat(startOfNextYear).isEqualTo(LocalDateTime.of(now.getYear() + 1, 1, 1, 0, 0));
    }
}
