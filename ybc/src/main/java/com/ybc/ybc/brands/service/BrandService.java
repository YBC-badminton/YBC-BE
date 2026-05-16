package com.ybc.ybc.brands.service;

import com.ybc.ybc.brands.dto.response.BrandListResponse;
import com.ybc.ybc.brands.dto.response.BrandSummaryResponse;
import com.ybc.ybc.brands.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {
    private final BrandRepository brandRepository;

    public BrandListResponse getBrands(String keyword) {
        return new BrandListResponse(
                brandRepository.findByNameContainingIgnoreCaseOrderByNameAsc(keyword == null ? "" : keyword).stream()
                        .map(BrandSummaryResponse::from)
                        .toList()
        );
    }
}
