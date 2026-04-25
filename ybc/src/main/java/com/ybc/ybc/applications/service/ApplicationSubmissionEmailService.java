package com.ybc.ybc.applications.service;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.InterviewTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationSubmissionEmailService {
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:${spring.mail.username:no-reply@ybc.local}}")
    private String fromAddress;

    public void sendApplicationSubmittedMail(Application application) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(application.getEmail());
        mailMessage.setFrom(fromAddress);
        mailMessage.setSubject("[YBC] 지원서 작성 내용 안내");
        mailMessage.setText(buildMailBody(application));
        mailSender.send(mailMessage);
    }

    private String buildMailBody(Application application) {
        String interviewTimes = application.getInterviewTimes().stream()
                .map(InterviewTime::getDisplayName)
                .collect(Collectors.joining(", "));

        String discoveryDetail = application.getDiscoveryEtc();
        if (discoveryDetail == null || discoveryDetail.isBlank()) {
            discoveryDetail = "-";
        }

        String equipment = application.getEquipment();
        if (equipment == null || equipment.isBlank()) {
            equipment = "-";
        }

        String finalComment = application.getFinalComment();
        if (finalComment == null || finalComment.isBlank()) {
            finalComment = "-";
        }

        return String.join(System.lineSeparator(),
                "YBC 지원서 작성이 완료되었습니다.",
                "",
                "아래는 제출하신 지원서 내용입니다.",
                "",
                "이름: " + application.getName(),
                "이메일: " + application.getEmail(),
                "주소: " + application.getAddress(),
                "성별: " + application.getGender().getDisplayName(),
                "나이: " + application.getAge(),
                "전화번호: " + application.getPhone(),
                "학교: " + application.getUniversity(),
                "학과: " + application.getMajor(),
                "면접 가능 시간: " + interviewTimes,
                "유입 경로: " + application.getDiscoverySource().getDisplayName(),
                "유입 경로 기타: " + discoveryDetail,
                "운영진 지원 여부: " + (application.isWantsStaff() ? "예" : "아니오"),
                "장비 보유 여부: " + equipment,
                "",
                "[자기소개]",
                application.getIntroduction(),
                "",
                "[지원동기]",
                application.getMotivation(),
                "",
                "[마지막 한마디]",
                finalComment
        );
    }
}
