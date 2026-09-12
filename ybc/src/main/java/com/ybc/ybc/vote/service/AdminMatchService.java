package com.ybc.ybc.vote.service;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.service.ClubMemberLookupService;
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
import com.ybc.ybc.vote.dto.request.AdminMatchGenerateCourtAssignmentRequest;
import com.ybc.ybc.vote.dto.request.AdminMatchGenerateRequest;
import com.ybc.ybc.vote.dto.request.AdminMatchParticipantRequest;
import com.ybc.ybc.vote.dto.response.AdminMatchCourtResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchDetailCourtResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchDetailCourtMatchResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchDetailParticipantResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchDetailResponse;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMatchService {
    private static final int TEAM_SIZE = 2;
    private static final int TEAM_ONE = 1;
    private static final int TEAM_TWO = 2;
    private static final int PARTICIPANTS_PER_GAME = TEAM_SIZE * 2;
    private static final int MINIMUM_MATCHES_PER_PARTICIPANT = 2;
    private static final int MAX_GENERATION_ATTEMPTS = 96;

    private final VoteRepository voteRepository;
    private final VoteAttendanceRepository voteAttendanceRepository;
    private final GuestRepository guestRepository;
    private final ClubMemberLookupService clubMemberLookupService;
    private final ClubMemberSyncService clubMemberSyncService;
    private final VoteMatchRepository voteMatchRepository;
    private final VoteMatchGroupRepository voteMatchGroupRepository;

    @Transactional(readOnly = true)
    public AdminMatchDetailResponse getMatch(Long matchId) {
        VoteMatchGroup matchGroup = getMatchGroup(matchId);
        return toMatchDetailResponse(matchGroup);
    }

    @Transactional(readOnly = true)
    public AdminMatchRegistrationResponse getMatchByVoteId(Long voteId) {
        clubMemberSyncService.reconcileFinalPassMembersSafely();
        Vote requestedVote = getVote(voteId);
        Vote baseVote = resolveBaseVote(requestedVote);
        VoteMatchGroup matchGroup = voteMatchGroupRepository.findByVote_Id(baseVote.getId())
                .orElseThrow(() -> new YbcException(ExceptionCode.MATCH_NOT_FOUND));
        return toMatchResponse(matchGroup);
    }

    @Transactional
    public AdminMatchRegistrationResponse updateMatch(Long matchId, List<AdminMatchCourtRequest> request) {
        if (request == null || request.isEmpty()) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }

        clubMemberSyncService.reconcileFinalPassMembersSafely();
        VoteMatchGroup matchGroup = getMatchGroup(matchId);
        Vote baseVote = resolveBaseVote(matchGroup.getVote());
        List<Vote> relatedVotes = getRelatedVotes(baseVote);
        validateRequestedGames(request);
        Map<ParticipantKey, MatchParticipantSource> participantMap = getParticipantMap(
                baseVote,
                relatedVotes,
                extractMatchParticipantKeys(request)
        );

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
    public void deleteMatch(Long matchId) {
        VoteMatchGroup matchGroup = getMatchGroup(matchId);
        voteMatchRepository.deleteAllByMatchGroup_Id(matchId);
        voteMatchGroupRepository.delete(matchGroup);
    }

    @Transactional
    public AdminMatchParticipantListResponse getParticipants(Long voteId) {
        clubMemberSyncService.reconcileFinalPassMembersSafely();
        Vote requestedVote = getVote(voteId);
        Vote baseVote = resolveBaseVote(requestedVote);
        List<Vote> relatedVotes = getRelatedVotes(baseVote);
        ConfirmedParticipantScope participantScope = resolveConfirmedParticipantScope(baseVote, relatedVotes);
        List<VoteAttendance> attendances = deduplicateAttendances(participantScope.confirmedAttendances());
        Map<Long, ClubMember> clubMembersByMemberId = getClubMembersByMemberIdSafely(attendances);

        List<AdminMatchParticipantResponse> participants = new ArrayList<>();
        attendances.forEach(attendance -> {
            AdminMatchParticipantProfileResponse profile = toMemberProfile(attendance, clubMembersByMemberId);
            participants.add(new AdminMatchParticipantResponse(
                    attendance.getId(),
                    MatchParticipantType.MEMBER.name(),
                    profile.name(),
                    profile.gender(),
                    profile.level()
            ));
        });
        participantScope.confirmedGuests().forEach(guest -> participants.add(new AdminMatchParticipantResponse(
                guest.getId(),
                MatchParticipantType.GUEST.name(),
                guest.getName(),
                guest.getGender(),
                guest.getLevel()
        )));

        return new AdminMatchParticipantListResponse(baseVote.getId(), participants.size(), participants);
    }

    @Transactional
    public AdminMatchRegistrationResponse registerMatches(Long voteId, List<AdminMatchCourtRequest> request) {
        if (request == null || request.isEmpty()) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }

        clubMemberSyncService.reconcileFinalPassMembersSafely();
        Vote requestedVote = getVote(voteId);
        Vote baseVote = resolveBaseVote(requestedVote);
        List<Vote> relatedVotes = getRelatedVotes(baseVote);
        Set<Long> relatedVoteIds = getVoteIds(relatedVotes);
        validateRequestedGames(request);
        Map<ParticipantKey, MatchParticipantSource> participantMap = getParticipantMap(
                baseVote,
                relatedVotes,
                extractMatchParticipantKeys(request)
        );

        VoteMatchGroup matchGroup = replaceMatches(baseVote, relatedVoteIds, request, participantMap);
        return toMatchResponse(matchGroup);
    }

    @Transactional
    public AdminMatchRegistrationResponse generateMatches(Long voteId, AdminMatchGenerateRequest request) {
        if (request == null || request.courtAssignments() == null || request.courtAssignments().isEmpty()) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }

        clubMemberSyncService.reconcileFinalPassMembersSafely();
        Vote requestedVote = getVote(voteId);
        Vote baseVote = resolveBaseVote(requestedVote);
        List<Vote> relatedVotes = getRelatedVotes(baseVote);
        Set<Long> relatedVoteIds = getVoteIds(relatedVotes);
        validateCourtAssignments(request);

        Set<ParticipantKey> participantKeys = extractAssignedParticipantKeys(request.courtAssignments());
        if (participantKeys.isEmpty()) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }
        Map<ParticipantKey, MatchParticipantSource> participantMap = getParticipantMap(baseVote, relatedVotes, participantKeys);
        List<AdminMatchCourtRequest> generatedMatches = generateCourtMatches(request.courtAssignments(), participantMap);

        VoteMatchGroup matchGroup = replaceMatches(baseVote, relatedVoteIds, generatedMatches, participantMap);
        return toMatchResponse(matchGroup);
    }

    private VoteMatchGroup replaceMatches(Vote baseVote,
                                          Set<Long> relatedVoteIds,
                                          List<AdminMatchCourtRequest> request,
                                          Map<ParticipantKey, MatchParticipantSource> participantMap) {
        voteMatchRepository.deleteAllByVote_IdIn(relatedVoteIds);
        Set<Long> additionalVoteIds = relatedVoteIds.stream()
                .filter(relatedVoteId -> !relatedVoteId.equals(baseVote.getId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!additionalVoteIds.isEmpty()) {
            voteMatchGroupRepository.deleteAllByVote_IdIn(additionalVoteIds);
        }

        VoteMatchGroup matchGroup = voteMatchGroupRepository.findByVote_Id(baseVote.getId())
                .orElseGet(() -> voteMatchGroupRepository.saveAndFlush(VoteMatchGroup.create(baseVote)));
        if (!request.isEmpty()) {
            voteMatchRepository.saveAllAndFlush(
                    toMatchEntities(baseVote, matchGroup, request, participantMap)
            );
        }
        return matchGroup;
    }

    private VoteMatchGroup getMatchGroup(Long matchId) {
        return voteMatchGroupRepository.findById(matchId)
                .orElseThrow(() -> new YbcException(ExceptionCode.MATCH_NOT_FOUND));
    }

    private Vote getVote(Long voteId) {
        return voteRepository.findById(voteId)
                .orElseThrow(() -> new YbcException(ExceptionCode.VOTE_NOT_FOUND));
    }

    private List<AdminMatchCourtRequest> generateCourtMatches(
            List<AdminMatchGenerateCourtAssignmentRequest> courtAssignments,
            Map<ParticipantKey, MatchParticipantSource> participantMap
    ) {
        return courtAssignments.stream()
                .sorted(Comparator.comparing(AdminMatchGenerateCourtAssignmentRequest::courtNumber))
                .map(courtAssignment -> new AdminMatchCourtRequest(
                        courtAssignment.courtNumber(),
                        generateGamesForCourt(courtAssignment.participants(), participantMap)
                ))
                .filter(court -> !court.courtMatches().isEmpty())
                .toList();
    }

    private List<AdminMatchGameRequest> generateGamesForCourt(
            List<AdminMatchParticipantRequest> participantRequests,
            Map<ParticipantKey, MatchParticipantSource> participantMap
    ) {
        if (participantRequests == null || participantRequests.size() < PARTICIPANTS_PER_GAME) {
            return List.of();
        }

        List<GeneratedParticipant> participants = participantRequests.stream()
                .map(ParticipantKey::from)
                .distinct()
                .map(participantKey -> new GeneratedParticipant(participantKey, participantMap.get(participantKey)))
                .toList();
        if (participants.size() < PARTICIPANTS_PER_GAME) {
            return List.of();
        }

        GeneratedCourtSchedule bestSchedule = null;
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            List<GeneratedParticipant> orderedParticipants = buildParticipantOrder(participants, attempt);
            GeneratedCourtSchedule candidate = buildCourtSchedule(orderedParticipants);
            if (bestSchedule == null || candidate.score() > bestSchedule.score()) {
                bestSchedule = candidate;
            }
        }

        if (bestSchedule == null || bestSchedule.games().isEmpty()) {
            return List.of();
        }

        List<AdminMatchGameRequest> generatedGames = new ArrayList<>();
        for (int matchNumber = 0; matchNumber < bestSchedule.games().size(); matchNumber++) {
            GeneratedGame generatedGame = bestSchedule.games().get(matchNumber);
            generatedGames.add(new AdminMatchGameRequest(
                    matchNumber + 1,
                    toParticipantRequests(generatedGame.team1()),
                    toParticipantRequests(generatedGame.team2())
            ));
        }
        return generatedGames;
    }

    private List<GeneratedParticipant> buildParticipantOrder(List<GeneratedParticipant> participants, int attempt) {
        int strategy = attempt % 4;
        List<GeneratedParticipant> orderedParticipants;
        if (strategy == 0) {
            orderedParticipants = new ArrayList<>(participants);
            Collections.shuffle(orderedParticipants);
        } else {
            List<GeneratedParticipant> males = shuffledParticipantsByGender(participants, Gender.MALE);
            List<GeneratedParticipant> females = shuffledParticipantsByGender(participants, Gender.FEMALE);
            List<GeneratedParticipant> unknowns = shuffledParticipantsByGender(participants, null);
            orderedParticipants = switch (strategy) {
                case 1 -> interleaveParticipants(males, females, unknowns);
                case 2 -> clusterParticipants(females, males, unknowns);
                default -> clusterParticipants(males, females, unknowns);
            };
        }

        if (!orderedParticipants.isEmpty()) {
            Collections.rotate(orderedParticipants, ThreadLocalRandom.current().nextInt(orderedParticipants.size()));
        }
        return orderedParticipants;
    }

    private List<GeneratedParticipant> shuffledParticipantsByGender(List<GeneratedParticipant> participants, Gender gender) {
        List<GeneratedParticipant> filteredParticipants = participants.stream()
                .filter(participant -> Objects.equals(participant.source().gender(), gender))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(filteredParticipants);
        return filteredParticipants;
    }

    private List<GeneratedParticipant> interleaveParticipants(List<GeneratedParticipant> firstGroup,
                                                              List<GeneratedParticipant> secondGroup,
                                                              List<GeneratedParticipant> unknowns) {
        List<GeneratedParticipant> orderedParticipants = new ArrayList<>();
        boolean useFirstGroup = firstGroup.size() >= secondGroup.size();
        while (!firstGroup.isEmpty() || !secondGroup.isEmpty()) {
            List<GeneratedParticipant> primaryGroup = useFirstGroup ? firstGroup : secondGroup;
            List<GeneratedParticipant> secondaryGroup = useFirstGroup ? secondGroup : firstGroup;
            if (!primaryGroup.isEmpty()) {
                orderedParticipants.add(primaryGroup.remove(0));
            } else if (!secondaryGroup.isEmpty()) {
                orderedParticipants.add(secondaryGroup.remove(0));
            }
            useFirstGroup = !useFirstGroup;
        }
        insertUnknownParticipants(orderedParticipants, unknowns);
        return orderedParticipants;
    }

    private List<GeneratedParticipant> clusterParticipants(List<GeneratedParticipant> firstGroup,
                                                           List<GeneratedParticipant> secondGroup,
                                                           List<GeneratedParticipant> unknowns) {
        List<GeneratedParticipant> orderedParticipants = new ArrayList<>();
        orderedParticipants.addAll(firstGroup);
        orderedParticipants.addAll(secondGroup);
        insertUnknownParticipants(orderedParticipants, unknowns);
        return orderedParticipants;
    }

    private void insertUnknownParticipants(List<GeneratedParticipant> orderedParticipants,
                                           List<GeneratedParticipant> unknowns) {
        if (unknowns.isEmpty()) {
            return;
        }
        if (orderedParticipants.isEmpty()) {
            orderedParticipants.addAll(unknowns);
            return;
        }
        unknowns.forEach(unknownParticipant ->
                orderedParticipants.add(
                        ThreadLocalRandom.current().nextInt(orderedParticipants.size() + 1),
                        unknownParticipant
                )
        );
    }

    private GeneratedCourtSchedule buildCourtSchedule(List<GeneratedParticipant> orderedParticipants) {
        int gameCount = getTargetGameCount(orderedParticipants.size());
        TargetGameDistribution targetDistribution = determineTargetDistribution(orderedParticipants, gameCount);
        Map<ParticipantPair, Integer> teammateHistory = new LinkedHashMap<>();
        Map<ParticipantPair, Integer> opponentHistory = new LinkedHashMap<>();
        List<GeneratedGame> games = new ArrayList<>();
        int totalScore = 0;

        for (int gameIndex = 0; gameIndex < gameCount; gameIndex++) {
            List<GeneratedParticipant> roster = buildGameRoster(orderedParticipants, gameIndex);
            GeneratedGame generatedGame = chooseBestGame(roster, teammateHistory, opponentHistory);
            games.add(generatedGame);
            totalScore += generatedGame.score();
        }

        totalScore += scoreAppearanceBalance(orderedParticipants, games);
        totalScore += scoreGameDistribution(games, targetDistribution);
        return new GeneratedCourtSchedule(games, totalScore);
    }

    private int getTargetGameCount(int participantCount) {
        if (participantCount < PARTICIPANTS_PER_GAME) {
            return 0;
        }
        return Math.max(MINIMUM_MATCHES_PER_PARTICIPANT, (participantCount + 1) / 2);
    }

    private List<GeneratedParticipant> buildGameRoster(List<GeneratedParticipant> orderedParticipants, int gameIndex) {
        List<GeneratedParticipant> roster = new ArrayList<>(PARTICIPANTS_PER_GAME);
        int participantCount = orderedParticipants.size();
        int startIndex = (gameIndex * TEAM_SIZE) % participantCount;
        for (int offset = 0; offset < PARTICIPANTS_PER_GAME; offset++) {
            roster.add(orderedParticipants.get((startIndex + offset) % participantCount));
        }
        return roster;
    }

    private GeneratedGame chooseBestGame(List<GeneratedParticipant> roster,
                                         Map<ParticipantPair, Integer> teammateHistory,
                                         Map<ParticipantPair, Integer> opponentHistory) {
        List<TeamSplit> candidates = enumerateTeamSplits(roster);
        TeamSplit bestSplit = null;
        int bestScore = Integer.MIN_VALUE;
        for (TeamSplit candidate : candidates) {
            int score = scoreRoster(roster)
                    + scoreTeamSplit(candidate.team1(), candidate.team2(), teammateHistory, opponentHistory);
            if (bestSplit == null || score > bestScore || (score == bestScore && ThreadLocalRandom.current().nextBoolean())) {
                bestSplit = candidate;
                bestScore = score;
            }
        }

        if (bestSplit == null) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }

        updateTeammateHistory(bestSplit.team1(), teammateHistory);
        updateTeammateHistory(bestSplit.team2(), teammateHistory);
        updateOpponentHistory(bestSplit.team1(), bestSplit.team2(), opponentHistory);
        return new GeneratedGame(bestSplit.team1(), bestSplit.team2(), bestScore);
    }

    private List<TeamSplit> enumerateTeamSplits(List<GeneratedParticipant> roster) {
        GeneratedParticipant first = roster.get(0);
        GeneratedParticipant second = roster.get(1);
        GeneratedParticipant third = roster.get(2);
        GeneratedParticipant fourth = roster.get(3);

        return List.of(
                new TeamSplit(List.of(first, second), List.of(third, fourth)),
                new TeamSplit(List.of(first, third), List.of(second, fourth)),
                new TeamSplit(List.of(first, fourth), List.of(second, third))
        );
    }

    private int scoreRoster(List<GeneratedParticipant> roster) {
        int maleCount = countGender(roster, Gender.MALE);
        int femaleCount = countGender(roster, Gender.FEMALE);
        int unknownCount = countUnknownGender(roster);

        if (unknownCount == 0 && maleCount == TEAM_SIZE && femaleCount == TEAM_SIZE) {
            return 40;
        }
        if (unknownCount == 0 && (maleCount == PARTICIPANTS_PER_GAME || femaleCount == PARTICIPANTS_PER_GAME)) {
            return 32;
        }
        return 18 - Math.abs(maleCount - femaleCount) * 3 - unknownCount * 2;
    }

    private int scoreTeamSplit(List<GeneratedParticipant> team1,
                               List<GeneratedParticipant> team2,
                               Map<ParticipantPair, Integer> teammateHistory,
                               Map<ParticipantPair, Integer> opponentHistory) {
        int score = 0;
        if (isMixedTeam(team1)) {
            score += 14;
        }
        if (isMixedTeam(team2)) {
            score += 14;
        }
        if (isSameGenderTeam(team1)) {
            score += 14;
        }
        if (isSameGenderTeam(team2)) {
            score += 14;
        }
        if (hasSameComposition(team1, team2)) {
            score += 16;
        }
        if (classifyGame(team1, team2) == GameCategory.OTHER) {
            score -= 8;
        }

        score -= Math.abs(countGender(team1, Gender.MALE) - countGender(team2, Gender.MALE)) * 4;
        score -= Math.abs(countGender(team1, Gender.FEMALE) - countGender(team2, Gender.FEMALE)) * 4;
        score -= Math.abs(countUnknownGender(team1) - countUnknownGender(team2)) * 2;
        score -= repeatedTeammatePenalty(team1, teammateHistory);
        score -= repeatedTeammatePenalty(team2, teammateHistory);
        score -= repeatedOpponentPenalty(team1, team2, opponentHistory);
        return score;
    }

    private int repeatedTeammatePenalty(List<GeneratedParticipant> team,
                                        Map<ParticipantPair, Integer> teammateHistory) {
        ParticipantPair pair = ParticipantPair.of(team.get(0).participantKey(), team.get(1).participantKey());
        return teammateHistory.getOrDefault(pair, 0) * 12;
    }

    private int repeatedOpponentPenalty(List<GeneratedParticipant> team1,
                                        List<GeneratedParticipant> team2,
                                        Map<ParticipantPair, Integer> opponentHistory) {
        int penalty = 0;
        for (GeneratedParticipant firstTeamParticipant : team1) {
            for (GeneratedParticipant secondTeamParticipant : team2) {
                penalty += opponentHistory.getOrDefault(
                        ParticipantPair.of(firstTeamParticipant.participantKey(), secondTeamParticipant.participantKey()),
                        0
                ) * 3;
            }
        }
        return penalty;
    }

    private void updateTeammateHistory(List<GeneratedParticipant> team,
                                       Map<ParticipantPair, Integer> teammateHistory) {
        ParticipantPair pair = ParticipantPair.of(team.get(0).participantKey(), team.get(1).participantKey());
        teammateHistory.merge(pair, 1, Integer::sum);
    }

    private void updateOpponentHistory(List<GeneratedParticipant> team1,
                                       List<GeneratedParticipant> team2,
                                       Map<ParticipantPair, Integer> opponentHistory) {
        for (GeneratedParticipant firstTeamParticipant : team1) {
            for (GeneratedParticipant secondTeamParticipant : team2) {
                opponentHistory.merge(
                        ParticipantPair.of(firstTeamParticipant.participantKey(), secondTeamParticipant.participantKey()),
                        1,
                        Integer::sum
                );
            }
        }
    }

    private int scoreAppearanceBalance(List<GeneratedParticipant> participants, List<GeneratedGame> games) {
        Map<ParticipantKey, Integer> appearances = new LinkedHashMap<>();
        participants.forEach(participant -> appearances.put(participant.participantKey(), 0));
        games.forEach(game -> Stream.concat(game.team1().stream(), game.team2().stream())
                .forEach(participant -> appearances.merge(participant.participantKey(), 1, Integer::sum)));
        int minimumAppearances = appearances.values().stream()
                .min(Integer::compareTo)
                .orElse(0);
        int maximumAppearances = appearances.values().stream()
                .max(Integer::compareTo)
                .orElse(0);
        return minimumAppearances * 12 - (maximumAppearances - minimumAppearances) * 6;
    }

    private TargetGameDistribution determineTargetDistribution(List<GeneratedParticipant> participants, int gameCount) {
        int maleCount = countGender(participants, Gender.MALE);
        int femaleCount = countGender(participants, Gender.FEMALE);
        int unknownCount = countUnknownGender(participants);
        boolean mixedAvailable = maleCount + unknownCount >= TEAM_SIZE
                && femaleCount + unknownCount >= TEAM_SIZE;
        boolean menAvailable = maleCount + unknownCount >= PARTICIPANTS_PER_GAME;
        boolean womenAvailable = femaleCount + unknownCount >= PARTICIPANTS_PER_GAME;

        TargetGameDistribution bestDistribution = new TargetGameDistribution(0, 0, 0, gameCount);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int mixedCount = 0; mixedCount <= gameCount; mixedCount++) {
            if (mixedCount > 0 && !mixedAvailable) {
                continue;
            }
            for (int menCount = 0; menCount <= gameCount - mixedCount; menCount++) {
                if (menCount > 0 && !menAvailable) {
                    continue;
                }
                for (int womenCount = 0; womenCount <= gameCount - mixedCount - menCount; womenCount++) {
                    if (womenCount > 0 && !womenAvailable) {
                        continue;
                    }
                    int otherCount = gameCount - mixedCount - menCount - womenCount;
                    TargetGameDistribution candidate = new TargetGameDistribution(
                            mixedCount,
                            menCount,
                            womenCount,
                            otherCount
                    );
                    double candidateScore = scoreTargetDistribution(
                            candidate,
                            maleCount,
                            femaleCount,
                            unknownCount,
                            mixedAvailable,
                            menAvailable,
                            womenAvailable,
                            gameCount
                    );
                    if (candidateScore > bestScore) {
                        bestDistribution = candidate;
                        bestScore = candidateScore;
                    }
                }
            }
        }
        return bestDistribution;
    }

    private int scoreGameDistribution(List<GeneratedGame> games, TargetGameDistribution targetDistribution) {
        Map<GameCategory, Long> actualCounts = games.stream()
                .map(this::classifyGame)
                .collect(Collectors.groupingBy(
                        Function.identity(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        int score = 0;
        score -= Math.abs(getGameCount(actualCounts, GameCategory.MIXED) - targetDistribution.mixedCount()) * 28;
        score -= Math.abs(getGameCount(actualCounts, GameCategory.MEN) - targetDistribution.menCount()) * 28;
        score -= Math.abs(getGameCount(actualCounts, GameCategory.WOMEN) - targetDistribution.womenCount()) * 28;
        score -= Math.abs(getGameCount(actualCounts, GameCategory.OTHER) - targetDistribution.otherCount()) * 18;
        return score;
    }

    private long getGameCount(Map<GameCategory, Long> gameCounts, GameCategory gameCategory) {
        return gameCounts.getOrDefault(gameCategory, 0L);
    }

    private double scoreTargetDistribution(TargetGameDistribution distribution,
                                           int maleCount,
                                           int femaleCount,
                                           int unknownCount,
                                           boolean mixedAvailable,
                                           boolean menAvailable,
                                           boolean womenAvailable,
                                           int gameCount) {
        int knownCount = maleCount + femaleCount;
        double expectedMaleAppearances = knownCount == 0
                ? 0
                : (double) PARTICIPANTS_PER_GAME * gameCount * maleCount / knownCount;
        double expectedFemaleAppearances = knownCount == 0
                ? 0
                : (double) PARTICIPANTS_PER_GAME * gameCount * femaleCount / knownCount;
        double otherMaleAppearances = knownCount == 0
                ? unknownCount * distribution.otherCount()
                : (double) PARTICIPANTS_PER_GAME * distribution.otherCount() * maleCount / knownCount;
        double otherFemaleAppearances = knownCount == 0
                ? unknownCount * distribution.otherCount()
                : (double) PARTICIPANTS_PER_GAME * distribution.otherCount() * femaleCount / knownCount;
        double projectedMaleAppearances = distribution.mixedCount() * 2D
                + distribution.menCount() * 4D
                + otherMaleAppearances;
        double projectedFemaleAppearances = distribution.mixedCount() * 2D
                + distribution.womenCount() * 4D
                + otherFemaleAppearances;

        double score = 0;
        score -= Math.abs(projectedMaleAppearances - expectedMaleAppearances) * 24D;
        score -= Math.abs(projectedFemaleAppearances - expectedFemaleAppearances) * 24D;
        score -= distribution.otherCount() * 36D;

        int categoryCount = countPositive(distribution.mixedCount(), distribution.menCount(), distribution.womenCount());
        score += categoryCount * 18D;
        if (distribution.menCount() > 0 && distribution.womenCount() > 0) {
            score += 14D;
        }
        if (menAvailable && womenAvailable) {
            score -= Math.abs(distribution.menCount() - distribution.womenCount()) * 8D;
        }
        if (mixedAvailable && menAvailable && womenAvailable) {
            score -= Math.abs(distribution.mixedCount() - gameCount / 2D) * 6D;
        } else if (mixedAvailable && (menAvailable || womenAvailable)) {
            score -= Math.abs(distribution.mixedCount() - gameCount * (2D / 3D)) * 4D;
        }
        return score;
    }

    private int countPositive(int... values) {
        int count = 0;
        for (int value : values) {
            if (value > 0) {
                count++;
            }
        }
        return count;
    }

    private GameCategory classifyGame(GeneratedGame game) {
        return classifyGame(game.team1(), game.team2());
    }

    private GameCategory classifyGame(List<GeneratedParticipant> team1, List<GeneratedParticipant> team2) {
        if (isMixedTeam(team1) && isMixedTeam(team2)) {
            return GameCategory.MIXED;
        }
        if (isMaleTeam(team1) && isMaleTeam(team2)) {
            return GameCategory.MEN;
        }
        if (isFemaleTeam(team1) && isFemaleTeam(team2)) {
            return GameCategory.WOMEN;
        }
        return GameCategory.OTHER;
    }

    private boolean isMixedTeam(List<GeneratedParticipant> team) {
        return countUnknownGender(team) == 0
                && countGender(team, Gender.MALE) == 1
                && countGender(team, Gender.FEMALE) == 1;
    }

    private boolean isSameGenderTeam(List<GeneratedParticipant> team) {
        return countUnknownGender(team) == 0
                && (countGender(team, Gender.MALE) == TEAM_SIZE || countGender(team, Gender.FEMALE) == TEAM_SIZE);
    }

    private boolean isMaleTeam(List<GeneratedParticipant> team) {
        return countUnknownGender(team) == 0
                && countGender(team, Gender.MALE) == TEAM_SIZE;
    }

    private boolean isFemaleTeam(List<GeneratedParticipant> team) {
        return countUnknownGender(team) == 0
                && countGender(team, Gender.FEMALE) == TEAM_SIZE;
    }

    private boolean hasSameComposition(List<GeneratedParticipant> team1, List<GeneratedParticipant> team2) {
        return countGender(team1, Gender.MALE) == countGender(team2, Gender.MALE)
                && countGender(team1, Gender.FEMALE) == countGender(team2, Gender.FEMALE)
                && countUnknownGender(team1) == countUnknownGender(team2);
    }

    private int countGender(List<GeneratedParticipant> participants, Gender gender) {
        return (int) participants.stream()
                .filter(participant -> participant.source().gender() == gender)
                .count();
    }

    private int countUnknownGender(List<GeneratedParticipant> participants) {
        return (int) participants.stream()
                .filter(participant -> participant.source().gender() == null)
                .count();
    }

    private List<AdminMatchParticipantRequest> toParticipantRequests(List<GeneratedParticipant> participants) {
        return participants.stream()
                .map(participant -> new AdminMatchParticipantRequest(
                        participant.participantKey().participantType(),
                        participant.participantKey().participantId()
                ))
                .toList();
    }

    private void validateCourtAssignments(AdminMatchGenerateRequest request) {
        if (request.courtCount() == null || request.courtCount() < 1 || request.courtAssignments().size() > request.courtCount()) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }

        Set<Integer> courtNumbers = new LinkedHashSet<>();
        Set<ParticipantKey> assignedParticipants = new LinkedHashSet<>();
        for (AdminMatchGenerateCourtAssignmentRequest courtAssignment : request.courtAssignments()) {
            if (courtAssignment.courtNumber() == null
                    || courtAssignment.courtNumber() < 1
                    || courtAssignment.courtNumber() > request.courtCount()
                    || courtAssignment.participants() == null) {
                throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
            }
            if (!courtNumbers.add(courtAssignment.courtNumber())) {
                throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
            }

            Set<ParticipantKey> courtParticipants = new LinkedHashSet<>();
            for (AdminMatchParticipantRequest participantRequest : courtAssignment.participants()) {
                ParticipantKey participantKey = ParticipantKey.from(participantRequest);
                if (!courtParticipants.add(participantKey) || !assignedParticipants.add(participantKey)) {
                    throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
                }
            }
        }
    }

    private Set<ParticipantKey> extractMatchParticipantKeys(List<AdminMatchCourtRequest> request) {
        return request.stream()
                .flatMap(court -> court.courtMatches().stream())
                .flatMap(match -> Stream.concat(match.team1().stream(), match.team2().stream()))
                .map(ParticipantKey::from)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<ParticipantKey> extractAssignedParticipantKeys(
            List<AdminMatchGenerateCourtAssignmentRequest> courtAssignments
    ) {
        return courtAssignments.stream()
                .flatMap(courtAssignment -> courtAssignment.participants().stream())
                .map(ParticipantKey::from)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<ParticipantKey, MatchParticipantSource> getParticipantMap(Vote baseVote,
                                                                          List<Vote> relatedVotes,
                                                                          Set<ParticipantKey> participantKeys) {
        ConfirmedParticipantScope participantScope = resolveConfirmedParticipantScope(baseVote, relatedVotes);
        Set<Long> attendanceIds = filterParticipantIds(participantKeys, MatchParticipantType.MEMBER);
        Set<Long> guestIds = filterParticipantIds(participantKeys, MatchParticipantType.GUEST);
        Map<Long, VoteAttendance> attendanceMap = getAttendanceMap(participantScope, attendanceIds);
        Map<Long, Guest> guestMap = getGuestMap(participantScope, guestIds);
        Map<Long, ClubMember> clubMembersByMemberId = getClubMembersByMemberIdSafely(attendanceMap.values());

        Map<ParticipantKey, MatchParticipantSource> participantMap = new LinkedHashMap<>();
        participantKeys.forEach(participantKey -> {
            if (participantKey.participantType() == MatchParticipantType.MEMBER) {
                VoteAttendance attendance = attendanceMap.get(participantKey.participantId());
                participantMap.put(
                        participantKey,
                        MatchParticipantSource.member(attendance, toMemberProfile(attendance, clubMembersByMemberId))
                );
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

    private Map<Long, VoteAttendance> getAttendanceMap(ConfirmedParticipantScope participantScope,
                                                       Collection<Long> attendanceIds) {
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
                        || !participantScope.relatedVoteIds().contains(attendance.getVote().getId())
                        || !participantScope.confirmedAttendanceIds().contains(attendance.getId()));
        if (hasInvalidParticipant) {
            throw new YbcException(ExceptionCode.MATCH_PARTICIPANT_NOT_FOUND);
        }

        return attendanceMap;
    }

    private Map<Long, Guest> getGuestMap(ConfirmedParticipantScope participantScope,
                                         Collection<Long> guestIds) {
        if (guestIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Guest> guestMap = guestRepository.findAllWithVoteByIdIn(guestIds).stream()
                .collect(Collectors.toMap(Guest::getId, Function.identity()));
        if (guestMap.size() != guestIds.size()) {
            throw new YbcException(ExceptionCode.MATCH_PARTICIPANT_NOT_FOUND);
        }

        boolean hasInvalidParticipant = guestMap.values().stream()
                .anyMatch(guest -> !participantScope.relatedVoteIds().contains(guest.getVote().getId())
                        || !participantScope.confirmedGuestIds().contains(guest.getId()));
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
        Map<Long, ClubMember> clubMembersByMemberId = getClubMembersByMemberIdSafely(
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

    private AdminMatchDetailResponse toMatchDetailResponse(VoteMatchGroup matchGroup) {
        List<VoteMatch> matches = voteMatchRepository.findAllWithParticipantsByMatchGroupId(matchGroup.getId());
        Map<Long, ClubMember> clubMembersByMemberId = getClubMembersByMemberIdSafely(
                matches.stream()
                        .map(VoteMatch::getVoteAttendance)
                        .filter(Objects::nonNull)
                        .toList()
        );
        return new AdminMatchDetailResponse(
                matchGroup.getVote().getId(),
                matchGroup.getId(),
                toCourtDetailResponses(matches, clubMembersByMemberId)
        );
    }

    private List<AdminMatchDetailCourtResponse> toCourtDetailResponses(List<VoteMatch> matches,
                                                                       Map<Long, ClubMember> clubMembersByMemberId) {
        Map<Integer, List<VoteMatch>> matchesByCourt = matches.stream()
                .collect(Collectors.groupingBy(
                        VoteMatch::getCourtNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return matchesByCourt.entrySet().stream()
                .map(courtEntry -> new AdminMatchDetailCourtResponse(
                        courtEntry.getKey(),
                        toGameDetailResponsesFromMatches(courtEntry.getValue(), clubMembersByMemberId)
                ))
                .toList();
    }

    private List<AdminMatchDetailCourtMatchResponse> toGameDetailResponsesFromMatches(List<VoteMatch> matches,
                                                                                      Map<Long, ClubMember> clubMembersByMemberId) {
        Map<Integer, List<VoteMatch>> matchesByGame = matches.stream()
                .collect(Collectors.groupingBy(
                        VoteMatch::getMatchNumber,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return matchesByGame.entrySet().stream()
                .map(gameEntry -> new AdminMatchDetailCourtMatchResponse(
                        gameEntry.getKey(),
                        toParticipantDetailResponsesFromMatches(gameEntry.getValue(), TEAM_ONE, clubMembersByMemberId),
                        toParticipantDetailResponsesFromMatches(gameEntry.getValue(), TEAM_TWO, clubMembersByMemberId)
                ))
                .toList();
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

    private List<AdminMatchDetailParticipantResponse> toParticipantDetailResponsesFromMatches(List<VoteMatch> matches,
                                                                                               int teamNumber,
                                                                                               Map<Long, ClubMember> clubMembersByMemberId) {
        return matches.stream()
                .filter(match -> match.getTeamNumber() == teamNumber)
                .map(match -> toParticipantDetailResponse(match, clubMembersByMemberId))
                .toList();
    }

    private AdminMatchParticipantProfileResponse toParticipantProfile(VoteMatch match,
                                                                      Map<Long, ClubMember> clubMembersByMemberId) {
        if (match.getParticipantType() == MatchParticipantType.GUEST) {
            Guest guest = match.getGuest();
            return new AdminMatchParticipantProfileResponse(guest.getName(), guest.getGender(), guest.getLevel());
        }
        return toMemberProfile(match.getVoteAttendance(), clubMembersByMemberId);
    }

    private AdminMatchDetailParticipantResponse toParticipantDetailResponse(VoteMatch match,
                                                                            Map<Long, ClubMember> clubMembersByMemberId) {
        AdminMatchParticipantProfileResponse profile = toParticipantProfile(match, clubMembersByMemberId);
        return new AdminMatchDetailParticipantResponse(
                getParticipantId(match),
                profile.name(),
                profile.gender(),
                profile.level(),
                match.getParticipantType()
        );
    }

    private Long getParticipantId(VoteMatch match) {
        if (match.getParticipantType() == MatchParticipantType.GUEST) {
            return match.getGuest() != null ? match.getGuest().getId() : null;
        }
        return match.getVoteAttendance() != null ? match.getVoteAttendance().getId() : null;
    }

    private AdminMatchParticipantProfileResponse toMemberProfile(VoteAttendance attendance,
                                                                 Map<Long, ClubMember> clubMembersByMemberId) {
        if (attendance.getMember() == null) {
            return new AdminMatchParticipantProfileResponse("알 수 없음", null, null);
        }
        ClubMember clubMember = clubMembersByMemberId.get(attendance.getMember().getId());
        if (clubMember != null) {
            return new AdminMatchParticipantProfileResponse(
                    clubMember.getName(),
                    clubMember.getGender(),
                    clubMember.getCourt() != null ? clubMember.getCourt().name() : null
            );
        }
        return new AdminMatchParticipantProfileResponse(attendance.getMember().getNickname(), null, null);
    }

    private Map<Long, ClubMember> getClubMembersByMemberIdSafely(Iterable<VoteAttendance> attendances) {
        try {
            return clubMemberLookupService.getClubMembersByMemberId(
                    StreamSupport.stream(attendances.spliterator(), false)
                            .map(VoteAttendance::getMember)
                            .filter(Objects::nonNull)
                            .toList()
            );
        } catch (RuntimeException exception) {
            log.error("Failed to resolve club member profiles for match participants.", exception);
            return Map.of();
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

    private Set<Long> getVoteIds(Collection<Vote> votes) {
        return votes.stream()
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

    private ConfirmedParticipantScope resolveConfirmedParticipantScope(Vote baseVote, List<Vote> relatedVotes) {
        Set<Long> relatedVoteIds = getVoteIds(relatedVotes);
        List<VoteAttendance> confirmedAttendances = VoteAttendanceWaitlistResolver.resolveConfirmedAttendancesByVote(
                voteAttendanceRepository.findAllActiveWithMemberByVoteIds(relatedVoteIds)
        );
        List<Guest> confirmedGuests = VoteGuestWaitlistResolver.resolveConfirmedGuests(
                baseVote,
                relatedVotes,
                confirmedAttendances,
                guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(relatedVoteIds),
                LocalDateTime.now()
        );
        return new ConfirmedParticipantScope(
                relatedVoteIds,
                confirmedAttendances,
                confirmedAttendances.stream()
                        .map(VoteAttendance::getId)
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                confirmedGuests,
                confirmedGuests.stream()
                        .map(Guest::getId)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
        );
    }

    private void validateGame(AdminMatchGameRequest game) {
        Set<ParticipantKey> participantKeys = Stream.concat(game.team1().stream(), game.team2().stream())
                .map(ParticipantKey::from)
                .collect(Collectors.toSet());
        if (game.team1().size() != TEAM_SIZE
                || game.team2().size() != TEAM_SIZE
                || participantKeys.size() != PARTICIPANTS_PER_GAME) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }
    }

    private record ParticipantKey(MatchParticipantType participantType, Long participantId) {
        private static ParticipantKey from(AdminMatchParticipantRequest participantRequest) {
            return new ParticipantKey(participantRequest.participantType(), participantRequest.participantId());
        }
    }

    private record ConfirmedParticipantScope(
            Set<Long> relatedVoteIds,
            List<VoteAttendance> confirmedAttendances,
            Set<Long> confirmedAttendanceIds,
            List<Guest> confirmedGuests,
            Set<Long> confirmedGuestIds
    ) {
    }

    private record MatchParticipantSource(MatchParticipantType participantType,
                                          VoteAttendance voteAttendance,
                                          Guest guest,
                                          String name,
                                          Gender gender) {
        private static MatchParticipantSource member(VoteAttendance voteAttendance,
                                                     AdminMatchParticipantProfileResponse profile) {
            return new MatchParticipantSource(
                    MatchParticipantType.MEMBER,
                    voteAttendance,
                    null,
                    profile.name(),
                    profile.gender()
            );
        }

        private static MatchParticipantSource guest(Guest guest) {
            return new MatchParticipantSource(
                    MatchParticipantType.GUEST,
                    null,
                    guest,
                    guest.getName(),
                    guest.getGender()
            );
        }
    }

    private record GeneratedParticipant(ParticipantKey participantKey, MatchParticipantSource source) {
    }

    private record TeamSplit(List<GeneratedParticipant> team1, List<GeneratedParticipant> team2) {
    }

    private record GeneratedGame(List<GeneratedParticipant> team1,
                                 List<GeneratedParticipant> team2,
                                 int score) {
    }

    private record GeneratedCourtSchedule(List<GeneratedGame> games, int score) {
    }

    private record TargetGameDistribution(int mixedCount,
                                          int menCount,
                                          int womenCount,
                                          int otherCount) {
    }

    private record ParticipantPair(ParticipantKey first, ParticipantKey second) {
        private static ParticipantPair of(ParticipantKey first, ParticipantKey second) {
            if (compare(first, second) <= 0) {
                return new ParticipantPair(first, second);
            }
            return new ParticipantPair(second, first);
        }

        private static int compare(ParticipantKey first, ParticipantKey second) {
            int typeComparison = first.participantType().name().compareTo(second.participantType().name());
            if (typeComparison != 0) {
                return typeComparison;
            }
            return first.participantId().compareTo(second.participantId());
        }
    }

    private record GameKey(Integer courtNumber, Integer matchNumber) {
    }

    private record CourtGameKey(Integer courtNumber, Integer matchNumber) {
    }

    private enum GameCategory {
        MIXED,
        MEN,
        WOMEN,
        OTHER
    }
}
