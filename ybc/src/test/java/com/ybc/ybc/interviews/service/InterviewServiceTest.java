package com.ybc.ybc.interviews.service;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.DiscoverySource;
import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.applications.domain.InterviewTime;
import com.ybc.ybc.applications.domain.Status;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.interviews.domain.Interview;
import com.ybc.ybc.interviews.dto.request.InterviewUpdateRequest;
import com.ybc.ybc.interviews.dto.response.InterviewManagementResponse;
import com.ybc.ybc.interviews.repository.InterviewRepository;
import com.ybc.ybc.members.service.ClubMemberSyncService;
import com.ybc.ybc.recruitments.domain.Recruitment;
import com.ybc.ybc.recruitments.repository.RecruitmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-20T03:00:00Z"), KOREA);

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private InterviewRepository interviewRepository;

    @Mock
    private ClubMemberSyncService clubMemberSyncService;

    @Mock
    private RecruitmentRepository recruitmentRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private InterviewService interviewService;

    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(FIXED_CLOCK.instant());
        lenient().when(clock.getZone()).thenReturn(FIXED_CLOCK.getZone());
    }

    @Test
    @DisplayName("면접 관리 리스트는 현재 모집 기수의 면접 대상자만 요약과 함께 반환한다")
    void getInterviewManagementReturnsSummaryAndInterviewList() {
        Application firstPassApplication = savedApplication(1L, "김철수", Status.FIRST_PASS, InterviewTime.FIRST_SESSION, InterviewTime.SECOND_SESSION);
        Application finalPassApplication = savedApplication(2L, "이영희", Status.FINAL_PASS, InterviewTime.SECOND_SESSION);
        Application holdApplication = savedApplication(4L, "최수진", Status.HOLD, InterviewTime.SECOND_SESSION);
        Interview scheduledInterview = savedInterview(11L, finalPassApplication, InterviewTime.SECOND_SESSION, "합격 유력");

        mockCurrentRecruitment("10기");
        when(applicationRepository.findAllByTermAndStatusIn(any(), any(), any(Sort.class)))
                .thenReturn(List.of(firstPassApplication, finalPassApplication, holdApplication));
        when(interviewRepository.findAllByApplication_IdIn(List.of(1L, 2L, 4L)))
                .thenReturn(List.of(scheduledInterview));

        InterviewManagementResponse response = interviewService.getInterviewManagement();

        assertThat(response.summary().totalInterviewees()).isEqualTo(3);
        assertThat(response.summary().passedCount()).isEqualTo(1);
        assertThat(response.summary().failedCount()).isZero();
        assertThat(response.summary().holdCount()).isEqualTo(1);
        assertThat(response.interviewList()).hasSize(3);
        assertThat(response.interviewList().get(0).applicationId()).isEqualTo(1L);
        assertThat(response.interviewList().get(0).availableSessions()).containsExactly("FIRST_SESSION", "SECOND_SESSION");
        assertThat(response.interviewList().get(0).scheduledAt()).isNull();
        assertThat(response.interviewList().get(1).scheduledAt()).isEqualTo("SECOND_SESSION");
        assertThat(response.interviewList().get(1).memo()).isEqualTo("합격 유력");
        assertThat(response.interviewList().get(2).applicationId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("면접 상세 정보 조회가 실패해도 면접 관리 리스트는 기본 정보로 반환한다")
    void getInterviewManagementReturnsDashboardWhenInterviewLookupFails() {
        Application firstPassApplication = savedApplication(1L, "김철수", Status.FIRST_PASS, InterviewTime.FIRST_SESSION, null);

        mockCurrentRecruitment("10기");
        when(applicationRepository.findAllByTermAndStatusIn(any(), any(), any(Sort.class)))
                .thenReturn(List.of(firstPassApplication));
        when(interviewRepository.findAllByApplication_IdIn(List.of(1L)))
                .thenThrow(new RuntimeException("broken interview rows"));

        InterviewManagementResponse response = interviewService.getInterviewManagement();

        assertThat(response.summary().totalInterviewees()).isEqualTo(1);
        assertThat(response.interviewList()).hasSize(1);
        assertThat(response.interviewList().get(0).availableSessions()).containsExactly("FIRST_SESSION");
        assertThat(response.interviewList().get(0).scheduledAt()).isNull();
        assertThat(response.interviewList().get(0).memo()).isNull();
    }

    @Test
    @DisplayName("현재 모집이 없으면 가장 최근 모집 기수의 면접 대상자만 조회한다")
    void getInterviewManagementReturnsMostRecentRecruitmentWhenCurrentRecruitmentMissing() {
        Application recentApplication = savedApplication(9L, "최근지원", Status.FIRST_PASS, InterviewTime.FIRST_SESSION);

        when(recruitmentRepository.findFirstByStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(
                any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());
        when(recruitmentRepository.findFirstByEndAtLessThanOrderByEndAtDescIdDesc(any(LocalDateTime.class)))
                .thenReturn(Optional.of(pastRecruitment("11기")));
        when(applicationRepository.findAllByTermAndStatusIn(any(), any(), any(Sort.class)))
                .thenReturn(List.of(recentApplication));
        when(interviewRepository.findAllByApplication_IdIn(List.of(9L))).thenReturn(List.of());

        InterviewManagementResponse response = interviewService.getInterviewManagement();

        assertThat(response.summary().totalInterviewees()).isEqualTo(1);
        assertThat(response.interviewList()).hasSize(1);
        assertThat(response.interviewList().get(0).applicationId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("면접 관리 수정 시 면접 정보와 상태를 갱신하고 대시보드를 반환한다")
    void updateInterviewUpdatesExistingInterviewAndReturnsDashboard() {
        Application firstPassApplication = savedApplication(1L, "김철수", Status.FIRST_PASS, InterviewTime.FIRST_SESSION, InterviewTime.SECOND_SESSION);
        Application holdApplication = savedApplication(2L, "이영희", Status.HOLD, InterviewTime.SECOND_SESSION);
        Interview interview = savedInterview(11L, firstPassApplication, InterviewTime.FIRST_SESSION, "기존 메모");

        mockCurrentRecruitment("10기");
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(firstPassApplication));
        when(interviewRepository.findByApplication_Id(1L)).thenReturn(Optional.of(interview));
        when(applicationRepository.findAllByTermAndStatusIn(any(), any(), any(Sort.class)))
                .thenReturn(List.of(firstPassApplication, holdApplication));
        when(interviewRepository.findAllByApplication_IdIn(List.of(1L, 2L)))
                .thenReturn(List.of(interview));

        InterviewManagementResponse response = interviewService.updateInterview(
                1L,
                new InterviewUpdateRequest(InterviewTime.SECOND_SESSION, "FINAL_PASS", "양배추 지인")
        );

        assertThat(firstPassApplication.getStatus()).isEqualTo(Status.FINAL_PASS);
        assertThat(interview.getScheduledAt()).isEqualTo(InterviewTime.SECOND_SESSION);
        assertThat(interview.getMemo()).isEqualTo("양배추 지인");
        assertThat(response.summary().totalInterviewees()).isEqualTo(2);
        assertThat(response.summary().passedCount()).isEqualTo(1);
        assertThat(response.interviewList().get(0).status()).isEqualTo("FINAL_PASS");
        verify(interviewRepository, never()).save(any());
        verify(clubMemberSyncService).syncByApplicationStatusChange(firstPassApplication, Status.FIRST_PASS, Status.FINAL_PASS);
    }

    @Test
    @DisplayName("면접 정보가 없던 지원자도 수정 요청으로 면접 정보가 생성된다")
    void updateInterviewCreatesInterviewWhenMissing() {
        Application application = savedApplication(1L, "김철수", Status.FIRST_PASS, InterviewTime.FIRST_SESSION, InterviewTime.SECOND_SESSION);
        Interview createdInterview = savedInterview(15L, application, InterviewTime.FIRST_SESSION, "신규 메모");

        mockCurrentRecruitment("10기");
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(interviewRepository.findByApplication_Id(1L)).thenReturn(Optional.empty());
        when(applicationRepository.findAllByTermAndStatusIn(any(), any(), any(Sort.class))).thenReturn(List.of(application));
        when(interviewRepository.findAllByApplication_IdIn(List.of(1L))).thenReturn(List.of(createdInterview));

        interviewService.updateInterview(
                1L,
                new InterviewUpdateRequest(InterviewTime.FIRST_SESSION, "FIRST_PASS", "신규 메모")
        );

        ArgumentCaptor<Interview> interviewCaptor = ArgumentCaptor.forClass(Interview.class);
        verify(interviewRepository).save(interviewCaptor.capture());
        assertThat(interviewCaptor.getValue().getApplicationId()).isEqualTo(1L);
        assertThat(interviewCaptor.getValue().getScheduledAt()).isEqualTo(InterviewTime.FIRST_SESSION);
        assertThat(interviewCaptor.getValue().getMemo()).isEqualTo("신규 메모");
    }

    @Test
    @DisplayName("면접 대상이 아닌 지원자는 면접 관리 수정 시 예외가 발생한다")
    void updateInterviewThrowsWhenApplicationIsNotInterviewTarget() {
        Application application = savedApplication(1L, "김철수", Status.NONE, InterviewTime.FIRST_SESSION);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> interviewService.updateInterview(
                1L,
                new InterviewUpdateRequest(InterviewTime.FIRST_SESSION, "FINAL_PASS", "메모")
        ))
                .isInstanceOf(YbcException.class)
                .hasMessage("면접 관리 대상 지원서가 아닙니다.");
    }

    @Test
    @DisplayName("면접 상태값이 올바르지 않으면 수정 시 예외가 발생한다")
    void updateInterviewThrowsWhenStatusIsInvalid() {
        Application application = savedApplication(1L, "김철수", Status.FIRST_PASS, InterviewTime.FIRST_SESSION);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> interviewService.updateInterview(
                1L,
                new InterviewUpdateRequest(InterviewTime.FIRST_SESSION, "NONE", "메모")
        ))
                .isInstanceOf(YbcException.class)
                .hasMessage("면접 상태값이 올바르지 않습니다.");
    }

    private Application savedApplication(Long id, String name, Status status, InterviewTime... availableTimes) {
        Application application = Application.builder()
                .name(name)
                .address("서울 강서구")
                .gender(Gender.MALE)
                .age(24)
                .phone("010-1234-5678")
                .term("10기")
                .university("한국대학교")
                .major("컴퓨터공학과")
                .introduction("성실하게 활동할 자신 있습니다.")
                .motivation("운동을 좋아합니다.")
                .equipment("개인 라켓 보유")
                .discoverySource(DiscoverySource.INSTAGRAM)
                .discoveryEtc(null)
                .wantsStaff(false)
                .finalComment("열심히 하겠습니다!")
                .status(status)
                .email("email@example.com")
                .build();
        for (InterviewTime availableTime : availableTimes) {
            application.addInterviewTime(availableTime);
        }
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    private Interview savedInterview(Long id, Application application, InterviewTime scheduledAt, String memo) {
        Interview interview = Interview.create(application, scheduledAt, memo);
        ReflectionTestUtils.setField(interview, "id", id);
        return interview;
    }

    private void mockCurrentRecruitment(String term) {
        when(recruitmentRepository.findFirstByStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(
                any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(Optional.of(currentRecruitment(term)));
    }

    private Recruitment currentRecruitment(String term) {
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);
        return Recruitment.builder()
                .term(term)
                .startAt(now.minusDays(1))
                .endAt(now.plusDays(1))
                .firstResultDate(now.toLocalDate())
                .finalResultDate(now.plusDays(1).toLocalDate())
                .interviewDate(now.plusDays(2).toLocalDate())
                .interviewLocation("학생회관")
                .interviewFirstTime("10:00")
                .interviewSecondTime("14:00")
                .otDate(now.plusDays(7).toLocalDate())
                .recruitmentMessage("현재 모집")
                .membershipFee(30000)
                .activityPeriod(6)
                .build();
    }

    private Recruitment pastRecruitment(String term) {
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);
        return Recruitment.builder()
                .term(term)
                .startAt(now.minusDays(10))
                .endAt(now.minusDays(5))
                .firstResultDate(now.minusDays(4).toLocalDate())
                .finalResultDate(now.minusDays(3).toLocalDate())
                .interviewDate(now.minusDays(2).toLocalDate())
                .interviewLocation("학생회관")
                .interviewFirstTime("10:00")
                .interviewSecondTime("14:00")
                .otDate(now.plusDays(7).toLocalDate())
                .recruitmentMessage("지난 모집")
                .membershipFee(30000)
                .activityPeriod(6)
                .build();
    }
}
