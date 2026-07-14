package com.ybc.ybc.contents.dto.response;

import com.ybc.ybc.contents.domain.PrivacyPolicy;

import java.time.LocalDate;

public record PrivacyPolicyResponse(
        String content,
        LocalDate updatedAt
) {
    public static PrivacyPolicyResponse from(PrivacyPolicy privacyPolicy) {
        return new PrivacyPolicyResponse(
                privacyPolicy.getContent(),
                privacyPolicy.getUpdatedAt().toLocalDate()
        );
    }
}
