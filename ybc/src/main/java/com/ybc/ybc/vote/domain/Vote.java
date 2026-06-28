package com.ybc.ybc.vote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Table(
        name = "vote",
        indexes = @Index(name = "idx_vote_original_vote_id", columnList = "original_vote_id")
)
@NoArgsConstructor(access = PROTECTED)
public class Vote {
    public static final String ADDITIONAL_PARTICIPATION_SUFFIX = "-추가참여";
    private static final String ADDITIONAL_PARTICIPATION_FEE_MESSAGE = "추가참여비 4000원";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_id", nullable = false)
    private Long id;

    @Column(name = "activity_type", nullable = false)
    @Convert(converter = ActivityTypeConverter.class)
    private ActivityType activityType;

    @Column(nullable = false)
    private String title;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "activity_time", nullable = false)
    private String activityTime;

    @Column(nullable = false)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "vote_start_at", nullable = false)
    private LocalDateTime voteStartAt;

    @Column(name = "vote_end_at", nullable = false)
    private LocalDateTime voteEndAt;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "opened_by_member_id")
    private Long openedByMemberId;

    @Column(name = "opened_by_nickname")
    private String openedByNickname;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_vote_id")
    private Vote originalVote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Vote(ActivityType activityType,
                String title,
                LocalDate activityDate,
                String activityTime,
                String location,
                String memo,
                LocalDateTime voteStartAt,
                LocalDateTime voteEndAt,
                int capacity,
                Long openedByMemberId,
                String openedByNickname,
                Vote originalVote) {
        this.activityType = activityType;
        this.title = title;
        this.activityDate = activityDate;
        this.activityTime = activityTime;
        this.location = location;
        this.memo = memo;
        this.voteStartAt = voteStartAt;
        this.voteEndAt = voteEndAt;
        this.capacity = capacity;
        this.openedByMemberId = openedByMemberId;
        this.openedByNickname = openedByNickname;
        this.originalVote = originalVote;
    }

    public static Vote createAdditionalVote(Vote originalVote,
                                            LocalDateTime voteStartAt,
                                            LocalDateTime voteEndAt,
                                            int capacity) {
        Vote baseVote = originalVote.getBaseVote();
        return Vote.builder()
                .activityType(ActivityType.REGULAR)
                .title(baseVote.getTitle() + ADDITIONAL_PARTICIPATION_SUFFIX)
                .activityDate(baseVote.getActivityDate())
                .activityTime(baseVote.getActivityTime())
                .location(baseVote.getLocation())
                .memo(buildAdditionalMemo(baseVote.getMemo()))
                .voteStartAt(voteStartAt)
                .voteEndAt(voteEndAt)
                .capacity(capacity)
                .originalVote(baseVote)
                .build();
    }

    public void update(ActivityType activityType,
                       String title,
                       LocalDate activityDate,
                       String activityTime,
                       String location,
                       String memo,
                       LocalDateTime voteStartAt,
                       LocalDateTime voteEndAt,
                       int capacity) {
        this.activityType = activityType;
        this.title = title;
        this.activityDate = activityDate;
        this.activityTime = activityTime;
        this.location = location;
        this.memo = memo;
        this.voteStartAt = voteStartAt;
        this.voteEndAt = voteEndAt;
        this.capacity = capacity;
    }

    public boolean isAdditionalParticipationVote() {
        return originalVote != null || hasAdditionalParticipationSuffix();
    }

    public Vote getBaseVote() {
        return originalVote == null ? this : originalVote;
    }

    public void linkOriginalVote(Vote originalVote) {
        this.originalVote = originalVote == null ? null : originalVote.getBaseVote();
    }

    public String getBaseVoteTitle() {
        if (!hasAdditionalParticipationSuffix()) {
            return title;
        }
        return title.substring(0, title.length() - ADDITIONAL_PARTICIPATION_SUFFIX.length());
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    private static String buildAdditionalMemo(String originalMemo) {
        if (originalMemo == null || originalMemo.isBlank()) {
            return ADDITIONAL_PARTICIPATION_FEE_MESSAGE;
        }
        return originalMemo + System.lineSeparator() + ADDITIONAL_PARTICIPATION_FEE_MESSAGE;
    }

    private boolean hasAdditionalParticipationSuffix() {
        return title != null && title.endsWith(ADDITIONAL_PARTICIPATION_SUFFIX);
    }
}
