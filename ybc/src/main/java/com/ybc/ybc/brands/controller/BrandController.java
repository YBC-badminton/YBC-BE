package com.ybc.ybc.brands.controller;

import com.ybc.ybc.brands.dto.response.BrandListResponse;
import com.ybc.ybc.brands.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/brands")
@RequiredArgsConstructor
public class BrandController {
    private final BrandService brandService;

    @GetMapping
    public ResponseEntity<BrandListResponse> getBrands(@RequestParam(required = false, defaultValue = "") String keyword) {
        return ResponseEntity.ok(brandService.getBrands(keyword));
    }
}
