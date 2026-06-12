package com.ybc.ybc.vote.service;

import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

final class VoteAttendanceWaitlistResolver {
    private VoteAttendanceWaitlistResolver() {
    }

    static List<ResolvedVoteAttendance> resolve(Vote vote,
                                                Collection<VoteAttendance> attendances,
                                                long guestCount) {
        List<VoteAttendance> orderedAttendances = sortByAttendanceOrder(attendances);
        int confirmedSlots = resolveConfirmedMemberSlots(vote, guestCount);
        List<ResolvedVoteAttendance> resolvedAttendances = new ArrayList<>(orderedAttendances.size());
        for (int index = 0; index < orderedAttendances.size(); index++) {
            resolvedAttendances.add(new ResolvedVoteAttendance(
                    orderedAttendances.get(index),
                    index >= confirmedSlots
            ));
        }
        return resolvedAttendances;
    }

    static List<VoteAttendance> resolveConfirmedAttendances(Vote vote,
                                                            Collection<VoteAttendance> attendances,
                                                            long guestCount) {
        return resolve(vote, attendances, guestCount).stream()
                .filter(resolvedAttendance -> !resolvedAttendance.waiting())
                .map(ResolvedVoteAttendance::attendance)
                .toList();
    }

    static List<VoteAttendance> resolveConfirmedAttendancesByVote(Collection<VoteAttendance> attendances,
                                                                  Map<Long, Long> guestCountByVoteId) {
        return attendances.stream()
                .collect(Collectors.groupingBy(
                        attendance -> attendance.getVote().getId()
                ))
                .values()
                .stream()
                .flatMap(voteAttendances -> resolveConfirmedAttendances(
                        voteAttendances.get(0).getVote(),
                        voteAttendances,
                        guestCountByVoteId.getOrDefault(voteAttendances.get(0).getVote().getId(), 0L)
                ).stream())
                .toList();
    }

    static Set<Long> resolveConfirmedAttendanceIds(Collection<VoteAttendance> attendances,
                                                   Map<Long, Vote> voteById,
                                                   Map<Long, Long> guestCountByVoteId) {
        return attendances.stream()
                .collect(Collectors.groupingBy(
                        attendance -> attendance.getVote().getId()
                ))
                .entrySet()
                .stream()
                .flatMap(entry -> resolveConfirmedAttendances(
                        Objects.requireNonNull(voteById.get(entry.getKey())),
                        entry.getValue(),
                        guestCountByVoteId.getOrDefault(entry.getKey(), 0L)
                ).stream())
                .map(VoteAttendance::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static List<VoteAttendance> sortByAttendanceOrder(Collection<VoteAttendance> attendances) {
        return attendances.stream()
                .sorted(Comparator
                        .comparing(VoteAttendanceWaitlistResolver::resolveAttendanceOrderAt)
                        .thenComparing(VoteAttendance::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private static int resolveConfirmedMemberSlots(Vote vote, long guestCount) {
        return (int) Math.max((long) vote.getCapacity() - guestCount, 0L);
    }

    private static LocalDateTime resolveAttendanceOrderAt(VoteAttendance attendance) {
        if (attendance.getAttendingAt() != null) {
            return attendance.getAttendingAt();
        }
        if (attendance.getCreatedAt() != null) {
            return attendance.getCreatedAt();
        }
        return LocalDateTime.MIN;
    }

    record ResolvedVoteAttendance(
            VoteAttendance attendance,
            boolean waiting
    ) {
    }
}
