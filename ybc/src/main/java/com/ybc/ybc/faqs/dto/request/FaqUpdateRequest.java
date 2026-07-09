package com.ybc.ybc.faqs.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FaqUpdateRequest(
        @NotBlank(message = "질문을 입력해 주세요.")
        String question,
        @NotBlank(message = "답변을 입력해 주세요.")
        String answer
) {
}
