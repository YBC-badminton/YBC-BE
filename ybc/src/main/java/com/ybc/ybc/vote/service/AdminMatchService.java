package com.ybc.ybc.vote.service;

import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.domain.VoteMatch;
import com.ybc.ybc.vote.domain.VoteMatchGroup;
import com.ybc.ybc.vote.dto.request.AdminMatchCourtRequest;
import com.ybc.ybc.vote.dto.request.AdminMatchGameRequest;
import com.ybc.ybc.vote.dto.response.AdminMatchCourtResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchGameResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchRegistrationResponse;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.VoteMatchGroupRepository;
import com.ybc.ybc.vote.repository.VoteMatchRepository;
import com.ybc.ybc.vote.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final VoteMatchRepository voteMatchRepository;
    private final VoteMatchGroupRepository voteMatchGroupRepository;

    @Transactional
    public AdminMatchRegistrationResponse registerMatches(Long voteId, List<AdminMatchCourtRequest> request) {
        if (request == null || request.isEmpty()) {
            throw new YbcException(ExceptionCode.INVALID_MATCH_REQUEST);
        }

        Vote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new YbcException(ExceptionCode.VOTE_NOT_FOUND));
        Map<Long, VoteAttendance> attendanceMap = getAttendanceMap(voteId, request);

        voteMatchRepository.deleteAllByVote_Id(voteId);
        VoteMatchGroup matchGroup = voteMatchGroupRepository.findByVote_Id(voteId)
                .orElseGet(() -> voteMatchGroupRepository.saveAndFlush(VoteMatchGroup.create(vote)));
        voteMatchRepository.saveAllAndFlush(
                toMatchEntities(vote, matchGroup, request, attendanceMap)
        );

        return new AdminMatchRegistrationResponse(
                vote.getId(),
                matchGroup.getId(),
                request.size(),
                toResponse(request, attendanceMap)
        );
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

    private List<AdminMatchCourtResponse> toResponse(List<AdminMatchCourtRequest> request,
                                                     Map<Long, VoteAttendance> attendanceMap) {
        return request.stream()
                .map(court -> new AdminMatchCourtResponse(
                        court.courtNumber(),
                        court.courtMatches().stream()
                                .map(game -> new AdminMatchGameResponse(
                                        game.matchNumber(),
                                        toMemberNames(game.team1(), attendanceMap),
                                        toMemberNames(game.team2(), attendanceMap)
                                ))
                                .toList()
                ))
                .toList();
    }

    private List<String> toMemberNames(List<Long> attendanceIds, Map<Long, VoteAttendance> attendanceMap) {
        return attendanceIds.stream()
                .map(attendanceId -> attendanceMap.get(attendanceId).getMember().getNickname())
                .toList();
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
}
