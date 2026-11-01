package com.ybc.ybc.members.service;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.DiscoverySource;
import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.applications.domain.Status;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubMemberRegistrationServiceTest {
    @Mock
    private ClubMemberRepository clubMemberRepository;

    @InjectMocks
    private ClubMemberRegistrationService clubMemberRegistrationService;

    @ParameterizedTest
    @EnumSource(value = Status.class, names = "FINAL_PASS", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("다른 상태에서 최종 합격으로 변경되면 지원서 정보로 부원을 등록한다")
    void registerOnFinalPassCreatesClubMember(Status previousStatus) {
        Application application = savedApplication(1L, "홍길동", 23, "서울 마포구");

        clubMemberRegistrationService.registerOnFinalPass(application, previousStatus, Status.FINAL_PASS);

        ArgumentCaptor<ClubMember> memberCaptor = ArgumentCaptor.forClass(ClubMember.class);
        verify(clubMemberRepository).save(memberCaptor.capture());
        ClubMember member = memberCaptor.getValue();
        assertThat(member.getApplicationId()).isEqualTo(1L);
        assertThat(member.getName()).isEqualTo("홍길동");
        assertThat(member.getUniversity()).isEqualTo("한국대학교");
        assertThat(member.getPhone()).isEqualTo("01012345678");
        assertThat(member.getAge()).isEqualTo(expectedBirthYear(23));
        assertThat(member.getEmail()).isEqualTo("email@example.com");
        assertThat(member.getGender()).isEqualTo(Gender.MALE);
        assertThat(member.getTerm()).isEqualTo("미정");
        assertThat(member.isMapoResident()).isTrue();
    }

    @Test
    @DisplayName("주소에 마포가 없으면 마포구민 여부를 false로 등록한다")
    void registerOnFinalPassSetsMapoResidentFalse() {
        Application application = savedApplication(1L, "홍길동", 23);

        clubMemberRegistrationService.registerOnFinalPass(application, Status.FIRST_PASS, Status.FINAL_PASS);

        ArgumentCaptor<ClubMember> memberCaptor = ArgumentCaptor.forClass(ClubMember.class);
        verify(clubMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().isMapoResident()).isFalse();
    }

    @Test
    @DisplayName("이미 등록된 지원서는 최종 합격으로 재변경해도 부원을 생성하거나 수정하지 않는다")
    void registerOnFinalPassSkipsExistingMember() {
        Application application = savedApplication(1L, "홍길동", 23);
        when(clubMemberRepository.existsByApplicationId(1L)).thenReturn(true);

        clubMemberRegistrationService.registerOnFinalPass(application, Status.HOLD, Status.FINAL_PASS);

        verify(clubMemberRepository).existsByApplicationId(1L);
        verifyNoMoreInteractions(clubMemberRepository);
    }

    @Test
    @DisplayName("최종 합격을 다시 저장해도 삭제되거나 누락된 부원을 재생성하지 않는다")
    void registerOnFinalPassSkipsUnchangedFinalPass() {
        Application application = savedApplication(1L, "홍길동", 23);

        clubMemberRegistrationService.registerOnFinalPass(application, Status.FINAL_PASS, Status.FINAL_PASS);

        verifyNoInteractions(clubMemberRepository);
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = "FINAL_PASS", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("최종 합격을 취소해도 등록된 부원을 삭제하지 않는다")
    void registerOnFinalPassKeepsMemberWhenStatusLeavesFinalPass(Status updatedStatus) {
        Application application = savedApplication(1L, "홍길동", 23);

        clubMemberRegistrationService.registerOnFinalPass(application, Status.FINAL_PASS, updatedStatus);

        verifyNoInteractions(clubMemberRepository);
    }

    @Test
    @DisplayName("최종 합격 이외의 상태 변경은 부원 명단을 변경하지 않는다")
    void registerOnFinalPassSkipsOtherStatusChanges() {
        Application application = savedApplication(1L, "홍길동", 23);

        clubMemberRegistrationService.registerOnFinalPass(application, Status.NONE, Status.FIRST_PASS);

        verifyNoInteractions(clubMemberRepository);
    }

    private Application savedApplication(Long id, String name, int age) {
        return savedApplication(id, name, age, "서울 강서구");
    }

    private Application savedApplication(Long id, String name, int age, String address) {
        Application application = Application.builder()
                .name(name)
                .address(address)
                .gender(Gender.MALE)
                .age(age)
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
                .status(Status.NONE)
                .email("email@example.com")
                .build();
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    private String expectedBirthYear(int age) {
        int birthYear = Year.now().getValue() - age + 1;
        return String.format("%02d", Math.floorMod(birthYear, 100));
    }
}
