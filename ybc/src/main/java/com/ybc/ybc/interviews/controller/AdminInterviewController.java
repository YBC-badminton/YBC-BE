package com.ybc.ybc.interviews.controller;

import com.ybc.ybc.interviews.dto.request.InterviewUpdateRequest;
import com.ybc.ybc.interviews.dto.response.InterviewManagementResponse;
import com.ybc.ybc.interviews.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/interviews")
public class AdminInterviewController {
    private final InterviewService interviewService;

    /**
     * GET /admin/interviews
     * 면접 관리 리스트 조회
     */
    @GetMapping
    public InterviewManagementResponse getInterviewManagement() {
        return interviewService.getInterviewManagement();
    }

    /**
     * PATCH /admin/interviews/{applicationId}
     * 면접 관리 수정
     */
    @PatchMapping("/{applicationId}")
    public InterviewManagementResponse updateInterview(@PathVariable Long applicationId,
                                                       @Valid @RequestBody InterviewUpdateRequest request) {
        return interviewService.updateInterview(applicationId, request);
    }
}
