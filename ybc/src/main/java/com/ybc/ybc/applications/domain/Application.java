package com.ybc.ybc.applications.domain;

import com.ybc.ybc.global.domain.BaseEntity;
import com.ybc.ybc.global.util.PhoneNumberUtils;
import jakarta.persistence.*;
import lombok.Builder;
import static lombok.AccessLevel.PROTECTED;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(
        name = "application",
        indexes = {
                @Index(name = "idx_application_name_phone_id", columnList = "name, phone, id"),
                @Index(name = "idx_application_term_created_at_id", columnList = "term, created_at, id")
        }
)
@NoArgsConstructor(access = PROTECTED)
public class Application extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(nullable = false)
    private int age;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false, length = 30)
    private String term;

    @Column(nullable = false)
    private String university;

    @Column(nullable = false)
    private String major;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String introduction;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String motivation;

    private String equipment;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "application_interview_times", joinColumns = @JoinColumn(name = "application_id"))
    private List<InterviewTime> interviewTimes = new ArrayList<>();

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DiscoverySource discoverySource;

    private String discoveryEtc;

    @Column(nullable = false)
    private boolean wantsStaff;

    @Column(columnDefinition = "TEXT")
    private String finalComment;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(nullable = false)
    private String email;

    @Builder
    public Application(String name,
                       String address,
                       Gender gender,
                       int age,
                       String phone,
                       String term,
                       String university,
                       String major,
                       String introduction,
                       String motivation,
                       String equipment,
                       DiscoverySource discoverySource,
                       String discoveryEtc,
                       boolean wantsStaff,
                       String finalComment,
                       Status status,
                       String email
                       ) {
        this.name = name;
        this.address = address;
        this.gender = gender;
        this.age = age;
        applyPhoneNumber(phone);
        this.term = term;
        this.university = university;
        this.major = major;
        this.introduction = introduction;
        this.motivation = motivation;
        this.equipment = equipment;
        this.discoverySource = discoverySource;
        this.discoveryEtc = discoveryEtc;
        this.wantsStaff = wantsStaff;
        this.finalComment = finalComment;
        this.status = status == null ? Status.NONE : status;
        this.email = email;
    }

    @PrePersist
    @PreUpdate
    private void normalizePhone() {
        this.phone = PhoneNumberUtils.normalize(phone);
    }

    public void addInterviewTime(InterviewTime time) {
        interviewTimes.add(time);
    }

    public void updateStatus(Status status) {
        this.status = status;
    }

    public Status getStatusOrDefault() {
        return status == null ? Status.NONE : status;
    }

    private void applyPhoneNumber(String phone) {
        this.phone = PhoneNumberUtils.normalize(phone);
    }
}
