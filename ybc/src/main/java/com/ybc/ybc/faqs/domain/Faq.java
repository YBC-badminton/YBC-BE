package com.ybc.ybc.faqs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "faq")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "faq_id")
    private Long id;

    @Column(nullable = false, length = 255)
    private String question;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Builder
    private Faq(String question, String answer) {
        this.question = question;
        this.answer = answer;
    }

    public static Faq create(String question, String answer) {
        return Faq.builder()
                .question(question)
                .answer(answer)
                .build();
    }

    public void update(String question, String answer) {
        this.question = question;
        this.answer = answer;
    }
}
