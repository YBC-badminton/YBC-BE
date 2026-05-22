package com.ybc.ybc.members.service;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.Status;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubMemberSyncService {
    private static final List<Status> FINAL_PASS_STATUS = List.of(Status.FINAL_PASS);

    private final ApplicationRepository applicationRepository;
    private final ClubMemberRepository clubMemberRepository;

    @Transactional
    public void syncByApplicationStatusChange(Application application,
                                              Status previousStatus,
                                              Status updatedStatus) {
        if (updatedStatus == Status.FINAL_PASS) {
            upsertClubMember(application);
            return;
        }

        if (previousStatus == Status.FINAL_PASS) {
            clubMemberRepository.findByApplicationId(application.getId())
                    .ifPresent(clubMemberRepository::delete);
        }
    }

    @Transactional
    public void reconcileFinalPassMembers() {
        List<Application> finalPassApplications = applicationRepository.findAllByStatusIn(
                FINAL_PASS_STATUS,
                Sort.by(Sort.Order.asc("id"))
        );

        finalPassApplications.forEach(this::upsertClubMember);

        Set<Long> finalPassApplicationIds = finalPassApplications.stream()
                .map(Application::getId)
                .collect(java.util.stream.Collectors.toSet());

        clubMemberRepository.findAllByApplicationIdIsNotNull().stream()
                .filter(clubMember -> !finalPassApplicationIds.contains(clubMember.getApplicationId()))
                .forEach(clubMemberRepository::delete);
    }

    public void reconcileFinalPassMembersSafely() {
        try {
            reconcileFinalPassMembers();
        } catch (RuntimeException exception) {
            log.error("Failed to reconcile final-pass club members.", exception);
        }
    }

    private void upsertClubMember(Application application) {
        String birthYear = toBirthYear(application.getAge());
        clubMemberRepository.findByApplicationId(application.getId())
                .ifPresentOrElse(
                        clubMember -> clubMember.syncFromApplication(
                                application.getName(),
                                application.getUniversity(),
                                application.getPhone(),
                                birthYear,
                                application.getEmail(),
                                application.getGender()
                        ),
                        () -> clubMemberRepository.save(
                                ClubMember.createFromApplication(
                                        application.getId(),
                                        application.getName(),
                                        application.getUniversity(),
                                        application.getPhone(),
                                        birthYear,
                                        application.getEmail(),
                                        application.getGender()
                                )
                        )
                );
    }

    private String toBirthYear(int age) {
        int birthYear = Year.now().getValue() - age + 1;
        return String.format("%02d", Math.floorMod(birthYear, 100));
    }
}
