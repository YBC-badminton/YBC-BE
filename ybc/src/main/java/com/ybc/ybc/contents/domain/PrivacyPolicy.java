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
@Table(name = "privacy_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivacyPolicy extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "privacy_policy_id")
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private PrivacyPolicy(String content) {
        this.content = content;
    }

    public static PrivacyPolicy create(String content) {
        return new PrivacyPolicy(content);
    }

    public void update(String content) {
        this.content = content;
    }
}
