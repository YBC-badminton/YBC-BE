package com.ybc.ybc.members.domain;

import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "club_member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubMember {
    private static final int MIN_COURT = 1;
    private static final int MAX_COURT = 4;
    private static final int UNASSIGNED_COURT = 0;
    private static final String DEFAULT_TERM = "미정";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", unique = true)
    private Long applicationId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String university;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false, length = 2)
    private String age;

    @Column(nullable = false, length = 30)
    private String term;

    @Column
    private Integer court;

    @Builder
    private ClubMember(Long applicationId,
                       String name,
                       String university,
                       String phone,
                       String age,
                       String term,
                       Integer court) {
        validateCourt(court);
        this.applicationId = applicationId;
        this.name = name;
        this.university = university;
        this.phone = phone;
        this.age = age;
        this.term = term;
        this.court = court;
    }

    public static ClubMember create(String name,
                                    String university,
                                    String phone,
                                    String age,
                                    String term,
                                    Integer court) {
        return ClubMember.builder()
                .name(name)
                .university(university)
                .phone(phone)
                .age(age)
                .term(term)
                .court(court)
                .build();
    }

    public static ClubMember createFromApplication(Long applicationId,
                                                   String name,
                                                   String university,
                                                   String phone,
                                                   String age) {
        return ClubMember.builder()
                .applicationId(applicationId)
                .name(name)
                .university(university)
                .phone(phone)
                .age(age)
                .term(DEFAULT_TERM)
                .court(UNASSIGNED_COURT)
                .build();
    }

    public void update(String name,
                       String age,
                       String university,
                       String term,
                       Integer court) {
        validateCourt(court);
        this.name = name;
        this.age = age;
        this.university = university;
        this.term = term;
        this.court = court;
    }

    public void syncFromApplication(String name,
                                    String university,
                                    String phone,
                                    String age) {
        this.name = name;
        this.university = university;
        this.phone = phone;
        this.age = age;
        if (this.term == null || this.term.isBlank()) {
            this.term = DEFAULT_TERM;
        }
    }

    public Integer getCourt() {
        return court != null && court == UNASSIGNED_COURT
                ? null
                : court;
    }

    private void validateCourt(Integer court) {
        if (court != null && court != UNASSIGNED_COURT && (court < MIN_COURT || court > MAX_COURT)) {
            throw new YbcException(ExceptionCode.INVALID_MEMBER_COURT);
        }
    }
}
