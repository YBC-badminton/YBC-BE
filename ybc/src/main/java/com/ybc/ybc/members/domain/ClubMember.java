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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String university;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false)
    private int age;

    @Column(nullable = false, length = 30)
    private String term;

    @Column(nullable = false)
    private int court;

    @Builder
    private ClubMember(String name,
                       String university,
                       String phone,
                       int age,
                       String term,
                       int court) {
        validateCourt(court);
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
                                    int age,
                                    String term,
                                    int court) {
        return ClubMember.builder()
                .name(name)
                .university(university)
                .phone(phone)
                .age(age)
                .term(term)
                .court(court)
                .build();
    }

    public void update(String name,
                       int age,
                       String university,
                       String term,
                       int court) {
        validateCourt(court);
        this.name = name;
        this.age = age;
        this.university = university;
        this.term = term;
        this.court = court;
    }

    private void validateCourt(int court) {
        if (court < MIN_COURT || court > MAX_COURT) {
            throw new YbcException(ExceptionCode.INVALID_MEMBER_COURT);
        }
    }
}
