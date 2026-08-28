package com.ybc.ybc.vote.service;

import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class VoteGuestWaitlistResolver {
    private static final int WEEKDAY_GUEST_QUOTA = 3;
    private static final int WEEKEND_GUEST_QUOTA = 2;

    private VoteGuestWaitlistResolver() {
    }

    static ResolvedGuestList resolve(Vote baseVote,
                                     Collection<Vote> relatedVotes,
                                     Collection<VoteAttendance> confirmedAttendances,
                                     Collection<Guest> guests,
                                     LocalDateTime now) {
        List<Guest> orderedGuests = sortByGuestApplicationOrder(guests);
        if (baseVote.getActivityType() == ActivityType.FLASH) {
            List<ResolvedGuest> confirmedGuests = orderedGuests.stream()
                    .map(guest -> new ResolvedGuest(guest, false))
                    .toList();
            return new ResolvedGuestList(orderedGuests.size(), orderedGuests.size(), 0L, confirmedGuests);
        }

        if (baseVote.getActivityType() == ActivityType.EVENT) {
            List<ResolvedGuest> waitingGuests = orderedGuests.stream()
                    .map(guest -> new ResolvedGuest(guest, true))
                    .toList();
            return new ResolvedGuestList(0, 0L, orderedGuests.size(), waitingGuests);
        }

        int guestQuota = resolveRegularGuestQuota(baseVote, relatedVotes, confirmedAttendances, now);
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

    private static int resolveRegularGuestQuota(Vote baseVote,
                                                Collection<Vote> relatedVotes,
                                                Collection<VoteAttendance> confirmedAttendances,
                                                LocalDateTime now) {
        Map<Long, Long> confirmedMemberCountByVoteId = confirmedAttendances.stream()
                .collect(Collectors.groupingBy(
                        attendance -> attendance.getVote().getId(),
                        Collectors.counting()
                ));

        int baseGuestQuota = isWeekend(baseVote.getActivityDate().getDayOfWeek())
                ? WEEKEND_GUEST_QUOTA
                : WEEKDAY_GUEST_QUOTA;
        int closedVoteVacancyQuota = relatedVotes.stream()
                .filter(relatedVote -> !relatedVote.getVoteEndAt().isAfter(now))
                .mapToInt(relatedVote -> (int) Math.max(
                        (long) relatedVote.getCapacity() - confirmedMemberCountByVoteId.getOrDefault(relatedVote.getId(), 0L),
                        0L
                ))
                .sum();
        return baseGuestQuota + closedVoteVacancyQuota;
    }

    private static boolean isWeekend(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
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
