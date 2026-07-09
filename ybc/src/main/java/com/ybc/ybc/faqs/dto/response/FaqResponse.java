package com.ybc.ybc.faqs.dto.response;

import com.ybc.ybc.faqs.domain.Faq;

public record FaqResponse(
        Long faqId,
        String question,
        String answer
) {
    public static FaqResponse from(Faq faq) {
        return new FaqResponse(
                faq.getId(),
                faq.getQuestion(),
                faq.getAnswer()
        );
    }
}
