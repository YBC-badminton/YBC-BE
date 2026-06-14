package com.ybc.ybc.recruitments.service;

import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.recruitments.domain.Recruitment;
import com.ybc.ybc.recruitments.dto.request.RecruitmentCreateRequest;
import com.ybc.ybc.recruitments.dto.request.RecruitmentUpdateRequest;
import com.ybc.ybc.recruitments.dto.response.RecruitmentListResponse;
import com.ybc.ybc.recruitments.dto.response.RecruitmentMessageResponse;
import com.ybc.ybc.recruitments.dto.response.RecruitmentResponse;
import com.ybc.ybc.recruitments.dto.response.RecruitmentSummaryResponse;
import com.ybc.ybc.recruitments.repository.RecruitmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecruitmentService {
    private final RecruitmentRepository recruitmentRepository;
    private final Clock clock;

    public RecruitmentListResponse getRecruitments() {
        List<Recruitment> recruitments = recruitmentRepository.findAll(Sort.by(Sort.Order.desc("id")));
        return new RecruitmentListResponse(
                recruitments.stream()
                        .map(RecruitmentSummaryResponse::from)
                        .toList()
        );
    }

    public RecruitmentResponse getRecruitment(Long recruitmentId) {
        return RecruitmentResponse.from(getRecruitmentById(recruitmentId));
    }

    public RecruitmentMessageResponse getCurrentRecruitmentMessage() {
        LocalDateTime now = LocalDateTime.now(clock);
        return findCurrentRecruitment(now)
                .map(RecruitmentMessageResponse::from)
                .orElseGet(RecruitmentMessageResponse::closed);
    }

    @Transactional
    public RecruitmentResponse createRecruitment(RecruitmentCreateRequest request) {
        Recruitment recruitment = Recruitment.builder()
                .term(request.term())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .firstResultDate(request.firstResultDate())
                .finalResultDate(request.finalResultDate())
                .interviewDate(request.interviewDate())
                .interviewLocation(request.interviewLocation())
                .interviewFirstTime(request.interviewFirstTime())
                .interviewSecondTime(request.interviewSecondTime())
                .otDate(request.otDate())
                .recruitmentMessage(request.recruitmentMessage())
                .membershipFee(request.membershipFee())
                .activityPeriod(request.activityPeriod())
                .build();

        return RecruitmentResponse.from(recruitmentRepository.save(recruitment));
    }

    @Transactional
    public RecruitmentResponse updateRecruitment(Long recruitmentId, RecruitmentUpdateRequest request) {
        Recruitment recruitment = getRecruitmentById(recruitmentId);

        recruitment.update(
                request.term(),
                request.startAt(),
                request.endAt(),
                request.firstResultDate(),
                request.finalResultDate(),
                request.interviewDate(),
                request.interviewLocation(),
                request.interviewFirstTime(),
                request.interviewSecondTime(),
                request.otDate(),
                request.recruitmentMessage(),
                request.membershipFee(),
                request.activityPeriod()
        );

        return RecruitmentResponse.from(recruitment);
    }

    private Recruitment getRecruitmentById(Long recruitmentId) {
        return recruitmentRepository.findById(recruitmentId)
                .orElseThrow(() -> new YbcException(ExceptionCode.RECRUITMENT_NOT_FOUND));
    }

    private Optional<Recruitment> findCurrentRecruitment(LocalDateTime now) {
        return recruitmentRepository.findFirstByStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(now, now);
    }
}
