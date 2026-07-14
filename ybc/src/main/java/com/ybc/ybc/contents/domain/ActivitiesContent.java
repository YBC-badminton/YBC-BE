package com.ybc.ybc.contents.domain;

import com.ybc.ybc.global.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "activities_content")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivitiesContent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activities_content_id")
    private Long id;

    @Column(nullable = false)
    private String mainTitle;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String subDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String regularMeetingDescription;

    @ElementCollection
    @CollectionTable(name = "activities_regular_meeting_images", joinColumns = @JoinColumn(name = "activities_content_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "image_url", nullable = false)
    private List<String> regularMeetingImageUrls = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "activities_events", joinColumns = @JoinColumn(name = "activities_content_id"))
    @OrderColumn(name = "display_order")
    private List<ActivitiesEvent> events = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "activities_after_party_descriptions", joinColumns = @JoinColumn(name = "activities_content_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private List<String> afterPartyDescriptions = new ArrayList<>();

    @Column(nullable = false)
    private String afterPartyImageUrl;

    private ActivitiesContent(
            String mainTitle,
            String subDescription,
            String regularMeetingDescription,
            List<String> regularMeetingImageUrls,
            List<ActivitiesEvent> events,
            List<String> afterPartyDescriptions,
            String afterPartyImageUrl
    ) {
        this.mainTitle = mainTitle;
        this.subDescription = subDescription;
        this.regularMeetingDescription = regularMeetingDescription;
        this.afterPartyImageUrl = afterPartyImageUrl;
        replaceRegularMeetingImageUrls(regularMeetingImageUrls);
        replaceEvents(events);
        replaceAfterPartyDescriptions(afterPartyDescriptions);
    }

    public static ActivitiesContent create(
            String mainTitle,
            String subDescription,
            String regularMeetingDescription,
            List<String> regularMeetingImageUrls,
            List<ActivitiesEvent> events,
            List<String> afterPartyDescriptions,
            String afterPartyImageUrl
    ) {
        return new ActivitiesContent(
                mainTitle,
                subDescription,
                regularMeetingDescription,
                regularMeetingImageUrls,
                events,
                afterPartyDescriptions,
                afterPartyImageUrl
        );
    }

    public void update(
            String mainTitle,
            String subDescription,
            String regularMeetingDescription,
            List<String> regularMeetingImageUrls,
            List<ActivitiesEvent> events,
            List<String> afterPartyDescriptions,
            String afterPartyImageUrl
    ) {
        this.mainTitle = mainTitle;
        this.subDescription = subDescription;
        this.regularMeetingDescription = regularMeetingDescription;
        this.afterPartyImageUrl = afterPartyImageUrl;
        replaceRegularMeetingImageUrls(regularMeetingImageUrls);
        replaceEvents(events);
        replaceAfterPartyDescriptions(afterPartyDescriptions);
    }

    private void replaceRegularMeetingImageUrls(List<String> regularMeetingImageUrls) {
        this.regularMeetingImageUrls.clear();
        this.regularMeetingImageUrls.addAll(regularMeetingImageUrls);
    }

    private void replaceEvents(List<ActivitiesEvent> events) {
        this.events.clear();
        this.events.addAll(events);
    }

    private void replaceAfterPartyDescriptions(List<String> afterPartyDescriptions) {
        this.afterPartyDescriptions.clear();
        this.afterPartyDescriptions.addAll(afterPartyDescriptions);
    }
}
