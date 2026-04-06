package com.ybc.ybc.applications.service;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import com.ybc.ybc.applications.dto.request.ApplicationRequest;
import com.ybc.ybc.applications.dto.response.ApplicantDetailResponse;
import com.ybc.ybc.applications.dto.response.ApplicantListResponse;
import com.ybc.ybc.global.exception.YbcException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.ybc.ybc.applications.domain.DiscoverySource.INSTAGRAM;
import static com.ybc.ybc.applications.domain.Gender.MALE;
import static com.ybc.ybc.applications.domain.InterviewTime.FIRST_SESSION;
import static com.ybc.ybc.applications.domain.InterviewTime.SECOND_SESSION;
import static com.ybc.ybc.applications.domain.Status.FIRST_PASS;
import static com.ybc.ybc.applications.domain.Status.NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {
    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private ApplicationService applicationService;

    @Test
    @DisplayName("지원서 요청을 엔티티로 변환해 저장소에 저장한다")
    // 지원서 작성 요청이 들어오면 DTO를 엔티티로 변환해 저장소에 저장해야 한다.
    void createApplicationSavesConvertedApplication() {
        ApplicationRequest request = validRequest();

        applicationService.createApplication(request);

        ArgumentCaptor<Application> applicationCaptor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(applicationCaptor.capture());
        verifyNoMoreInteractions(applicationRepository);

        Application savedApplication = applicationCaptor.getValue();
        assertThat(savedApplication.getName()).isEqualTo("홍길동");
        assertThat(savedApplication.getAddress()).isEqualTo("서울특별시 서대문구 이화여대길 52");
        assertThat(savedApplication.getGender()).isEqualTo(MALE);
        assertThat(savedApplication.getAge()).isEqualTo(23);
        assertThat(savedApplication.getPhone()).isEqualTo("010-1234-5678");
        assertThat(savedApplication.getUniversity()).isEqualTo("이화여자대학교");
        assertThat(savedApplication.getMajor()).isEqualTo("체육과학부");
        assertThat(savedApplication.getIntroduction()).isEqualTo("배드민턴을 꾸준히 쳐 왔고 동아리 활동에도 적극적으로 참여하고 싶습니다.");
        assertThat(savedApplication.getMotivation()).isEqualTo("함께 배드민턴을 즐기며 좋은 사람들과 교류하고 싶어서 지원했습니다.");
        assertThat(savedApplication.getEquipment()).isEqualTo("배드민턴 라켓 2자루와 실내 운동화");
        assertThat(savedApplication.getInterviewTimes()).containsExactly(FIRST_SESSION, SECOND_SESSION);
        assertThat(savedApplication.getDiscoverySource()).isEqualTo(INSTAGRAM);
        assertThat(savedApplication.getDiscoveryEtc()).isEqualTo("인스타그램 모집 게시글");
        assertThat(savedApplication.isWantsStaff()).isTrue();
        assertThat(savedApplication.getFinalComment()).isEqualTo("열심히 참여하겠습니다.");
        assertThat(savedApplication.getStatus()).isEqualTo(NONE);
        assertThat(savedApplication.getEmail()).isEqualTo("hong@example.com");
    }

    @Test
    @DisplayName("면접 가능 시간 목록을 누락 없이 엔티티에 반영한다")
    // 면접 가능 시간 목록도 누락 없이 엔티티에 그대로 담아 저장해야 한다.
    void createApplicationKeepsInterviewTimes() {
        ApplicationRequest request = validRequest();

        applicationService.createApplication(request);

        ArgumentCaptor<Application> applicationCaptor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(applicationCaptor.capture());

        assertThat(applicationCaptor.getValue().getInterviewTimes())
                .containsExactly(FIRST_SESSION, SECOND_SESSION);
    }

    @Test
    @DisplayName("지원자 목록을 응답 DTO로 변환해 순서대로 반환한다")
    void getApplicantsReturnsMappedSummaries() {
        Application firstApplication = savedApplication(1L, "김철수", NONE, LocalDateTime.of(2026, 3, 24, 11, 0));
        Application secondApplication = savedApplication(2L, "이영희", FIRST_PASS, LocalDateTime.of(2026, 3, 25, 14, 30));
        when(applicationRepository.findAll(any(Sort.class))).thenReturn(List.of(secondApplication, firstApplication));

        ApplicantListResponse response = applicationService.getApplicants();

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.applicants()).hasSize(2);
        assertThat(response.applicants().get(0).applicationId()).isEqualTo(2L);
        assertThat(response.applicants().get(0).name()).isEqualTo("이영희");
        assertThat(response.applicants().get(0).gender()).isEqualTo("남성");
        assertThat(response.applicants().get(0).status()).isEqualTo("FIRST_PASS");
        assertThat(response.applicants().get(1).applicationId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("지원자 상세 조회 시 상세 응답 DTO로 반환한다")
    void getApplicantReturnsMappedDetail() {
        Application application = savedApplication(1L, "김철수", NONE, LocalDateTime.of(2026, 3, 25, 14, 30));
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        ApplicantDetailResponse response = applicationService.getApplicant(1L);

        assertThat(response.applicationId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("김철수");
        assertThat(response.gender()).isEqualTo("남성");
        assertThat(response.discoverySource()).isEqualTo("인스타그램");
        assertThat(response.interviewAvailableTimes()).containsExactly("앞타임", "뒷타임");
        assertThat(response.status()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("지원자가 없으면 상세 조회 시 예외가 발생한다")
    void getApplicantThrowsWhenApplicationIsMissing() {
        when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getApplicant(999L))
                .isInstanceOf(YbcException.class)
                .hasMessage("존재하지 않는 지원서입니다.");
    }

    @Test
    @DisplayName("지원자 상태를 수정하면 엔티티와 응답 모두 변경된다")
    void updateApplicantStatusUpdatesApplication() {
        Application application = savedApplication(1L, "김철수", NONE, LocalDateTime.of(2026, 3, 25, 14, 30));
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        ApplicantDetailResponse response = applicationService.updateApplicantStatus(1L, "FIRST_PASS");

        assertThat(application.getStatus()).isEqualTo(FIRST_PASS);
        assertThat(response.status()).isEqualTo("FIRST_PASS");
    }

    @Test
    @DisplayName("유효하지 않은 지원자 상태값이면 수정 시 예외가 발생한다")
    void updateApplicantStatusThrowsWhenStatusIsInvalid() {
        Application application = savedApplication(1L, "김철수", NONE, LocalDateTime.of(2026, 3, 25, 14, 30));
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.updateApplicantStatus(1L, "WRONG_STATUS"))
                .isInstanceOf(YbcException.class)
                .hasMessage("지원자 상태값이 올바르지 않습니다.");

        assertThat(application.getStatus()).isEqualTo(NONE);
    }

    private ApplicationRequest validRequest() {
        return new ApplicationRequest(
                "홍길동",
                "서울특별시 서대문구 이화여대길 52",
                MALE,
                23,
                "010-1234-5678",
                "이화여자대학교",
                "체육과학부",
                "배드민턴을 꾸준히 쳐 왔고 동아리 활동에도 적극적으로 참여하고 싶습니다.",
                "함께 배드민턴을 즐기며 좋은 사람들과 교류하고 싶어서 지원했습니다.",
                "배드민턴 라켓 2자루와 실내 운동화",
                List.of(FIRST_SESSION, SECOND_SESSION),
                INSTAGRAM,
                "인스타그램 모집 게시글",
                true,
                "열심히 참여하겠습니다.",
                NONE,
                "hong@example.com"
        );
    }

    private Application savedApplication(Long id, String name, com.ybc.ybc.applications.domain.Status status, LocalDateTime createdAt) {
        Application application = Application.builder()
                .name(name)
                .address("서울 강서구")
                .gender(MALE)
                .age(24)
                .phone("010-1234-5678")
                .university("한국대학교")
                .major("컴퓨터공학과")
                .introduction("성실하게 활동할 자신 있습니다.")
                .motivation("운동을 좋아합니다.")
                .equipment("개인 라켓 보유")
                .discoverySource(INSTAGRAM)
                .discoveryEtc(null)
                .wantsStaff(false)
                .finalComment("열심히 하겠습니다!")
                .status(status)
                .email("email@example.com")
                .build();
        application.addInterviewTime(FIRST_SESSION);
        application.addInterviewTime(SECOND_SESSION);
        ReflectionTestUtils.setField(application, "id", id);
        ReflectionTestUtils.setField(application, "createdAt", createdAt);
        return application;
    }
}
