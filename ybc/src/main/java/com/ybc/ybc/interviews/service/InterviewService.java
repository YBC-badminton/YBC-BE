package com.ybc.ybc.interviews.service;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.Status;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.interviews.domain.Interview;
import com.ybc.ybc.interviews.dto.request.InterviewUpdateRequest;
import com.ybc.ybc.interviews.dto.response.InterviewManagementItemResponse;
import com.ybc.ybc.interviews.dto.response.InterviewManagementResponse;
import com.ybc.ybc.interviews.dto.response.InterviewSummaryResponse;
import com.ybc.ybc.interviews.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewService {
    private static final List<Status> INTERVIEW_TARGET_STATUSES = List.of(
            Status.FIRST_PASS,
            Status.FINAL_PASS,
            Status.FAIL,
            Status.HOLD
    );

    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;

    public InterviewManagementResponse getInterviewManagement() {
        List<Application> interviewTargets = getInterviewTargets();
        return toResponse(interviewTargets);
    }

    // 면접 관리 수정
    @Transactional
    public InterviewManagementResponse updateInterview(Long applicationId, InterviewUpdateRequest request) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new YbcException(ExceptionCode.APPLICATION_NOT_FOUND));

        if (!application.getStatusOrDefault().isInterviewTarget()) {
            throw new YbcException(ExceptionCode.APPLICATION_NOT_INTERVIEW_TARGET);
        }

        application.updateStatus(Status.fromInterviewStatus(request.status()));

        interviewRepository.findByApplication_Id(applicationId)
                .ifPresentOrElse(
                        interview -> interview.update(request.scheduledAt(), request.memo()),
                        () -> saveInterviewIfNeeded(application, request)
                );

        return toResponse(getInterviewTargets());
    }

    private void saveInterviewIfNeeded(Application application, InterviewUpdateRequest request) {
        if (request.scheduledAt() == null && request.memo() == null) {
            return;
        }

        interviewRepository.save(Interview.create(application, request.scheduledAt(), request.memo()));
    }

    private List<Application> getInterviewTargets() {
        return applicationRepository.findAllByStatusIn(
                INTERVIEW_TARGET_STATUSES,
                Sort.by(Sort.Order.asc("id"))
        );
    }

    private InterviewManagementResponse toResponse(List<Application> applications) {
        if (applications.isEmpty()) {
            return new InterviewManagementResponse(
                    summarize(applications),
                    List.of()
            );
        }

        Map<Long, Interview> interviewByApplicationId = interviewRepository.findAllByApplication_IdIn(
                        applications.stream()
                                .map(Application::getId)
                                .toList()
                ).stream()
                .collect(Collectors.toMap(Interview::getApplicationId, Function.identity()));

        List<InterviewManagementItemResponse> interviewList = applications.stream()
                .map(application -> InterviewManagementItemResponse.from(
                        application,
                        interviewByApplicationId.get(application.getId())
                ))
                .toList();

        return new InterviewManagementResponse(
                summarize(applications),
                interviewList
        );
    }

    private InterviewSummaryResponse summarize(List<Application> applications) {
        return new InterviewSummaryResponse(
                applications.size(),
                applications.stream().filter(application -> application.getStatusOrDefault() == Status.FINAL_PASS).count(),
                applications.stream().filter(application -> application.getStatusOrDefault() == Status.FAIL).count(),
                applications.stream().filter(application -> application.getStatusOrDefault() == Status.HOLD).count()
        );
    }
}
