package com.ybc.ybc.recruitments.controller;

import com.ybc.ybc.recruitments.dto.response.RecruitmentListResponse;
import com.ybc.ybc.recruitments.service.RecruitmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TODO: 관리자 접근만 허용
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/recruitments")
public class AdminRecruitmentController {
    private final RecruitmentService recruitmentService;

    // GET : 전체 기수 리스트 조회
    @GetMapping
    public ResponseEntity<RecruitmentListResponse> getRecruitments() {
        return ResponseEntity.ok(recruitmentService.getRecruitments());
    }
}
