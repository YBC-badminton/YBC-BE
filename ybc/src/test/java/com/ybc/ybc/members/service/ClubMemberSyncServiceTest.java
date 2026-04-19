package com.ybc.ybc.members.service;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.DiscoverySource;
import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.applications.domain.Status;
import com.ybc.ybc.applications.repository.ApplicationRepository;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubMemberSyncServiceTest {
    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ClubMemberRepository clubMemberRepository;

    @InjectMocks
    private ClubMemberSyncService clubMemberSyncService;

    @Test
    @DisplayName("지원서가 최종 합격으로 변경되면 부원을 자동 생성한다")
    void syncByApplicationStatusChangeCreatesClubMemberWhenStatusBecomesFinalPass() {
        Application application = savedApplication(1L, "홍길동", 23);
        when(clubMemberRepository.findByApplicationId(1L)).thenReturn(Optional.empty());

        clubMemberSyncService.syncByApplicationStatusChange(application, Status.FIRST_PASS, Status.FINAL_PASS);

        ArgumentCaptor<ClubMember> memberCaptor = ArgumentCaptor.forClass(ClubMember.class);
        verify(clubMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getApplicationId()).isEqualTo(1L);
        assertThat(memberCaptor.getValue().getName()).isEqualTo("홍길동");
        assertThat(memberCaptor.getValue().getAge()).isEqualTo(expectedBirthYear(23));
        assertThat(memberCaptor.getValue().getEmail()).isEqualTo("email@example.com");
        assertThat(memberCaptor.getValue().getGender()).isEqualTo(Gender.MALE);
        assertThat(memberCaptor.getValue().getTerm()).isEqualTo("미정");
        assertThat(memberCaptor.getValue().getCourt()).isNull();
    }

    @Test
    @DisplayName("기존 자동 생성 부원이 있으면 최종 합격 시 정보만 갱신한다")
    void syncByApplicationStatusChangeUpdatesExistingAutoCreatedMember() {
        Application application = savedApplication(1L, "홍길동", 24);
        ClubMember clubMember = ClubMember.createFromApplication(1L, "이전이름", "이전학교", "010-0000-0000", "99");
        when(clubMemberRepository.findByApplicationId(1L)).thenReturn(Optional.of(clubMember));

        clubMemberSyncService.syncByApplicationStatusChange(application, Status.HOLD, Status.FINAL_PASS);

        verify(clubMemberRepository, never()).save(any());
        assertThat(clubMember.getName()).isEqualTo("홍길동");
        assertThat(clubMember.getUniversity()).isEqualTo("한국대학교");
        assertThat(clubMember.getPhone()).isEqualTo("010-1234-5678");
        assertThat(clubMember.getAge()).isEqualTo(expectedBirthYear(24));
        assertThat(clubMember.getEmail()).isEqualTo("email@example.com");
        assertThat(clubMember.getGender()).isEqualTo(Gender.MALE);
    }

    @Test
    @DisplayName("이미 최종 합격 상태여도 다시 동기화하면 부원을 생성한다")
    void syncByApplicationStatusChangeCreatesClubMemberWhenFinalPassIsSavedAgain() {
        Application application = savedApplication(1L, "홍길동", 23);
        when(clubMemberRepository.findByApplicationId(1L)).thenReturn(Optional.empty());

        clubMemberSyncService.syncByApplicationStatusChange(application, Status.FINAL_PASS, Status.FINAL_PASS);

        verify(clubMemberRepository).save(any(ClubMember.class));
    }

    @Test
    @DisplayName("최종 합격에서 다른 상태로 변경되면 자동 생성된 부원을 삭제한다")
    void syncByApplicationStatusChangeDeletesClubMemberWhenStatusLeavesFinalPass() {
        Application application = savedApplication(1L, "홍길동", 23);
        ClubMember clubMember = ClubMember.createFromApplication(1L, "홍길동", "한국대학교", "010-1234-5678", "03");
        when(clubMemberRepository.findByApplicationId(1L)).thenReturn(Optional.of(clubMember));

        clubMemberSyncService.syncByApplicationStatusChange(application, Status.FINAL_PASS, Status.HOLD);

        verify(clubMemberRepository).delete(clubMember);
    }

    @Test
    @DisplayName("최종 합격자 보정 시 누락된 부원을 생성하고 stale 부원을 삭제한다")
    void reconcileFinalPassMembersSynchronizesCurrentFinalPassApplications() {
        Application finalPassApplication = savedApplication(1L, "홍길동", 23);
        ClubMember staleClubMember = ClubMember.createFromApplication(2L, "이전부원", "이전학교", "010-0000-0000", "99");

        when(applicationRepository.findAllByStatusIn(any(), any(Sort.class))).thenReturn(List.of(finalPassApplication));
        when(clubMemberRepository.findByApplicationId(1L)).thenReturn(Optional.empty());
        when(clubMemberRepository.findAllByApplicationIdIsNotNull()).thenReturn(List.of(staleClubMember));

        clubMemberSyncService.reconcileFinalPassMembers();

        verify(clubMemberRepository).save(any(ClubMember.class));
        verify(clubMemberRepository).delete(staleClubMember);
    }

    private Application savedApplication(Long id, String name, int age) {
        Application application = Application.builder()
                .name(name)
                .address("서울 강서구")
                .gender(Gender.MALE)
                .age(age)
                .phone("010-1234-5678")
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
