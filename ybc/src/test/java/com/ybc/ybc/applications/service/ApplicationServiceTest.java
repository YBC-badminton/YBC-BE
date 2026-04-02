package com.ybc.ybc.applications.service;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import com.ybc.ybc.applications.dto.request.ApplicationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.ybc.ybc.applications.domain.DiscoverySource.INSTAGRAM;
import static com.ybc.ybc.applications.domain.Gender.MALE;
import static com.ybc.ybc.applications.domain.Status.NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
        assertThat(savedApplication.getInterviewTimes()).containsExactly("2026-04-02 14:00", "2026-04-03 16:00");
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
                .containsExactly("2026-04-02 14:00", "2026-04-03 16:00");
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
                List.of("2026-04-02 14:00", "2026-04-03 16:00"),
                INSTAGRAM,
                "인스타그램 모집 게시글",
                true,
                "열심히 참여하겠습니다.",
                NONE,
                "hong@example.com"
        );
    }
}
