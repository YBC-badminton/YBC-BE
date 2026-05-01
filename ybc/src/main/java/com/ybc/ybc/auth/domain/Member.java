package com.ybc.ybc.auth.domain;

import com.ybc.ybc.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_members_provider_social_id",
                columnNames = {"social_provider", "social_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", nullable = false, length = 20)
    private SocialProvider socialProvider;

    @Column(name = "social_id", nullable = false, length = 100)
    private String socialId;

    @Column(nullable = false, length = 100)
    private String nickname;
커
    @Column(length = 255)
    private String email;

    @Column(name = "last_login_at", nullable = false)
    private LocalDateTime lastLoginAt;

    private Member(SocialProvider socialProvider,
                   String socialId,
                   String nickname,
                   String email) {
        this.socialProvider = socialProvider;
        this.socialId = socialId;
        this.nickname = nickname;
        this.email = email;
    }

    public static Member createSocialMember(SocialProvider socialProvider,
                                            String socialId,
                                            String nickname,
                                            String email) {
        return new Member(socialProvider, socialId, nickname, email);
    }

    public void updateProfile(String nickname, String email) {
        this.nickname = nickname;
        this.email = email;
        this.lastLoginAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.lastLoginAt == null) {
            this.lastLoginAt = LocalDateTime.now();
        }
    }
}
