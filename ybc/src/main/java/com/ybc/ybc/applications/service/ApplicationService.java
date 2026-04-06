package com.ybc.ybc.applications.service;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.Status;
import com.ybc.ybc.applications.dto.request.ApplicationRequest;
import com.ybc.ybc.applications.dto.response.ApplicantDetailResponse;
import com.ybc.ybc.applications.dto.response.ApplicantListResponse;
import com.ybc.ybc.applications.dto.response.ApplicantSummaryResponse;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {
    private final ApplicationRepository applicationRepository;

    // 지원서 저장
    @Transactional
    public void createApplication(ApplicationRequest request){
        Application application = request.toApplication();
        applicationRepository.save(application);
    }

    public ApplicantListResponse getApplicants() {
        List<ApplicantSummaryResponse> applicants = applicationRepository.findAll(
                        Sort.by(
                                Sort.Order.desc("createdAt"),
                                Sort.Order.desc("id")
                        )
                ).stream()
                .map(ApplicantSummaryResponse::from)
                .toList();
        return new ApplicantListResponse(applicants, applicants.size());
    }

    public ApplicantDetailResponse getApplicant(Long applicationId) {
        return ApplicantDetailResponse.from(getApplication(applicationId));
    }

    // 지원서 상태 변경(1차합격, 최종합격, 불합격, 보류)
    @Transactional
    public ApplicantDetailResponse updateApplicantStatus(Long applicationId, String statusValue) {
        Application application = getApplication(applicationId);
        Status status = Status.from(statusValue);
        application.updateStatus(status);
        return ApplicantDetailResponse.from(application);
    }

    private Application getApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new YbcException(ExceptionCode.APPLICATION_NOT_FOUND));
    }
}
