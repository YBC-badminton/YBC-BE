package com.ybc.ybc.contents.domain;

import com.ybc.ybc.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "home_content")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomeContent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "home_content_id")
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String clubIntroduction;

    @Column(nullable = false)
    private String activityImageUrl;

    @Column(nullable = false)
    private Integer regularMeetingCount;

    @Column(nullable = false)
    private Integer memberCount;

    private HomeContent(String clubIntroduction, String activityImageUrl, Integer regularMeetingCount, Integer memberCount) {
        this.clubIntroduction = clubIntroduction;
        this.activityImageUrl = activityImageUrl;
        this.regularMeetingCount = regularMeetingCount;
        this.memberCount = memberCount;
    }

    public static HomeContent create(
            String clubIntroduction,
            String activityImageUrl,
            Integer regularMeetingCount,
            Integer memberCount
    ) {
        return new HomeContent(clubIntroduction, activityImageUrl, regularMeetingCount, memberCount);
    }

    public void update(
            String clubIntroduction,
            String activityImageUrl,
            Integer regularMeetingCount,
            Integer memberCount
    ) {
        this.clubIntroduction = clubIntroduction;
        this.activityImageUrl = activityImageUrl;
        this.regularMeetingCount = regularMeetingCount;
        this.memberCount = memberCount;
    }
}
