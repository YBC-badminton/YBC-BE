package com.ybc.ybc.vote.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "vote")
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

    public static Vote createAdditionalVote(Vote originalVote,
                                            LocalDateTime voteStartAt,
                                            LocalDateTime voteEndAt,
                                            int capacity) {
        return Vote.builder()
                .activityType(ActivityType.REGULAR)
                .title(originalVote.getTitle() + ADDITIONAL_PARTICIPATION_SUFFIX)
                .activityDate(originalVote.getActivityDate())
                .activityTime(originalVote.getActivityTime())
                .location(originalVote.getLocation())
                .memo(buildAdditionalMemo(originalVote.getMemo()))
                .voteStartAt(voteStartAt)
                .voteEndAt(voteEndAt)
                .capacity(capacity)
                .build();
    }

    public boolean isAdditionalParticipationVote() {
        return title != null && title.endsWith(ADDITIONAL_PARTICIPATION_SUFFIX);
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
}
