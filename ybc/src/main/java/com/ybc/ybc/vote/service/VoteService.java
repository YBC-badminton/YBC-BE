package com.ybc.ybc.vote.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.dto.request.FlashVoteCreateRequest;
import com.ybc.ybc.vote.dto.request.VoteAttendanceRequest;
import com.ybc.ybc.vote.dto.request.VoteGuestRequest;
import com.ybc.ybc.vote.dto.response.FlashVoteCreateResponse;
import com.ybc.ybc.vote.dto.response.RecentVoteListResponse;
import com.ybc.ybc.vote.dto.response.RecentVoteResponse;
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
import com.ybc.ybc.vote.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoteService {
    private final VoteRepository voteRepository;
    private final VoteAttendanceRepository voteAttendanceRepository;
    private final GuestRepository guestRepository;
    private final MemberService memberService;

    /**
     * 번개 모임 투표를 생성한다.
     */
    @Transactional
    public FlashVoteCreateResponse createFlashVote(FlashVoteCreateRequest request) {
        validateVotePeriod(request.voteStartAt(), request.voteEndAt());

        Vote savedVote = voteRepository.saveAndFlush(
                Vote.builder()
                        .activityType(ActivityType.FLASH)
                        .title(request.title())
                        .activityDate(request.activityDate())
                        .activityTime(request.activityTime())
                        .location(request.location())
                        .memo(request.memo())
                        .voteStartAt(request.voteStartAt())
                        .voteEndAt(request.voteEndAt())
                        .capacity(request.capacity())
                        .build()
        );

        return FlashVoteCreateResponse.from(savedVote);
    }

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
     * 최근 생성된 투표 3개를 조회한다. 비로그인 사용자는 참여 인원 수를 숨긴다.
     */
    public RecentVoteListResponse getRecentVotes(boolean participantCountVisible) {
        List<RecentVoteResponse> votes = voteRepository.findTop3ByOrderByCreatedAtDescIdDesc().stream()
                .map(vote -> RecentVoteResponse.from(
                        vote,
                        participantCountVisible ? countCurrentParticipantCount(vote.getId()) : null,
                        participantCountVisible
                ))
                .toList();
        return RecentVoteListResponse.from(votes);
    }

    /**
     * 투표 식별자에 해당하는 상세 정보와 현재 참석 인원 수를 조회하여 반환한다.
     */
    public VoteDetailResponse getVote(Long voteId) {
        Vote vote = getVoteEntity(voteId);
        long currentParticipantCount = countCurrentParticipantCount(voteId);
        return VoteDetailResponse.from(vote, currentParticipantCount);
    }

    /**
     * 특정 투표에 참여한 게스트 수와 게스트 상세 목록을 조회하여 반환한다.
     */
    public VoteGuestListResponse getVoteGuests(Long voteId) {
        getVoteEntity(voteId);
        return VoteGuestListResponse.from(guestRepository.findAllByVote_IdOrderByIdAsc(voteId));
    }

    /**
     * 현재 로그인한 회원의 투표 참석 여부를 조회하여 반환한다.
     */
    public VoteAttendanceStatusResponse getVoteAttendance(Long voteId, Long memberId) {
        getVoteEntity(voteId);
        Boolean attendanceStatus = voteAttendanceRepository.findByVote_IdAndMember_Id(voteId, memberId)
                .map(VoteAttendance::isAttendanceStatus)
                .orElse(null);
        return VoteAttendanceStatusResponse.from(attendanceStatus);
    }

    /**
     * 특정 투표에서 참석 상태가 true인 회원 목록과 총 참석 인원 수를 조회하여 반환한다.
     */
    public VoteAttendeeListResponse getVoteAttendees(Long voteId) {
        getVoteEntity(voteId);
        return VoteAttendeeListResponse.from(
                voteAttendanceRepository.findAllByVote_IdAndAttendanceStatusTrueOrderByIdAsc(voteId)
        );
    }

    /**
     * 특정 투표에서 참석 상태가 false인 회원 목록과 총 인원 수를 조회하여 반환한다.
     */
    public VoteAbsenteeListResponse getVoteAbsentees(Long voteId) {
        getVoteEntity(voteId);
        return VoteAbsenteeListResponse.from(
                voteAttendanceRepository.findAllByVote_IdAndAttendanceStatusFalseOrderByIdAsc(voteId)
        );
    }

    /**
     * 현재 로그인한 회원의 투표 참석 여부를 생성하거나 수정한다.
     */
    @Transactional
    public void saveVoteAttendance(Long voteId, Long memberId, VoteAttendanceRequest request) {
        Vote vote = getVoteEntityForUpdate(voteId);
        validateVoteIsJoinable(vote);
        Member member = memberService.getMemberById(memberId);

        voteAttendanceRepository.findByVote_IdAndMember_Id(voteId, memberId)
                .ifPresentOrElse(
                        voteAttendance -> updateVoteAttendance(vote, voteAttendance, request.attendanceStatus()),
                        () -> createVoteAttendance(vote, member, request.attendanceStatus())
                );
    }

    /**
     * 현재 로그인한 회원 소속의 게스트 참여 정보를 저장한다.
     */
    @Transactional
    public void createGuest(Long voteId, Long memberId, VoteGuestRequest request) {
        Vote vote = getVoteEntityForUpdate(voteId);
        validateVoteIsJoinable(vote);
        validateCapacity(vote);
        Member member = memberService.getMemberById(memberId);

        guestRepository.save(
                Guest.builder()
                        .vote(vote)
                        .member(member)
                        .name(request.name())
                        .gender(request.gender())
                        .level(request.level())
                        .build()
        );
    }

    /**
     * 현재 로그인한 회원이 본인이 추가한 게스트 참여 정보를 수정한다.
     */
    @Transactional
    public void updateGuest(Long voteId, Long guestId, Long memberId, VoteGuestRequest request) {
        Vote vote = getVoteEntityForUpdate(voteId);
        validateVoteIsJoinable(vote);
        Guest guest = guestRepository.findByIdAndVote_Id(guestId, voteId)
                .orElseThrow(() -> new YbcException(ExceptionCode.GUEST_NOT_FOUND));

        if (!guest.getMember().getId().equals(memberId)) {
            throw new YbcException(ExceptionCode.GUEST_UPDATE_FORBIDDEN);
        }

        guest.update(request.name(), request.gender(), request.level());
    }

    /**
     * 현재 로그인한 회원이 본인이 추가한 게스트만 삭제한다.
     */
    @Transactional
    public void deleteGuest(Long voteId, Long guestId, Long memberId) {
        getVoteEntity(voteId);
        Guest guest = guestRepository.findByIdAndVote_Id(guestId, voteId)
                .orElseThrow(() -> new YbcException(ExceptionCode.GUEST_NOT_FOUND));

        if (!guest.getMember().getId().equals(memberId)) {
            throw new YbcException(ExceptionCode.GUEST_DELETE_FORBIDDEN);
        }

        guestRepository.delete(guest);
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

    private Vote getVoteEntityForUpdate(Long voteId) {
        return voteRepository.findByIdForUpdate(voteId)
                .orElseThrow(() -> new YbcException(ExceptionCode.VOTE_NOT_FOUND));
    }

    private void updateVoteAttendance(Vote vote, VoteAttendance voteAttendance, boolean attendanceStatus) {
        if (!voteAttendance.isAttendanceStatus() && attendanceStatus) {
            validateRelatedVoteAttendance(vote, voteAttendance.getMember().getId());
            validateCapacity(vote);
        }
        voteAttendance.updateAttendanceStatus(attendanceStatus);
    }

    private void createVoteAttendance(Vote vote, Member member, boolean attendanceStatus) {
        if (attendanceStatus) {
            validateRelatedVoteAttendance(vote, member.getId());
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
        long currentParticipantCount = countCurrentParticipantCount(vote.getId());
        if (currentParticipantCount >= vote.getCapacity()) {
            throw new YbcException(ExceptionCode.SESSION_FULL);
        }
    }

    private long countCurrentParticipantCount(Long voteId) {
        return voteAttendanceRepository.countAttendancesByVoteId(voteId);
    }

    private void validateVoteIsJoinable(Vote vote) {
        if (!vote.getVoteEndAt().isAfter(LocalDateTime.now())) {
            throw new YbcException(ExceptionCode.VOTE_CLOSED);
        }
    }

    private void validateRelatedVoteAttendance(Vote vote, Long memberId) {
        Set<Long> relatedVoteIds = getRelatedVoteIds(vote);
        if (relatedVoteIds.size() <= 1) {
            return;
        }

        if (voteAttendanceRepository.existsByVote_IdInAndMember_IdAndAttendanceStatusTrue(relatedVoteIds, memberId)) {
            throw new YbcException(ExceptionCode.DUPLICATE_VOTE_ATTENDANCE);
        }
    }

    private Set<Long> getRelatedVoteIds(Vote vote) {
        return getRelatedVotes(vote).stream()
                .map(Vote::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Vote> getRelatedVotes(Vote vote) {
        Vote baseVote = resolveBaseVote(vote);
        List<Vote> relatedVotes = new ArrayList<>();
        relatedVotes.add(baseVote);
        relatedVotes.addAll(voteRepository.findAllByOriginalVote_IdOrderByIdAsc(baseVote.getId()));

        voteRepository.findAllByActivityTypeAndActivityDateAndActivityTimeAndLocationAndVoteStartAtAndTitleAndOriginalVoteIsNullOrderByIdAsc(
                        ActivityType.REGULAR,
                        baseVote.getActivityDate(),
                        baseVote.getActivityTime(),
                        baseVote.getLocation(),
                        baseVote.getVoteEndAt(),
                        baseVote.getTitle() + Vote.ADDITIONAL_PARTICIPATION_SUFFIX
                ).stream()
                .filter(legacyAdditionalVote -> !legacyAdditionalVote.getId().equals(baseVote.getId()))
                .forEach(legacyAdditionalVote -> {
                    legacyAdditionalVote.linkOriginalVote(baseVote);
                    relatedVotes.add(legacyAdditionalVote);
                });

        return relatedVotes.stream()
                .collect(java.util.stream.Collectors.toMap(
                        Vote::getId,
                        voteItem -> voteItem,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private void validateVotePeriod(LocalDateTime voteStartAt, LocalDateTime voteEndAt) {
        if (!voteEndAt.isAfter(voteStartAt)) {
            throw new YbcException(ExceptionCode.INVALID_VOTE_PERIOD);
        }
    }

    private Vote resolveBaseVote(Vote vote) {
        if (vote.getOriginalVote() != null) {
            return vote.getOriginalVote();
        }
        if (!vote.isAdditionalParticipationVote()) {
            return vote;
        }

        return voteRepository.findFirstByTitleAndActivityTypeAndActivityDateAndActivityTimeAndLocationAndVoteEndAtAndOriginalVoteIsNullOrderByIdAsc(
                        vote.getBaseVoteTitle(),
                        ActivityType.REGULAR,
                        vote.getActivityDate(),
                        vote.getActivityTime(),
                        vote.getLocation(),
                        vote.getVoteStartAt()
                )
                .map(baseVote -> {
                    vote.linkOriginalVote(baseVote);
                    return baseVote;
                })
                .orElse(vote);
    }
}
