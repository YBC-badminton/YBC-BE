package com.ybc.ybc.vote.service;

import com.ybc.ybc.vote.domain.AttendanceStatus;
import com.ybc.ybc.vote.dto.VoteHistoryResponse;
import com.ybc.ybc.vote.dto.VoteListResponse;
import com.ybc.ybc.vote.dto.VoteSummaryResponse;
import com.ybc.ybc.vote.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoteService {
    private final VoteRepository voteRepository;

    /**
     * 페이지 정보를 구성하고 joinable 조건에 맞는 투표 목록을 조회한 뒤 응답 DTO로 변환한다.
     */
    public VoteListResponse getVotes(boolean joinable, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "voteStartAt"));
        LocalDateTime now = LocalDateTime.now();
        Page<VoteSummaryResponse> votePage = joinable
                ? voteRepository.findJoinableVoteSummaries(now, AttendanceStatus.ATTEND, pageable)
                : voteRepository.findClosedVoteSummaries(now, AttendanceStatus.ATTEND, pageable);
        return VoteListResponse.from(votePage);
    }

    /**
     * 현재 참여 가능한 투표 수와 올해 생성된 투표 수를 집계하여 응답 DTO로 변환한다.
     */
    public VoteHistoryResponse getVoteHistory() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfYear = now.toLocalDate()
                .withDayOfYear(1)
                .atStartOfDay();
        LocalDateTime startOfNextYear = startOfYear.plusYears(1);

        long joinableVotes = voteRepository.countByVoteEndAtAfter(now);
        long thisYearVotes = voteRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(startOfYear, startOfNextYear);

        return new VoteHistoryResponse(joinableVotes, thisYearVotes);
    }
}
