package com.ybc.ybc.recruitments.controller;

import com.ybc.ybc.recruitments.dto.request.RecruitmentCreateRequest;
import com.ybc.ybc.recruitments.dto.request.RecruitmentUpdateRequest;
import com.ybc.ybc.recruitments.dto.response.RecruitmentListResponse;
import com.ybc.ybc.recruitments.dto.response.RecruitmentResponse;
import com.ybc.ybc.recruitments.service.RecruitmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/recruitments")
public class AdminRecruitmentController {
    private final RecruitmentService recruitmentService;

    @GetMapping
    public ResponseEntity<RecruitmentListResponse> getRecruitments() {
        return ResponseEntity.ok(recruitmentService.getRecruitments());
    }

    @PostMapping
    public ResponseEntity<RecruitmentResponse> createRecruitment(@Valid @RequestBody RecruitmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recruitmentService.createRecruitment(request));
    }

    @PatchMapping("/{recruitmentId}")
    public ResponseEntity<RecruitmentResponse> updateRecruitment(@PathVariable Long recruitmentId,
                                                                 @Valid @RequestBody RecruitmentUpdateRequest request) {
        return ResponseEntity.ok(recruitmentService.updateRecruitment(recruitmentId, request));
    }
}
