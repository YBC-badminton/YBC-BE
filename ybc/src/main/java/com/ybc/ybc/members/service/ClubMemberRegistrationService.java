package com.ybc.ybc.members.service;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.Status;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Year;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubMemberRegistrationService {
    private final ClubMemberRepository clubMemberRepository;

    @Transactional
    public void registerOnFinalPass(Application application, Status previousStatus, Status updatedStatus) {
        if (previousStatus == Status.FINAL_PASS || updatedStatus != Status.FINAL_PASS) {
            return;
        }
        if (clubMemberRepository.existsByApplicationId(application.getId())) {
            return;
        }

        clubMemberRepository.save(ClubMember.createFromApplication(
                application.getId(),
                application.getName(),
                application.getUniversity(),
                application.getPhone(),
                toBirthYear(application.getAge()),
                isMapoResident(application.getAddress()),
                application.getEmail(),
                application.getGender()
        ));
    }

    private String toBirthYear(int age) {
        int birthYear = Year.now().getValue() - age + 1;
        return String.format("%02d", Math.floorMod(birthYear, 100));
    }

    private boolean isMapoResident(String address) {
        return StringUtils.hasText(address) && address.contains("마포");
    }
}
