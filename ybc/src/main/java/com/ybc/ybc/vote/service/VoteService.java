package com.ybc.ybc.vote.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.MemberPrincipal;
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
import com.ybc.ybc.vote.dto.response.VoteAttendeeResponse;
import com.ybc.ybc.vote.dto.response.VoteDetailResponse;
import com.ybc.ybc.vote.dto.response.VoteGuestListResponse;
import com.ybc.ybc.vote.dto.response.VoteGuestSummaryResponse;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoteService {
    private static final long VOTE_PREVIEW_MINUTES = 30L;
    private static final int FLASH_VOTE_UNLIMITED_CAPACITY = 0;

    private final VoteRepository voteRepository;
    private final VoteAttendanceRepository voteAttendanceRepository;
    private final GuestRepository guestRepository;
    private final MemberService memberService;
    private final Clock clock;

    /**
     * 번개 모임 투표를 생성한다.
     */
    @Transactional
    public FlashVoteCreateResponse createFlashVote(FlashVoteCreateRequest request, MemberPrincipal memberPrincipal) {
        LocalDateTime voteStartAt = now();
        validateVotePeriod(voteStartAt, request.voteEndAt());

        Vote savedVote = voteRepository.saveAndFlush(
                Vote.builder()
                        .activityType(ActivityType.FLASH)
                        .title(request.title())
                        .activityDate(request.activityDate())
                        .activityTime(request.activityTime())
                        .location(request.location())
                        .memo(request.memo())
                        .voteStartAt(voteStartAt)
                        .voteEndAt(request.voteEndAt())
                        .capacity(FLASH_VOTE_UNLIMITED_CAPACITY)
                        .openedByMemberId(memberPrincipal.getId())
                        .openedByNickname(memberPrincipal.getNickname())
                        .build()
        );

        return FlashVoteCreateResponse.from(savedVote);
    }

    /**
     * 페이지 정보를 구성하고 open 조건에 맞는 투표 목록을 조회한 뒤 응답 DTO로 변환한다.
     */
    public VoteListResponse getVotes(boolean open, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "voteStartAt"));
        LocalDateTime now = now();
        Page<VoteSummaryResponse> votePage = open
                ? voteRepository.findOpenVoteSummaries(now, now.plusMinutes(VOTE_PREVIEW_MINUTES), pageable)
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
        Vote requestedVote = getVoteEntity(voteId);
        Vote baseVote = resolveBaseVote(requestedVote);
        List<Vote> relatedVotes = getRelatedVotes(baseVote);
        Set<Long> relatedVoteIds = getVoteIds(relatedVotes);
        VoteGuestWaitlistResolver.ResolvedGuestList guestList = VoteGuestWaitlistResolver.resolve(
                baseVote,
                relatedVotes,
                VoteAttendanceWaitlistResolver.resolveConfirmedAttendancesByVote(
                        voteAttendanceRepository.findAllActiveWithMemberByVoteIds(relatedVoteIds)
                ),
                guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(relatedVoteIds),
                now()
        );
        List<VoteGuestSummaryResponse> guests = guestList.guests().stream()
                .map(resolvedGuest -> VoteGuestSummaryResponse.from(
                        resolvedGuest.guest(),
                        resolvedGuest.waiting()
                ))
                .toList();
        return VoteGuestListResponse.from(
                guestList.confirmedGuestCount(),
                guestList.waitingGuestCount(),
                guestList.guestQuota(),
                guests
        );
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
        Vote vote = getVoteEntity(voteId);
        List<VoteAttendanceWaitlistResolver.ResolvedVoteAttendance> resolvedAttendances = VoteAttendanceWaitlistResolver.resolve(
                vote,
                voteAttendanceRepository.findAllActiveWithMemberByVoteId(voteId)
        );
        List<VoteAttendeeResponse> attendees = java.util.stream.IntStream.range(0, resolvedAttendances.size())
                .mapToObj(index -> {
                    VoteAttendanceWaitlistResolver.ResolvedVoteAttendance resolvedAttendance = resolvedAttendances.get(index);
                    return VoteAttendeeResponse.from(
                            index + 1,
                            resolvedAttendance.attendance(),
                            resolvedAttendance.waiting()
                    );
                })
                .toList();
        return VoteAttendeeListResponse.from(attendees);
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
        validateVoteIsOpenForParticipation(vote);
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
        validateGuestApplicationIsOpen(vote);
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
        validateGuestApplicationIsOpen(vote);
        Guest guest = guestRepository.findByIdAndVote_IdIn(guestId, getRelatedVoteIds(vote))
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
        Vote vote = getVoteEntityForUpdate(voteId);
        validateGuestApplicationIsOpen(vote);
        Guest guest = guestRepository.findByIdAndVote_IdIn(guestId, getRelatedVoteIds(vote))
                .orElseThrow(() -> new YbcException(ExceptionCode.GUEST_NOT_FOUND));

        if (!guest.getMember().getId().equals(memberId)) {
            throw new YbcException(ExceptionCode.GUEST_DELETE_FORBIDDEN);
        }

        guestRepository.delete(guest);
    }

    /**
     * 현재 진행 중인 투표 수와 마감된 일반 투표 수를 집계하여 응답 DTO로 변환한다.
     */
    public VoteHistoryResponse getVoteHistory() {
        LocalDateTime now = now();
        long joinableVotes = voteRepository.countByVoteStartAtLessThanEqualAndVoteEndAtAfter(now, now);
        long thisYearVotes = voteRepository.countCompletedVotesExcludingAdditional(
                now,
                Vote.ADDITIONAL_PARTICIPATION_SUFFIX
        );

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
        }
        voteAttendance.updateAttendanceStatus(attendanceStatus);
    }

    private void createVoteAttendance(Vote vote, Member member, boolean attendanceStatus) {
        if (attendanceStatus) {
            validateRelatedVoteAttendance(vote, member.getId());
        }

        voteAttendanceRepository.save(
                VoteAttendance.builder()
                        .vote(vote)
                        .member(member)
                        .attendanceStatus(attendanceStatus)
                        .build()
                );
    }

    private long countCurrentParticipantCount(Long voteId) {
        return voteAttendanceRepository.countAttendancesByVoteId(voteId);
    }

    private void validateVoteIsOpenForParticipation(Vote vote) {
        LocalDateTime now = now();
        if (vote.getVoteStartAt().isAfter(now)) {
            throw new YbcException(ExceptionCode.VOTE_NOT_OPEN);
        }
        if (!vote.getVoteEndAt().isAfter(now)) {
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

    private void validateGuestApplicationIsOpen(Vote vote) {
        LocalDateTime now = now();
        List<Vote> relatedVotes = getRelatedVotes(vote);
        boolean hasOpenVote = relatedVotes.stream()
                .anyMatch(relatedVote -> isOpenForParticipation(relatedVote, now));
        if (!hasOpenVote) {
            boolean hasUpcomingVote = relatedVotes.stream()
                    .anyMatch(relatedVote -> relatedVote.getVoteEndAt().isAfter(now));
            if (hasUpcomingVote) {
                throw new YbcException(ExceptionCode.VOTE_NOT_OPEN);
            }
            throw new YbcException(ExceptionCode.VOTE_CLOSED);
        }
    }

    private boolean isOpenForParticipation(Vote vote, LocalDateTime now) {
        return !vote.getVoteStartAt().isAfter(now) && vote.getVoteEndAt().isAfter(now);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private Set<Long> getRelatedVoteIds(Vote vote) {
        return getVoteIds(getRelatedVotes(vote));
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

    private Set<Long> getVoteIds(List<Vote> votes) {
        return votes.stream()
                .map(Vote::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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
