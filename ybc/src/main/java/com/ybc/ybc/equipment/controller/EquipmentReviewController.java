package com.ybc.ybc.equipment.controller;

import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.equipment.domain.EquipmentCategory;
import com.ybc.ybc.equipment.dto.request.EquipmentReviewCreateRequest;
import com.ybc.ybc.equipment.dto.response.EquipmentReviewCreateResponse;
import com.ybc.ybc.equipment.dto.response.EquipmentReviewDetailResponse;
import com.ybc.ybc.equipment.dto.response.EquipmentReviewListResponse;
import com.ybc.ybc.equipment.service.EquipmentReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class EquipmentReviewController {
    private final EquipmentReviewService equipmentReviewService;

    @GetMapping
    public ResponseEntity<EquipmentReviewListResponse> getReviews(
            @RequestParam(required = false) EquipmentCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "desc") String sort
    ) {
        return ResponseEntity.ok(equipmentReviewService.getReviews(category, page, size, sort));
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<EquipmentReviewDetailResponse> getReview(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal MemberPrincipal memberPrincipal
    ) {
        return ResponseEntity.ok(equipmentReviewService.getReview(reviewId, memberPrincipal.getId()));
    }

    @PostMapping
    public ResponseEntity<EquipmentReviewCreateResponse> createReview(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @Valid @RequestBody EquipmentReviewCreateRequest request
    ) {
        EquipmentReviewCreateResponse response = equipmentReviewService.createReview(memberPrincipal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
