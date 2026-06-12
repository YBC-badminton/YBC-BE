package com.ybc.ybc.vote.service;

import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class VoteGuestWaitlistResolver {
    private static final int TUESDAY_GUEST_QUOTA = 3;
    private static final int SATURDAY_GUEST_QUOTA = 2;

    private VoteGuestWaitlistResolver() {
    }

    static ResolvedGuestList resolve(Vote baseVote,
                                     Collection<Vote> relatedVotes,
                                     Collection<VoteAttendance> confirmedAttendances,
                                     Collection<Guest> guests,
                                     LocalDateTime now) {
        List<Guest> orderedGuests = sortByGuestApplicationOrder(guests);
        int guestQuota = resolveGuestQuota(baseVote, relatedVotes, confirmedAttendances, now);
        List<ResolvedGuest> resolvedGuests = orderedGuests.stream()
                .map(guest -> new ResolvedGuest(guest, true))
                .toList();
        if (orderedGuests.isEmpty()) {
            return new ResolvedGuestList(guestQuota, 0L, 0L, resolvedGuests);
        }

        List<ResolvedGuest> guestsWithWaiting = java.util.stream.IntStream.range(0, orderedGuests.size())
                .mapToObj(index -> new ResolvedGuest(
                        orderedGuests.get(index),
                        index >= guestQuota
                ))
                .toList();
        long confirmedGuestCount = Math.min(orderedGuests.size(), guestQuota);
        long waitingGuestCount = orderedGuests.size() - confirmedGuestCount;
        return new ResolvedGuestList(guestQuota, confirmedGuestCount, waitingGuestCount, guestsWithWaiting);
    }

    static List<Guest> resolveConfirmedGuests(Vote baseVote,
                                              Collection<Vote> relatedVotes,
                                              Collection<VoteAttendance> confirmedAttendances,
                                              Collection<Guest> guests,
                                              LocalDateTime now) {
        return resolve(baseVote, relatedVotes, confirmedAttendances, guests, now).guests().stream()
                .filter(resolvedGuest -> !resolvedGuest.waiting())
                .map(ResolvedGuest::guest)
                .toList();
    }

    static Set<Long> resolveConfirmedGuestIds(Vote baseVote,
                                              Collection<Vote> relatedVotes,
                                              Collection<VoteAttendance> confirmedAttendances,
                                              Collection<Guest> guests,
                                              LocalDateTime now) {
        return resolveConfirmedGuests(baseVote, relatedVotes, confirmedAttendances, guests, now).stream()
                .map(Guest::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static int resolveGuestQuota(Vote baseVote,
                                         Collection<Vote> relatedVotes,
                                         Collection<VoteAttendance> confirmedAttendances,
                                         LocalDateTime now) {
        Map<Long, Long> confirmedMemberCountByVoteId = confirmedAttendances.stream()
                .collect(Collectors.groupingBy(
                        attendance -> attendance.getVote().getId(),
                        Collectors.counting()
                ));

        if (baseVote.getActivityType() != ActivityType.REGULAR) {
            long confirmedMembers = confirmedMemberCountByVoteId.getOrDefault(baseVote.getId(), 0L);
            return (int) Math.max((long) baseVote.getCapacity() - confirmedMembers, 0L);
        }

        int baseGuestQuota = switch (baseVote.getActivityDate().getDayOfWeek()) {
            case TUESDAY -> TUESDAY_GUEST_QUOTA;
            case SATURDAY -> SATURDAY_GUEST_QUOTA;
            default -> 0;
        };
        int additionalGuestQuota = relatedVotes.stream()
                .filter(relatedVote -> !relatedVote.getId().equals(baseVote.getId()))
                .filter(relatedVote -> !relatedVote.getVoteEndAt().isAfter(now))
                .mapToInt(relatedVote -> (int) Math.max(
                        (long) relatedVote.getCapacity() - confirmedMemberCountByVoteId.getOrDefault(relatedVote.getId(), 0L),
                        0L
                ))
                .sum();
        return baseGuestQuota + additionalGuestQuota;
    }

    private static List<Guest> sortByGuestApplicationOrder(Collection<Guest> guests) {
        return guests.stream()
                .sorted(Comparator
                        .comparing(Guest::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(Guest::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    record ResolvedGuest(
            Guest guest,
            boolean waiting
    ) {
    }

    record ResolvedGuestList(
            int guestQuota,
            long confirmedGuestCount,
            long waitingGuestCount,
            List<ResolvedGuest> guests
    ) {
    }
}
