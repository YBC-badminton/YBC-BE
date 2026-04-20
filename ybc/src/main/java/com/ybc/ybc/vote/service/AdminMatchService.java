package com.ybc.ybc.vote.service;

import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import com.ybc.ybc.members.service.ClubMemberSyncService;
import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.domain.VoteMatch;
import com.ybc.ybc.vote.domain.VoteMatchGroup;
import com.ybc.ybc.vote.dto.request.AdminMatchCourtRequest;
import com.ybc.ybc.vote.dto.request.AdminMatchGameRequest;
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
        Long voteId = matchGroup.getVote().getId();
        validateRequestedGames(request);
        Map<Long, VoteAttendance> attendanceMap = getAttendanceMap(voteId, request);

        getRequestedGameKeys(request).forEach(gameKey ->
                voteMatchRepository.deleteAllByMatchGroup_IdAndCourtNumberAndMatchNumber(
                        matchGroup.getId(),
                        gameKey.courtNumber(),
                        gameKey.matchNumber()
                )
        );
        voteMatchRepository.flush();
        voteMatchRepository.saveAllAndFlush(
                toMatchEntities(matchGroup.getVote(), matchGroup, request, attendanceMap)
        );

        return toMatchResponse(matchGroup);
    }

    @Transactional
    public AdminMatchParticipantListResponse getParticipants(Long voteId) {
        clubMemberSyncService.reconcileFinalPassMembers();
        Vote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new YbcException(ExceptionCode.VOTE_NOT_FOUND));
        List<VoteAttendance> attendances = voteAttendanceRepository.findAllActiveWithMemberByVoteId(vote.getId());
        List<Guest> guests = guestRepository.findAllByVote_IdOrderByIdAsc(vote.getId());
        Map<String, ClubMember> clubMembersByEmail = getClubMembersByEmail(attendances);

        List<AdminMatchParticipantResponse> participants = new ArrayList<>();
        attendances.forEach(attendance -> {
            AdminMatchParticipantProfileResponse profile = toMemberProfile(attendance, clubMembersByEmail);
            participants.add(new AdminMatchParticipantResponse(
                    attendance.getId(),
                    "MEMBER",
                    profile.name(),
                    profile.gender()
            ));
        });
        guests.forEach(guest -> participants.add(new AdminMatchParticipantResponse(
                guest.getId(),
                "GUEST",
                guest.getName(),
                guest.getGender()
        )));

        return new AdminMatchParticipantListResponse(vote.getId(), participants.size(), participants);
    }

    @Transactional
    public AdminMatchRegistrationResponse registerMatches(Long voteId, List<AdminMatchCourtRequest> request) {
        if (request == null || request.isEmpty()) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }

        clubMemberSyncService.reconcileFinalPassMembers();
        Vote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new YbcException(ExceptionCode.VOTE_NOT_FOUND));
        validateRequestedGames(request);
        Map<Long, VoteAttendance> attendanceMap = getAttendanceMap(voteId, request);

        voteMatchRepository.deleteAllByVote_Id(voteId);
        VoteMatchGroup matchGroup = voteMatchGroupRepository.findByVote_Id(voteId)
                .orElseGet(() -> voteMatchGroupRepository.saveAndFlush(VoteMatchGroup.create(vote)));
        voteMatchRepository.saveAllAndFlush(
                toMatchEntities(vote, matchGroup, request, attendanceMap)
        );

        return toMatchResponse(matchGroup);
    }

    private VoteMatchGroup getMatchGroup(Long matchId) {
        return voteMatchGroupRepository.findById(matchId)
                .orElseThrow(() -> new YbcException(ExceptionCode.MATCH_NOT_FOUND));
    }

    private Map<Long, VoteAttendance> getAttendanceMap(Long voteId, List<AdminMatchCourtRequest> request) {
        Set<Long> attendanceIds = request.stream()
                .flatMap(court -> court.courtMatches().stream())
                .flatMap(match -> Stream.concat(match.team1().stream(), match.team2().stream()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (attendanceIds.isEmpty()) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }

        Map<Long, VoteAttendance> attendanceMap = voteAttendanceRepository.findAllWithVoteAndMemberByIdIn(attendanceIds)
                .stream()
                .collect(Collectors.toMap(VoteAttendance::getId, Function.identity()));
        if (attendanceMap.size() != attendanceIds.size()) {
            throw new YbcException(ExceptionCode.MATCH_PARTICIPANT_NOT_FOUND);
        }

        boolean hasInvalidParticipant = attendanceMap.values().stream()
                .anyMatch(attendance -> !attendance.isAttendanceStatus()
                        || !attendance.getVote().getId().equals(voteId));
        if (hasInvalidParticipant) {
            throw new YbcException(ExceptionCode.MATCH_PARTICIPANT_NOT_FOUND);
        }

        return attendanceMap;
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
                                            Map<Long, VoteAttendance> attendanceMap) {
        List<VoteMatch> matches = new ArrayList<>();
        for (AdminMatchCourtRequest court : request) {
            for (AdminMatchGameRequest game : court.courtMatches()) {
                validateGame(game);
                addTeam(matches, vote, matchGroup, attendanceMap, court.courtNumber(), game.matchNumber(), TEAM_ONE, game.team1());
                addTeam(matches, vote, matchGroup, attendanceMap, court.courtNumber(), game.matchNumber(), TEAM_TWO, game.team2());
            }
        }
        return matches;
    }

    private void addTeam(List<VoteMatch> matches,
                         Vote vote,
                         VoteMatchGroup matchGroup,
                         Map<Long, VoteAttendance> attendanceMap,
                         Integer courtNumber,
                         Integer matchNumber,
                         int teamNumber,
                         List<Long> attendanceIds) {
        for (Long attendanceId : attendanceIds) {
            matches.add(VoteMatch.builder()
                    .vote(vote)
                    .matchGroup(matchGroup)
                    .voteAttendance(attendanceMap.get(attendanceId))
                    .courtNumber(courtNumber)
                    .matchNumber(matchNumber)
                    .teamNumber(teamNumber)
                    .build());
        }
    }

    private AdminMatchRegistrationResponse toMatchResponse(VoteMatchGroup matchGroup) {
        List<VoteMatch> matches = voteMatchRepository.findAllWithParticipantsByMatchGroupId(matchGroup.getId());
        Map<String, ClubMember> clubMembersByEmail = getClubMembersByEmail(
                matches.stream()
                        .map(VoteMatch::getVoteAttendance)
                        .toList()
        );
        List<AdminMatchCourtResponse> courtResponses = toCourtResponsesFromMatches(matches, clubMembersByEmail);
        return new AdminMatchRegistrationResponse(
                matchGroup.getVote().getId(),
                matchGroup.getId(),
                courtResponses.size(),
                courtResponses
        );
    }

    private List<AdminMatchCourtResponse> toCourtResponsesFromMatches(List<VoteMatch> matches,
                                                                      Map<String, ClubMember> clubMembersByEmail) {
        Map<Integer, List<VoteMatch>> matchesByCourt = matches.stream()
                .collect(Collectors.groupingBy(
                        VoteMatch::getCourtNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return matchesByCourt.entrySet().stream()
                .map(courtEntry -> new AdminMatchCourtResponse(
                        courtEntry.getKey(),
                        toGameResponsesFromMatches(courtEntry.getValue(), clubMembersByEmail)
                ))
                .toList();
    }

    private List<AdminMatchGameResponse> toGameResponsesFromMatches(List<VoteMatch> matches,
                                                                    Map<String, ClubMember> clubMembersByEmail) {
        Map<Integer, List<VoteMatch>> matchesByGame = matches.stream()
                .collect(Collectors.groupingBy(
                        VoteMatch::getMatchNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return matchesByGame.entrySet().stream()
                .map(gameEntry -> new AdminMatchGameResponse(
                        gameEntry.getKey(),
                        toMemberProfilesFromMatches(gameEntry.getValue(), TEAM_ONE, clubMembersByEmail),
                        toMemberProfilesFromMatches(gameEntry.getValue(), TEAM_TWO, clubMembersByEmail)
                ))
                .toList();
    }

    private List<AdminMatchParticipantProfileResponse> toMemberProfilesFromMatches(List<VoteMatch> matches,
                                                                                   int teamNumber,
                                                                                   Map<String, ClubMember> clubMembersByEmail) {
        return matches.stream()
                .filter(match -> match.getTeamNumber() == teamNumber)
                .map(match -> toMemberProfile(match.getVoteAttendance(), clubMembersByEmail))
                .toList();
    }

    private AdminMatchParticipantProfileResponse toMemberProfile(VoteAttendance attendance,
                                                                 Map<String, ClubMember> clubMembersByEmail) {
        String email = normalizeEmail(attendance.getMember().getEmail());
        ClubMember clubMember = clubMembersByEmail.get(email);
        if (clubMember != null) {
            return new AdminMatchParticipantProfileResponse(clubMember.getName(), clubMember.getGender());
        }
        return new AdminMatchParticipantProfileResponse(attendance.getMember().getNickname(), null);
    }

    private Map<String, ClubMember> getClubMembersByEmail(Iterable<VoteAttendance> attendances) {
        Set<String> emails = new LinkedHashSet<>();
        attendances.forEach(attendance -> {
            String email = normalizeEmail(attendance.getMember().getEmail());
            if (email != null) {
                emails.add(email);
            }
        });
        if (emails.isEmpty()) {
            return Map.of();
        }

        return clubMemberRepository.findAllByNormalizedEmailInOrderByIdAsc(emails).stream()
                .filter(clubMember -> normalizeEmail(clubMember.getEmail()) != null)
                .collect(Collectors.toMap(
                        clubMember -> Objects.requireNonNull(normalizeEmail(clubMember.getEmail())),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validateGame(AdminMatchGameRequest game) {
        Set<Long> playerIds = Stream.concat(game.team1().stream(), game.team2().stream())
                .collect(Collectors.toSet());
        if (game.team1().size() != TEAM_SIZE
                || game.team2().size() != TEAM_SIZE
                || playerIds.size() != TEAM_SIZE * 2) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }
    }

    private record GameKey(Integer courtNumber, Integer matchNumber) {
    }
}
