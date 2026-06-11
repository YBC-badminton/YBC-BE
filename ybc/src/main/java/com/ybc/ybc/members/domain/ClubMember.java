package com.ybc.ybc.members.domain;

import com.ybc.ybc.applications.domain.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;

@Entity
@Getter
@Table(name = "club_member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubMember {
    private static final String DEFAULT_TERM = "미정";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", unique = true)
    private Long applicationId;

    @Column(name = "member_id", unique = true)
    private Long memberId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String university;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "is_mapo_resident")
    private Boolean mapoResident;

    @Column(nullable = false, length = 2)
    private String age;

    @Column(nullable = false, length = 30)
    private String term;

    @Builder
    private ClubMember(Long applicationId,
                       Long memberId,
                       String name,
                       String university,
                       String phone,
                       String email,
                       Gender gender,
                       Boolean mapoResident,
                       String age,
                       String term) {
        this.applicationId = applicationId;
        this.memberId = memberId;
        this.name = name;
        this.university = university;
        this.phone = phone;
        this.email = normalizeEmail(email);
        this.gender = gender;
        this.mapoResident = mapoResident;
        this.age = age;
        this.term = term;
    }

    public static ClubMember create(String name,
                                    String university,
                                    String phone,
                                    String age,
                                    String term) {
        return create(name, university, phone, age, term, null, null);
    }

    public static ClubMember create(String name,
                                    String university,
                                    String phone,
                                    String age,
                                    String term,
                                    String email,
                                    Gender gender) {
        return create(name, university, phone, age, term, false, email, gender);
    }

    public static ClubMember create(String name,
                                    String university,
                                    String phone,
                                    String age,
                                    String term,
                                    Boolean mapoResident,
                                    String email,
                                    Gender gender) {
        return ClubMember.builder()
                .name(name)
                .university(university)
                .phone(phone)
                .email(email)
                .gender(gender)
                .mapoResident(mapoResident)
                .age(age)
                .term(term)
                .build();
    }

    public static ClubMember createFromApplication(Long applicationId,
                                                   String name,
                                                   String university,
                                                   String phone,
                                                   String age) {
        return createFromApplication(applicationId, name, university, phone, age, null, null);
    }

    public static ClubMember createFromApplication(Long applicationId,
                                                   String name,
                                                   String university,
                                                   String phone,
                                                   String age,
                                                   String email,
                                                   Gender gender) {
        return createFromApplication(applicationId, name, university, phone, age, null, email, gender);
    }

    public static ClubMember createFromApplication(Long applicationId,
                                                   String name,
                                                   String university,
                                                   String phone,
                                                   String age,
                                                   Boolean mapoResident,
                                                   String email,
                                                   Gender gender) {
        return ClubMember.builder()
                .applicationId(applicationId)
                .name(name)
                .university(university)
                .phone(phone)
                .email(email)
                .gender(gender)
                .mapoResident(mapoResident)
                .age(age)
                .term(DEFAULT_TERM)
                .build();
    }

    public void update(String name,
                       String age,
                       String university,
                       String term) {
        update(name, age, university, term, null, null, null);
    }

    public void update(String name,
                       String age,
                       String university,
                       String term,
                       String email,
                       Gender gender) {
        update(name, age, university, term, null, email, gender);
    }

    public void update(String name,
                       String age,
                       String university,
                       String term,
                       Boolean mapoResident,
                       String email,
                       Gender gender) {
        this.name = name;
        this.age = age;
        this.university = university;
        this.term = term;
        if (mapoResident != null) {
            this.mapoResident = mapoResident;
        }
        if (email != null) {
            this.email = normalizeEmail(email);
        }
        if (gender != null) {
            this.gender = gender;
        }
    }

    public void syncFromApplication(String name,
                                    String university,
                                    String phone,
                                    String age) {
        syncFromApplication(name, university, phone, age, null, null, null);
    }

    public void syncFromApplication(String name,
                                    String university,
                                    String phone,
                                    String age,
                                    String email,
                                    Gender gender) {
        syncFromApplication(name, university, phone, age, null, email, gender);
    }

    public void syncFromApplication(String name,
                                    String university,
                                    String phone,
                                    String age,
                                    Boolean mapoResident,
                                    String email,
                                    Gender gender) {
        this.name = name;
        this.university = university;
        this.phone = phone;
        this.age = age;
        this.email = normalizeEmail(email);
        this.gender = gender;
        if (this.mapoResident == null && mapoResident != null) {
            this.mapoResident = mapoResident;
        }
        if (this.term == null || this.term.isBlank()) {
            this.term = DEFAULT_TERM;
        }
    }

    public void linkMember(Long memberId) {
        if (memberId == null || this.memberId != null) {
            return;
        }
        this.memberId = memberId;
    }

    public boolean isMapoResident() {
        return Boolean.TRUE.equals(mapoResident);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
