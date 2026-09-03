package com.ybc.ybc.vote.service;

import com.ybc.ybc.vote.dto.response.ActiveVoteSummaryResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteStatus;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.projection.ActiveVoteCandidateProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberActiveVoteService {
    private final VoteAttendanceRepository voteAttendanceRepository;
    private final Clock clock;

    public List<ActiveVoteSummaryResponse> getParticipatedActiveVotes(Long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return voteAttendanceRepository.findParticipatedVoteCandidates(memberId, now, now.toLocalDate()).stream()
                .filter(candidate -> isActive(candidate, now))
                .map(ActiveVoteSummaryResponse::from)
                .toList();
    }

    private boolean isActive(ActiveVoteCandidateProjection candidate, LocalDateTime now) {
        return AdminVoteStatus.from(
                candidate.voteStartAt(),
                candidate.voteEndAt(),
                candidate.activityDate(),
                candidate.activityTime(),
                now
        ) == AdminVoteStatus.ACTIVE;
    }
}
