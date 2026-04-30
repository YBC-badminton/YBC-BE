package com.ybc.ybc.applications.controller;

import com.ybc.ybc.applications.dto.request.ApplicationStatusUpdateRequest;
import com.ybc.ybc.applications.dto.response.ApplicantDetailResponse;
import com.ybc.ybc.applications.dto.response.ApplicantListResponse;
import com.ybc.ybc.applications.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/applications")
public class AdminApplicationController {
    private final ApplicationService applicationService;

    /**
     * GET /aamin/applications
     * 지원서 리스트 조회
     */
    @GetMapping
    public ApplicantListResponse getApplicants(@RequestParam(required = false) String term) {
        return applicationService.getApplicants(term);
    }

    /**
     * GET /admin/applications/{applicationId}
     * 지원서 상세 조회
     */
    @GetMapping("/{applicationId}")
    public ApplicantDetailResponse getApplicant(@PathVariable Long applicationId) {
        return applicationService.getApplicant(applicationId);
    }

    /**
     * PATCH /admin/applications/{applicationId}
     * 지원서 상태 수정
     */
    @PatchMapping("/{applicationId}")
    public ApplicantDetailResponse updateApplicantStatus(@PathVariable Long applicationId,
                                                         @Valid @RequestBody ApplicationStatusUpdateRequest request) {
        return applicationService.updateApplicantStatus(applicationId, request.status());
    }
}
