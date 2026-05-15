package com.ybc.ybc.vote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Table(name = "vote_match")
@NoArgsConstructor(access = PROTECTED)
public class VoteMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_id", nullable = false)
    private Vote vote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_group_id", nullable = false)
    private VoteMatchGroup matchGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 20)
    private MatchParticipantType participantType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_attendance_id")
    private VoteAttendance voteAttendance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    private Guest guest;

    @Column(name = "court_number", nullable = false)
    private Integer courtNumber;

    @Column(name = "match_number", nullable = false)
    private Integer matchNumber;

    @Column(name = "team_number", nullable = false)
    private Integer teamNumber;

    @Builder
    public VoteMatch(Vote vote,
                     VoteMatchGroup matchGroup,
                     MatchParticipantType participantType,
                     VoteAttendance voteAttendance,
                     Guest guest,
                     Integer courtNumber,
                     Integer matchNumber,
                     Integer teamNumber) {
        this.vote = vote;
        this.matchGroup = matchGroup;
        this.participantType = participantType;
        this.voteAttendance = voteAttendance;
        this.guest = guest;
        this.courtNumber = courtNumber;
        this.matchNumber = matchNumber;
        this.teamNumber = teamNumber;
    }
}
