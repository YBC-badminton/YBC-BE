package com.ybc.ybc.brands.dto.response;

import com.ybc.ybc.brands.domain.Brand;

public record BrandSummaryResponse(
        Long brandId,
        String name
) {
    public static BrandSummaryResponse from(Brand brand) {
        return new BrandSummaryResponse(
                brand.getId(),
                brand.getName()
        );
    }
}
