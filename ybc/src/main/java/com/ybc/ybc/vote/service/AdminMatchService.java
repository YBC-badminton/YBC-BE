package com.ybc.ybc.vote.service;

import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import com.ybc.ybc.members.service.ClubMemberSyncService;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.domain.MatchParticipantType;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.domain.VoteMatch;
import com.ybc.ybc.vote.domain.VoteMatchGroup;
import com.ybc.ybc.vote.dto.request.AdminMatchCourtRequest;
import com.ybc.ybc.vote.dto.request.AdminMatchGameRequest;
import com.ybc.ybc.vote.dto.request.AdminMatchParticipantRequest;
import com.ybc.ybc.vote.dto.response.AdminMatchCourtResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchGameResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchParticipantListResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchParticipantProfileResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchParticipantResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchRegistrationResponse;
import com.ybc.ybc.vote.repository.GuestRepository;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.VoteMatchGroupRepository;
import com.ybc.ybc.vote.repository.VoteMatchRepository;
import com.ybc.ybc.vote.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminMatchService {
    private static final int TEAM_SIZE = 2;
    private static final int TEAM_ONE = 1;
    private static final int TEAM_TWO = 2;

    private final VoteRepository voteRepository;
    private final VoteAttendanceRepository voteAttendanceRepository;
    private final GuestRepository guestRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubMemberSyncService clubMemberSyncService;
    private final VoteMatchRepository voteMatchRepository;
    private final VoteMatchGroupRepository voteMatchGroupRepository;

    @Transactional(readOnly = true)
    public AdminMatchRegistrationResponse getMatch(Long matchId) {
        VoteMatchGroup matchGroup = getMatchGroup(matchId);
        return toMatchResponse(matchGroup);
    }

    @Transactional
    public AdminMatchRegistrationResponse updateMatch(Long matchId, List<AdminMatchCourtRequest> request) {
        if (request == null || request.isEmpty()) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }

        clubMemberSyncService.reconcileFinalPassMembers();
        VoteMatchGroup matchGroup = getMatchGroup(matchId);
        Vote baseVote = resolveBaseVote(matchGroup.getVote());
        Set<Long> relatedVoteIds = getRelatedVoteIds(baseVote);
        validateRequestedGames(request);
        Map<ParticipantKey, MatchParticipantSource> participantMap = getParticipantMap(relatedVoteIds, request);

        getRequestedGameKeys(request).forEach(gameKey ->
                voteMatchRepository.deleteAllByMatchGroup_IdAndCourtNumberAndMatchNumber(
                        matchGroup.getId(),
                        gameKey.courtNumber(),
                        gameKey.matchNumber()
                )
        );
        voteMatchRepository.flush();
        voteMatchRepository.saveAllAndFlush(
                toMatchEntities(matchGroup.getVote(), matchGroup, request, participantMap)
        );

        return toMatchResponse(matchGroup);
    }

    @Transactional
    public AdminMatchParticipantListResponse getParticipants(Long voteId) {
        clubMemberSyncService.reconcileFinalPassMembers();
        Vote requestedVote = getVote(voteId);
        Vote baseVote = resolveBaseVote(requestedVote);
        Set<Long> relatedVoteIds = getRelatedVoteIds(baseVote);
        List<VoteAttendance> attendances = deduplicateAttendances(
                voteAttendanceRepository.findAllActiveWithMemberByVoteIds(relatedVoteIds)
        );
        List<Guest> guests = guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(relatedVoteIds);
        Map<Long, ClubMember> clubMembersByMemberId = getClubMembersByMemberId(attendances);

        List<AdminMatchParticipantResponse> participants = new ArrayList<>();
        attendances.forEach(attendance -> {
            AdminMatchParticipantProfileResponse profile = toMemberProfile(attendance, clubMembersByMemberId);
            participants.add(new AdminMatchParticipantResponse(
                    attendance.getId(),
                    MatchParticipantType.MEMBER.name(),
                    profile.name(),
                    profile.gender()
            ));
        });
        guests.forEach(guest -> participants.add(new AdminMatchParticipantResponse(
                guest.getId(),
                MatchParticipantType.GUEST.name(),
                guest.getName(),
                guest.getGender()
        )));

        return new AdminMatchParticipantListResponse(baseVote.getId(), participants.size(), participants);
    }

    @Transactional
    public AdminMatchRegistrationResponse registerMatches(Long voteId, List<AdminMatchCourtRequest> request) {
        if (request == null || request.isEmpty()) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }

        clubMemberSyncService.reconcileFinalPassMembers();
        Vote requestedVote = getVote(voteId);
        Vote baseVote = resolveBaseVote(requestedVote);
        Set<Long> relatedVoteIds = getRelatedVoteIds(baseVote);
        validateRequestedGames(request);
        Map<ParticipantKey, MatchParticipantSource> participantMap = getParticipantMap(relatedVoteIds, request);

        voteMatchRepository.deleteAllByVote_IdIn(relatedVoteIds);
        Set<Long> additionalVoteIds = relatedVoteIds.stream()
                .filter(relatedVoteId -> !relatedVoteId.equals(baseVote.getId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!additionalVoteIds.isEmpty()) {
            voteMatchGroupRepository.deleteAllByVote_IdIn(additionalVoteIds);
        }
        VoteMatchGroup matchGroup = voteMatchGroupRepository.findByVote_Id(baseVote.getId())
                .orElseGet(() -> voteMatchGroupRepository.saveAndFlush(VoteMatchGroup.create(baseVote)));
        voteMatchRepository.saveAllAndFlush(
                toMatchEntities(baseVote, matchGroup, request, participantMap)
        );

        return toMatchResponse(matchGroup);
    }

    private VoteMatchGroup getMatchGroup(Long matchId) {
        return voteMatchGroupRepository.findById(matchId)
                .orElseThrow(() -> new YbcException(ExceptionCode.MATCH_NOT_FOUND));
    }

    private Vote getVote(Long voteId) {
        return voteRepository.findById(voteId)
                .orElseThrow(() -> new YbcException(ExceptionCode.VOTE_NOT_FOUND));
    }

    private Map<ParticipantKey, MatchParticipantSource> getParticipantMap(Set<Long> relatedVoteIds, List<AdminMatchCourtRequest> request) {
        Set<ParticipantKey> participantKeys = request.stream()
                .flatMap(court -> court.courtMatches().stream())
                .flatMap(match -> Stream.concat(match.team1().stream(), match.team2().stream()))
                .map(ParticipantKey::from)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (participantKeys.isEmpty()) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }

        Set<Long> attendanceIds = filterParticipantIds(participantKeys, MatchParticipantType.MEMBER);
        Set<Long> guestIds = filterParticipantIds(participantKeys, MatchParticipantType.GUEST);
        Map<Long, VoteAttendance> attendanceMap = getAttendanceMap(relatedVoteIds, attendanceIds);
        Map<Long, Guest> guestMap = getGuestMap(relatedVoteIds, guestIds);

        Map<ParticipantKey, MatchParticipantSource> participantMap = new LinkedHashMap<>();
        participantKeys.forEach(participantKey -> {
            if (participantKey.participantType() == MatchParticipantType.MEMBER) {
                participantMap.put(participantKey, MatchParticipantSource.member(attendanceMap.get(participantKey.participantId())));
                return;
            }
            participantMap.put(participantKey, MatchParticipantSource.guest(guestMap.get(participantKey.participantId())));
        });
        return participantMap;
    }

    private Set<Long> filterParticipantIds(Set<ParticipantKey> participantKeys, MatchParticipantType participantType) {
        return participantKeys.stream()
                .filter(participantKey -> participantKey.participantType() == participantType)
                .map(ParticipantKey::participantId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<Long, VoteAttendance> getAttendanceMap(Set<Long> relatedVoteIds, Collection<Long> attendanceIds) {
        if (attendanceIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, VoteAttendance> attendanceMap = voteAttendanceRepository.findAllWithVoteAndMemberByIdIn(attendanceIds)
                .stream()
                .collect(Collectors.toMap(VoteAttendance::getId, Function.identity()));
        if (attendanceMap.size() != attendanceIds.size()) {
            throw new YbcException(ExceptionCode.MATCH_PARTICIPANT_NOT_FOUND);
        }

        boolean hasInvalidParticipant = attendanceMap.values().stream()
                .anyMatch(attendance -> !attendance.isAttendanceStatus()
                        || !relatedVoteIds.contains(attendance.getVote().getId()));
        if (hasInvalidParticipant) {
            throw new YbcException(ExceptionCode.MATCH_PARTICIPANT_NOT_FOUND);
        }

        return attendanceMap;
    }

    private Map<Long, Guest> getGuestMap(Set<Long> relatedVoteIds, Collection<Long> guestIds) {
        if (guestIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Guest> guestMap = guestRepository.findAllWithVoteByIdIn(guestIds).stream()
                .collect(Collectors.toMap(Guest::getId, Function.identity()));
        if (guestMap.size() != guestIds.size()) {
            throw new YbcException(ExceptionCode.MATCH_PARTICIPANT_NOT_FOUND);
        }

        boolean hasInvalidParticipant = guestMap.values().stream()
                .anyMatch(guest -> !relatedVoteIds.contains(guest.getVote().getId()));
        if (hasInvalidParticipant) {
            throw new YbcException(ExceptionCode.MATCH_PARTICIPANT_NOT_FOUND);
        }

        return guestMap;
    }

    private void validateRequestedGames(List<AdminMatchCourtRequest> request) {
        Set<GameKey> gameKeys = new LinkedHashSet<>();
        for (AdminMatchCourtRequest court : request) {
            for (AdminMatchGameRequest game : court.courtMatches()) {
                validateGame(game);
                boolean added = gameKeys.add(new GameKey(court.courtNumber(), game.matchNumber()));
                if (!added) {
                    throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
                }
            }
        }
    }

    private Set<GameKey> getRequestedGameKeys(List<AdminMatchCourtRequest> request) {
        return request.stream()
                .flatMap(court -> court.courtMatches().stream()
                        .map(game -> new GameKey(court.courtNumber(), game.matchNumber())))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<VoteMatch> toMatchEntities(Vote vote,
                                            VoteMatchGroup matchGroup,
                                            List<AdminMatchCourtRequest> request,
                                            Map<ParticipantKey, MatchParticipantSource> participantMap) {
        List<VoteMatch> matches = new ArrayList<>();
        for (AdminMatchCourtRequest court : request) {
            for (AdminMatchGameRequest game : court.courtMatches()) {
                validateGame(game);
                addTeam(matches, vote, matchGroup, participantMap, court.courtNumber(), game.matchNumber(), TEAM_ONE, game.team1());
                addTeam(matches, vote, matchGroup, participantMap, court.courtNumber(), game.matchNumber(), TEAM_TWO, game.team2());
            }
        }
        return matches;
    }

    private void addTeam(List<VoteMatch> matches,
                         Vote vote,
                         VoteMatchGroup matchGroup,
                         Map<ParticipantKey, MatchParticipantSource> participantMap,
                         Integer courtNumber,
                         Integer matchNumber,
                         int teamNumber,
                         List<AdminMatchParticipantRequest> participants) {
        for (AdminMatchParticipantRequest participantRequest : participants) {
            MatchParticipantSource participant = participantMap.get(ParticipantKey.from(participantRequest));
            matches.add(VoteMatch.builder()
                    .vote(vote)
                    .matchGroup(matchGroup)
                    .participantType(participant.participantType())
                    .voteAttendance(participant.voteAttendance())
                    .guest(participant.guest())
                    .courtNumber(courtNumber)
                    .matchNumber(matchNumber)
                    .teamNumber(teamNumber)
                    .build());
        }
    }

    private AdminMatchRegistrationResponse toMatchResponse(VoteMatchGroup matchGroup) {
        List<VoteMatch> matches = voteMatchRepository.findAllWithParticipantsByMatchGroupId(matchGroup.getId());
        Map<Long, ClubMember> clubMembersByMemberId = getClubMembersByMemberId(
                matches.stream()
                        .map(VoteMatch::getVoteAttendance)
                        .filter(Objects::nonNull)
                        .toList()
        );
        List<AdminMatchCourtResponse> courtResponses = toCourtResponsesFromMatches(matches, clubMembersByMemberId);
        return new AdminMatchRegistrationResponse(
                matchGroup.getVote().getId(),
                matchGroup.getId(),
                courtResponses.size(),
                courtResponses
        );
    }

    private List<AdminMatchCourtResponse> toCourtResponsesFromMatches(List<VoteMatch> matches,
                                                                      Map<Long, ClubMember> clubMembersByMemberId) {
        Map<Integer, List<VoteMatch>> matchesByCourt = matches.stream()
                .collect(Collectors.groupingBy(
                        VoteMatch::getCourtNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return matchesByCourt.entrySet().stream()
                .map(courtEntry -> new AdminMatchCourtResponse(
                        courtEntry.getKey(),
                        toGameResponsesFromMatches(courtEntry.getValue(), clubMembersByMemberId)
                ))
                .toList();
    }

    private List<AdminMatchGameResponse> toGameResponsesFromMatches(List<VoteMatch> matches,
                                                                    Map<Long, ClubMember> clubMembersByMemberId) {
        Map<Integer, List<VoteMatch>> matchesByGame = matches.stream()
                .collect(Collectors.groupingBy(
                        VoteMatch::getMatchNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return matchesByGame.entrySet().stream()
                .map(gameEntry -> new AdminMatchGameResponse(
                        gameEntry.getKey(),
                        toParticipantProfilesFromMatches(gameEntry.getValue(), TEAM_ONE, clubMembersByMemberId),
                        toParticipantProfilesFromMatches(gameEntry.getValue(), TEAM_TWO, clubMembersByMemberId)
                ))
                .toList();
    }

    private List<AdminMatchParticipantProfileResponse> toParticipantProfilesFromMatches(List<VoteMatch> matches,
                                                                                        int teamNumber,
                                                                                        Map<Long, ClubMember> clubMembersByMemberId) {
        return matches.stream()
                .filter(match -> match.getTeamNumber() == teamNumber)
                .map(match -> toParticipantProfile(match, clubMembersByMemberId))
                .toList();
    }

    private AdminMatchParticipantProfileResponse toParticipantProfile(VoteMatch match,
                                                                      Map<Long, ClubMember> clubMembersByMemberId) {
        if (match.getParticipantType() == MatchParticipantType.GUEST) {
            Guest guest = match.getGuest();
            return new AdminMatchParticipantProfileResponse(guest.getName(), guest.getGender());
        }
        return toMemberProfile(match.getVoteAttendance(), clubMembersByMemberId);
    }

    private AdminMatchParticipantProfileResponse toMemberProfile(VoteAttendance attendance,
                                                                 Map<Long, ClubMember> clubMembersByMemberId) {
        ClubMember clubMember = clubMembersByMemberId.get(attendance.getMember().getId());
        if (clubMember != null) {
            return new AdminMatchParticipantProfileResponse(clubMember.getName(), clubMember.getGender());
        }
        return new AdminMatchParticipantProfileResponse(attendance.getMember().getNickname(), null);
    }

    private Map<Long, ClubMember> getClubMembersByMemberId(Iterable<VoteAttendance> attendances) {
        Set<Long> memberIds = new LinkedHashSet<>();
        Map<Long, String> emailsByMemberId = new LinkedHashMap<>();
        attendances.forEach(attendance -> {
            String email = normalizeEmail(attendance.getMember().getEmail());
            Long memberId = attendance.getMember().getId();
            memberIds.add(memberId);
            emailsByMemberId.put(memberId, email);
        });
        if (memberIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ClubMember> clubMembersByMemberId = clubMemberRepository.findAllByMemberIdInOrderByIdAsc(memberIds).stream()
                .filter(clubMember -> clubMember.getMemberId() != null)
                .collect(Collectors.toMap(
                        ClubMember::getMemberId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        Set<Long> unresolvedMemberIds = memberIds.stream()
                .filter(memberId -> !clubMembersByMemberId.containsKey(memberId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unresolvedMemberIds.isEmpty()) {
            return clubMembersByMemberId;
        }

        Set<String> unresolvedEmails = unresolvedMemberIds.stream()
                .map(emailsByMemberId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unresolvedEmails.isEmpty()) {
            return clubMembersByMemberId;
        }

        Map<String, List<ClubMember>> clubMembersByEmail = clubMemberRepository.findAllByNormalizedEmailInOrderByIdAsc(unresolvedEmails)
                .stream()
                .filter(clubMember -> normalizeEmail(clubMember.getEmail()) != null)
                .collect(Collectors.groupingBy(
                        clubMember -> Objects.requireNonNull(normalizeEmail(clubMember.getEmail())),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        unresolvedMemberIds.forEach(memberId -> {
            String email = emailsByMemberId.get(memberId);
            if (email == null) {
                return;
            }

            ClubMember linkedClubMember = clubMembersByEmail.getOrDefault(email, List.of())
                    .stream()
                    .filter(clubMember -> clubMember.getMemberId() == null || clubMember.getMemberId().equals(memberId))
                    .findFirst()
                    .orElse(null);
            if (linkedClubMember == null) {
                return;
            }

            linkedClubMember.linkMember(memberId);
            clubMembersByMemberId.put(memberId, linkedClubMember);
        });

        return clubMembersByMemberId;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
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

    private Set<Long> getRelatedVoteIds(Vote vote) {
        return getRelatedVotes(vote).stream()
                .map(Vote::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private List<VoteAttendance> deduplicateAttendances(List<VoteAttendance> attendances) {
        Map<Long, VoteAttendance> uniqueAttendancesByMemberId = new LinkedHashMap<>();
        attendances.forEach(attendance -> uniqueAttendancesByMemberId.putIfAbsent(attendance.getMember().getId(), attendance));
        return uniqueAttendancesByMemberId.values().stream().toList();
    }

    private void validateGame(AdminMatchGameRequest game) {
        Set<ParticipantKey> participantKeys = Stream.concat(game.team1().stream(), game.team2().stream())
                .map(ParticipantKey::from)
                .collect(Collectors.toSet());
        if (game.team1().size() != TEAM_SIZE
                || game.team2().size() != TEAM_SIZE
                || participantKeys.size() != TEAM_SIZE * 2) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }
    }

    private record ParticipantKey(MatchParticipantType participantType, Long participantId) {
        private static ParticipantKey from(AdminMatchParticipantRequest participantRequest) {
            return new ParticipantKey(participantRequest.participantType(), participantRequest.participantId());
        }
    }

    private record MatchParticipantSource(MatchParticipantType participantType,
                                          VoteAttendance voteAttendance,
                                          Guest guest) {
        private static MatchParticipantSource member(VoteAttendance voteAttendance) {
            return new MatchParticipantSource(MatchParticipantType.MEMBER, voteAttendance, null);
        }

        private static MatchParticipantSource guest(Guest guest) {
            return new MatchParticipantSource(MatchParticipantType.GUEST, null, guest);
        }
    }

    private record GameKey(Integer courtNumber, Integer matchNumber) {
    }
}
