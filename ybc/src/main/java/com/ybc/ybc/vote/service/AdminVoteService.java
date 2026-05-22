package com.ybc.ybc.vote.service;

import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.dto.request.AdminVoteCreateRequest;
import com.ybc.ybc.vote.dto.response.AdminCompletedVoteResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteListItemResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteListResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteListSummaryResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteQueueItemResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminVoteService {
    private static final int ADDITIONAL_VOTE_TRIGGER_VACANCY = 3;
    private static final int ADDITIONAL_WEEKDAY_CAPACITY = 5;
    private static final int ADDITIONAL_WEEKEND_CAPACITY = 3;

    private final VoteRepository voteRepository;
    private final VoteAttendanceRepository voteAttendanceRepository;
    private final GuestRepository guestRepository;
    private final VoteMatchRepository voteMatchRepository;
    private final VoteMatchGroupRepository voteMatchGroupRepository;
    private final Clock clock;

    // 투표 예약 생성
    @Transactional
    public AdminVoteReservationResponse createVote(AdminVoteCreateRequest request) {
        validateVotePeriod(request.voteStartAt(), request.voteEndAt());

        Vote savedVote = voteRepository.saveAndFlush(
                Vote.builder()
                        .activityType(ActivityType.from(request.activityType()))
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

        return AdminVoteReservationResponse.from(savedVote);
    }

    // 투표 정보 수정
    @Transactional
    public AdminVoteListItemResponse updateVote(Long voteId, AdminVoteCreateRequest request) {
        validateVotePeriod(request.voteStartAt(), request.voteEndAt());

        Vote vote = getVote(voteId);

        vote.update(
                ActivityType.from(request.activityType()),
                request.title(),
                request.activityDate(),
                request.activityTime(),
                request.location(),
                request.memo(),
                request.voteStartAt(),
                request.voteEndAt(),
                request.capacity()
        );

        return toManagementItem(vote);
    }

    // 투표 예약 대기열 조회
    @Transactional
    public AdminVoteQueueResponse getVoteQueue() {
        reconcileAdditionalVotes();

        LocalDateTime now = LocalDateTime.now(clock);
        List<AdminVoteQueueItemResponse> queue = voteRepository.findAll(
                        Sort.by(
                                Sort.Order.asc("voteStartAt"),
                                Sort.Order.asc("id")
                        )
                ).stream()
                .filter(vote -> vote.getVoteStartAt().isAfter(now))
                .map(AdminVoteQueueItemResponse::from)
                .toList();

        return new AdminVoteQueueResponse(queue.size(), queue);
    }

    // 투표 현황 리스트 조회
    @Transactional
    public AdminVoteListResponse getVoteManagement() {
        reconcileAdditionalVotes();

        LocalDateTime now = LocalDateTime.now(clock);
        List<Vote> votes = voteRepository.findAll(
                Sort.by(Sort.Order.asc("id"))
        );
        VoteCountBundle voteCountBundle = buildVoteCountBundle(votes);

        List<AdminVoteListItemResponse> data = votes.stream()
                .map(vote -> {
                    AdminVoteStatus status = AdminVoteStatus.from(vote.getVoteStartAt(), vote.getVoteEndAt(), now);
                    return AdminVoteListItemResponse.from(
                            vote,
                            status,
                            voteCountBundle.attendeeCounts().getOrDefault(vote.getId(), 0L),
                            voteCountBundle.guestCounts().getOrDefault(vote.getId(), 0L)
                    );
                })
                .toList();

        return new AdminVoteListResponse(
                summarize(data),
                data
        );
    }

    // 투표 완료된 운동 리스트 조회
    @Transactional
    public List<AdminCompletedVoteResponse> getCompletedVotes() {
        reconcileAdditionalVotes();

        LocalDateTime now = LocalDateTime.now(clock);
        List<Vote> completedVotes = voteRepository.findAll(
                        Sort.by(
                                Sort.Order.desc("activityDate"),
                                Sort.Order.desc("id")
                        )
                ).stream()
                .filter(vote -> AdminVoteStatus.from(vote.getVoteStartAt(), vote.getVoteEndAt(), now) == AdminVoteStatus.COMPLETED)
                .toList();
        VoteCountBundle voteCountBundle = buildVoteCountBundle(completedVotes);

        return completedVotes.stream()
                .map(vote -> AdminCompletedVoteResponse.from(
                        vote,
                        voteCountBundle.attendeeCounts().getOrDefault(vote.getId(), 0L),
                        voteCountBundle.guestCounts().getOrDefault(vote.getId(), 0L)
                ))
                .toList();
    }

    // 투표 삭제
    @Transactional
    public void deleteVote(Long voteId) {
        Vote vote = getVote(voteId);
        Vote baseVote = resolveBaseVote(vote);
        List<Vote> relatedVotes = getRelatedVotes(baseVote);
        Set<Long> relatedVoteIds = getVoteIds(relatedVotes);

        voteMatchRepository.deleteAllByVote_IdIn(relatedVoteIds);
        voteMatchGroupRepository.deleteAllByVote_IdIn(relatedVoteIds);

        if (vote.getId().equals(baseVote.getId())) {
            relatedVotes.stream()
                    .filter(relatedVote -> !relatedVote.getId().equals(baseVote.getId()))
                    .forEach(this::deleteVoteContents);
            deleteVoteContents(baseVote);
            return;
        }

        deleteVoteContents(vote);
    }

    // 추가참여 투표 자동 생성 규칙
    @Transactional
    public void reconcileAdditionalVotes() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Vote> endedVotes = voteRepository.findAllByVoteEndAtLessThanEqualOrderByVoteEndAtAscIdAsc(now);
        if (endedVotes.isEmpty()) {
            return;
        }

        VoteCountBundle voteCountBundle = buildVoteCountBundle(endedVotes);
        List<Vote> additionalVotes = endedVotes.stream()
                .map(vote -> reconcileAdditionalVote(vote, voteCountBundle))
                .filter(Objects::nonNull)
                .toList();

        if (!additionalVotes.isEmpty()) {
            voteRepository.saveAll(additionalVotes);
        }
    }

    private Vote reconcileAdditionalVote(Vote vote, VoteCountBundle voteCountBundle) {
        if (vote.getActivityType() != ActivityType.REGULAR || vote.isAdditionalParticipationVote()) {
            return null;
        }

        long attendeeCount = voteCountBundle.attendeeCounts().getOrDefault(vote.getId(), 0L);
        long guestCount = voteCountBundle.guestCounts().getOrDefault(vote.getId(), 0L);
        long totalParticipants = attendeeCount + guestCount;
        int additionalCapacity = resolveAdditionalCapacity(vote);
        long threshold = Math.max(vote.getCapacity() - ADDITIONAL_VOTE_TRIGGER_VACANCY, 0);

        if (totalParticipants < threshold) {
            return null;
        }

        LocalDateTime additionalVoteStartAt = vote.getVoteEndAt();
        LocalDateTime additionalVoteEndAt = vote.getActivityDate().atTime(12, 0);
        if (!additionalVoteEndAt.isAfter(additionalVoteStartAt)) {
            return null;
        }

        if (voteRepository.existsByOriginalVote_Id(vote.getId())
                || voteRepository.existsByTitleAndActivityTypeAndActivityDateAndVoteStartAtAndVoteEndAt(
                vote.getTitle() + Vote.ADDITIONAL_PARTICIPATION_SUFFIX,
                ActivityType.REGULAR,
                vote.getActivityDate(),
                additionalVoteStartAt,
                additionalVoteEndAt
        )) {
            return null;
        }

        return Vote.createAdditionalVote(
                vote,
                additionalVoteStartAt,
                additionalVoteEndAt,
                additionalCapacity
        );
    }

    private VoteCountBundle buildVoteCountBundle(List<Vote> votes) {
        if (votes.isEmpty()) {
            return VoteCountBundle.empty();
        }

        List<Long> voteIds = votes.stream()
                .map(Vote::getId)
                .toList();

        Map<Long, Long> attendeeCounts = voteAttendanceRepository.countAttendancesByVoteIds(voteIds).stream()
                .collect(Collectors.toMap(VoteAttendanceCountProjection::voteId, VoteAttendanceCountProjection::attendeeCount));
        Map<Long, Long> guestCounts = guestRepository.countGuestsByVoteIds(voteIds).stream()
                .collect(Collectors.toMap(VoteGuestCountProjection::voteId, VoteGuestCountProjection::guestCount));

        return new VoteCountBundle(attendeeCounts, guestCounts);
    }

    private AdminVoteListItemResponse toManagementItem(Vote vote) {
        LocalDateTime now = LocalDateTime.now(clock);
        long attendeeCount = voteAttendanceRepository.countAttendancesByVoteId(vote.getId());
        long guestCount = guestRepository.countByVote_Id(vote.getId());
        AdminVoteStatus status = AdminVoteStatus.from(vote.getVoteStartAt(), vote.getVoteEndAt(), now);
        return AdminVoteListItemResponse.from(vote, status, attendeeCount, guestCount);
    }

    private AdminVoteListSummaryResponse summarize(List<AdminVoteListItemResponse> data) {
        return new AdminVoteListSummaryResponse(
                data.size(),
                data.stream().filter(item -> item.status() == AdminVoteStatus.UPCOMING).count(),
                data.stream().filter(item -> item.status() == AdminVoteStatus.IN_PROGRESS).count(),
                data.stream().filter(item -> item.status() == AdminVoteStatus.COMPLETED).count()
        );
    }

    private void validateVotePeriod(LocalDateTime voteStartAt, LocalDateTime voteEndAt) {
        if (!voteEndAt.isAfter(voteStartAt)) {
            throw new YbcException(ExceptionCode.INVALID_VOTE_PERIOD);
        }
    }

    private Vote getVote(Long voteId) {
        return voteRepository.findById(voteId)
                .orElseThrow(() -> new YbcException(ExceptionCode.VOTE_NOT_FOUND));
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
                .collect(Collectors.toMap(
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
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void deleteVoteContents(Vote vote) {
        guestRepository.deleteAllByVote_Id(vote.getId());
        voteAttendanceRepository.deleteAllByVote_Id(vote.getId());
        voteRepository.delete(vote);
    }

    private int resolveAdditionalCapacity(Vote vote) {
        DayOfWeek dayOfWeek = vote.getActivityDate().getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
                ? ADDITIONAL_WEEKEND_CAPACITY
                : ADDITIONAL_WEEKDAY_CAPACITY;
    }

    private record VoteCountBundle(
            Map<Long, Long> attendeeCounts,
            Map<Long, Long> guestCounts
    ) {
        private static VoteCountBundle empty() {
            return new VoteCountBundle(Map.of(), Map.of());
        }
    }
}
