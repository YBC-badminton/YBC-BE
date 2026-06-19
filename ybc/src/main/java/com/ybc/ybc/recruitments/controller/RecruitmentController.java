package com.ybc.ybc.recruitments.controller;

import com.ybc.ybc.recruitments.dto.response.RecruitmentInterviewTimesResponse;
import com.ybc.ybc.recruitments.dto.response.RecruitmentMessageResponse;
import com.ybc.ybc.recruitments.service.RecruitmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/recruitments")
public class RecruitmentController {
    private final RecruitmentService recruitmentService;

    @GetMapping("/message")
    public ResponseEntity<RecruitmentMessageResponse> getRecruitmentMessage() {
        return ResponseEntity.ok(recruitmentService.getCurrentRecruitmentMessage());
    }

    @GetMapping("/interview-times")
    public ResponseEntity<RecruitmentInterviewTimesResponse> getCurrentRecruitmentInterviewTimes() {
        return ResponseEntity.ok(recruitmentService.getCurrentRecruitmentInterviewTimes());
    }
}
