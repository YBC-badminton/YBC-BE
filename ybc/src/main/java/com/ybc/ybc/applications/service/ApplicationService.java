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
import com.ybc.ybc.members.service.ClubMemberSyncService;
import com.ybc.ybc.recruitments.repository.RecruitmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {
    private static final Sort APPLICANT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private final ApplicationRepository applicationRepository;
    private final ClubMemberSyncService clubMemberSyncService;
    private final ApplicationSubmissionMailService applicationSubmissionMailService;
    private final RecruitmentRepository recruitmentRepository;
    private final Clock clock;

    // 지원서 저장
    @Transactional
    public void createApplication(ApplicationRequest request){
        Application application = request.toApplication(getCurrentRecruitmentTerm());
        applicationRepository.save(application);
        applicationSubmissionMailService.sendApplicationSubmittedMail(application);
    }

    public ApplicantListResponse getApplicants(String term) {
        List<Application> applications = getApplications(term);
        Set<Long> reapplicantIds = getReapplicantIds(applications);

        List<ApplicantSummaryResponse> applicants = applications.stream()
                .map(application -> ApplicantSummaryResponse.from(
                        application,
                        reapplicantIds.contains(application.getId())
                ))
                .toList();
        return new ApplicantListResponse(applicants, applicants.size());
    }

    public ApplicantDetailResponse getApplicant(Long applicationId) {
        Application application = getApplication(applicationId);
        return ApplicantDetailResponse.from(application, isReapplicant(application));
    }

    // 지원서 상태 변경(1차합격, 최종합격, 불합격, 보류)
    @Transactional
    public ApplicantDetailResponse updateApplicantStatus(Long applicationId, String statusValue) {
        Application application = getApplication(applicationId);
        Status previousStatus = application.getStatusOrDefault();
        Status status = Status.from(statusValue);
        application.updateStatus(status);
        clubMemberSyncService.syncByApplicationStatusChange(application, previousStatus, status);
        return ApplicantDetailResponse.from(application, isReapplicant(application));
    }

    private Application getApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new YbcException(ExceptionCode.APPLICATION_NOT_FOUND));
    }

    private List<Application> getApplications(String term) {
        if (StringUtils.hasText(term)) {
            return applicationRepository.findAllByTerm(term.trim(), APPLICANT_SORT);
        }
        return applicationRepository.findAll(APPLICANT_SORT);
    }

    private Set<Long> getReapplicantIds(List<Application> applications) {
        if (applications.isEmpty()) {
            return Set.of();
        }

        List<Long> applicationIds = applications.stream()
                .map(Application::getId)
                .toList();
        return new HashSet<>(applicationRepository.findReapplicantIdsByApplicationIds(applicationIds));
    }

    private boolean isReapplicant(Application application) {
        return applicationRepository.existsReapplicantHistory(
                application.getName(),
                application.getPhone(),
                application.getId(),
                normalizeTerm(application.getTerm())
        );
    }

    private String getCurrentRecruitmentTerm() {
        LocalDateTime now = LocalDateTime.now(clock);
        return recruitmentRepository.findFirstByStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(now, now)
                .map(recruitment -> recruitment.getTerm())
                .orElseThrow(() -> new YbcException(ExceptionCode.RECRUITMENT_NOT_FOUND));
    }

    private String normalizeTerm(String term) {
        return term == null ? "" : term.trim();
    }
}
