package com.ybc.ybc.vote.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.dto.request.VoteAttendanceRequest;
import com.ybc.ybc.vote.dto.response.VoteDetailResponse;
import com.ybc.ybc.vote.dto.response.VoteHistoryResponse;
import com.ybc.ybc.vote.dto.response.VoteListResponse;
import com.ybc.ybc.vote.dto.response.VoteSummaryResponse;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
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
    private final VoteAttendanceRepository voteAttendanceRepository;
    private final MemberService memberService;

    /**
     * 페이지 정보를 구성하고 joinable 조건에 맞는 투표 목록을 조회한 뒤 응답 DTO로 변환한다.
     */
    public VoteListResponse getVotes(boolean joinable, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "voteStartAt"));
        LocalDateTime now = LocalDateTime.now();
        Page<VoteSummaryResponse> votePage = joinable
                ? voteRepository.findJoinableVoteSummaries(now, pageable)
                : voteRepository.findClosedVoteSummaries(now, pageable);
        return VoteListResponse.from(votePage);
    }

    /**
     * 투표 식별자에 해당하는 상세 정보와 현재 참석 인원 수를 조회하여 반환한다.
     */
    public VoteDetailResponse getVote(Long voteId) {
        Vote vote = getVoteEntity(voteId);
        long currentParticipantCount = voteAttendanceRepository.countAttendancesByVoteId(voteId);
        return VoteDetailResponse.from(vote, currentParticipantCount);
    }

    /**
     * 현재 로그인한 회원의 투표 참석 여부를 생성하거나 수정한다.
     */
    @Transactional
    public void saveVoteAttendance(Long voteId, Long memberId, VoteAttendanceRequest request) {
        Vote vote = getVoteEntity(voteId);
        Member member = memberService.getMemberById(memberId);

        voteAttendanceRepository.findByVote_IdAndMember_Id(voteId, memberId)
                .ifPresentOrElse(
                        voteAttendance -> updateVoteAttendance(vote, voteAttendance, request.attendanceStatus()),
                        () -> createVoteAttendance(vote, member, request.attendanceStatus())
                );
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

    private Vote getVoteEntity(Long voteId) {
        return voteRepository.findById(voteId)
                .orElseThrow(() -> new YbcException(ExceptionCode.VOTE_NOT_FOUND));
    }

    private void updateVoteAttendance(Vote vote, VoteAttendance voteAttendance, boolean attendanceStatus) {
        if (!voteAttendance.isAttendanceStatus() && attendanceStatus) {
            validateCapacity(vote);
        }
        voteAttendance.updateAttendanceStatus(attendanceStatus);
    }

    private void createVoteAttendance(Vote vote, Member member, boolean attendanceStatus) {
        if (attendanceStatus) {
            validateCapacity(vote);
        }

        voteAttendanceRepository.save(
                VoteAttendance.builder()
                        .vote(vote)
                        .member(member)
                        .attendanceStatus(attendanceStatus)
                        .build()
        );
    }

    private void validateCapacity(Vote vote) {
        long currentParticipantCount = voteAttendanceRepository.countAttendancesByVoteId(vote.getId());
        if (currentParticipantCount >= vote.getCapacity()) {
            throw new YbcException(ExceptionCode.SESSION_FULL);
        }
    }
}
