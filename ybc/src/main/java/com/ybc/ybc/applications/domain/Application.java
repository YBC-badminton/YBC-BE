package com.ybc.ybc.applications.domain;

import com.ybc.ybc.global.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import static lombok.AccessLevel.PROTECTED;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
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
    @CollectionTable(name = "application_interview_times", joinColumns = @JoinColumn(name = "application_id"))
    private List<String> interviewTimes = new ArrayList<>();

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
        this.phone = phone;
        this.university = university;
        this.major = major;
        this.introduction = introduction;
        this.motivation = motivation;
        this.equipment = equipment;
        this.discoverySource = discoverySource;
        this.discoveryEtc = discoveryEtc;
        this.wantsStaff = wantsStaff;
        this.finalComment = finalComment;
        this.status = status;
        this.email = email;
    }

    public void addInterviewTime(String time) {
        interviewTimes.add(time);
    }
}
