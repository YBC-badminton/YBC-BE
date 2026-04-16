package com.ybc.ybc.recruitments.service;

import com.ybc.ybc.recruitments.domain.Recruitment;
import com.ybc.ybc.recruitments.dto.response.RecruitmentListResponse;
import com.ybc.ybc.recruitments.dto.response.RecruitmentSummaryResponse;
import com.ybc.ybc.recruitments.repository.RecruitmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecruitmentService {
    private final RecruitmentRepository recruitmentRepository;

    public RecruitmentListResponse getRecruitments() {
        List<Recruitment> recruitments = recruitmentRepository.findAll(Sort.by(Sort.Order.desc("id")));
        return new RecruitmentListResponse(
                recruitments.stream()
                        .map(RecruitmentSummaryResponse::from)
                        .toList()
        );
    }
}
