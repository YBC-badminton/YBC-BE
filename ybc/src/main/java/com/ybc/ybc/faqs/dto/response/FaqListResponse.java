package com.ybc.ybc.faqs.dto.response;

import java.util.List;

public record FaqListResponse(
        List<FaqResponse> faqs
) {
}
