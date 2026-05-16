package com.ybc.ybc.brands.dto.response;

import java.util.List;

public record BrandListResponse(
        List<BrandSummaryResponse> brands
) {
}
