package com.ybc.ybc.members.service;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.dto.request.ClubMemberCreateRequest;
import com.ybc.ybc.members.dto.request.ClubMemberUpdateRequest;
import com.ybc.ybc.members.dto.response.ClubMemberListResponse;
import com.ybc.ybc.members.dto.response.ClubMemberResponse;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubMemberServiceTest {
    @Mock
    private ClubMemberRepository clubMemberRepository;

    @Mock
    private ClubMemberSyncService clubMemberSyncService;

    @InjectMocks
    private ClubMemberService clubMemberService;

    @Test
    @DisplayName("부원 명단 조회 시 코트별 요약과 목록을 반환한다")
    void getMembersReturnsSummaryAndMembers() {
        ClubMember firstMember = savedMember(1L, "홍길동", "한국대학교", "010-1234-5678", "03", "2기", 1);
        ClubMember secondMember = savedMember(2L, "강해린", "미래대학교", "010-5555-6666", "05", "7.5기", 2);
        ClubMember thirdMember = savedMember(15L, "이마크", "글로벌대학교", "010-7777-8888", "01", "8기", 4);
        when(clubMemberRepository.findAll(any(Sort.class))).thenReturn(List.of(firstMember, secondMember, thirdMember));

        ClubMemberListResponse response = clubMemberService.getMembers();

        verify(clubMemberSyncService).reconcileFinalPassMembers();
        assertThat(response.summary().totalMembers()).isEqualTo(3);
        assertThat(response.summary().court1()).isEqualTo(1);
        assertThat(response.summary().court2()).isEqualTo(1);
        assertThat(response.summary().court3()).isEqualTo(0);
        assertThat(response.summary().court4()).isEqualTo(1);
        assertThat(response.members()).hasSize(3);
        assertThat(response.members().get(0).memberId()).isEqualTo(1L);
        assertThat(response.members().get(1).name()).isEqualTo("강해린");
        assertThat(response.members().get(2).court()).isEqualTo(4);
    }

    @Test
    @DisplayName("부원 추가 시 저장된 부원 정보를 반환한다")
    void createMemberSavesAndReturnsMember() {
        ClubMemberCreateRequest request = new ClubMemberCreateRequest(
                "홍길동", "한국대학교", "010-1234-5678", "03", "2기", 1, "hong@example.com", Gender.MALE
        );
        ClubMember savedMember = savedMember(50L, "홍길동", "한국대학교", "010-1234-5678", "03", "2기", 1);
        when(clubMemberRepository.save(any(ClubMember.class))).thenReturn(savedMember);

        ClubMemberResponse response = clubMemberService.createMember(request);

        ArgumentCaptor<ClubMember> memberCaptor = ArgumentCaptor.forClass(ClubMember.class);
        verify(clubMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getName()).isEqualTo("홍길동");
        assertThat(memberCaptor.getValue().getEmail()).isEqualTo("hong@example.com");
        assertThat(memberCaptor.getValue().getGender()).isEqualTo(Gender.MALE);
        assertThat(response.memberId()).isEqualTo(50L);
        assertThat(response.phone()).isEqualTo("010-1234-5678");
    }

    @Test
    @DisplayName("부원 수정 시 전화번호를 유지한 채 정보를 변경한다")
    void updateMemberUpdatesProfile() {
        ClubMember member = savedMember(1L, "홍길동", "한국대학교", "010-0000-0000", "03", "2기", 1);
        when(clubMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        ClubMemberResponse response = clubMemberService.updateMember(
                1L,
                new ClubMemberUpdateRequest("홍길동", "02", "한국대학교", "10기", 2, "hong@example.com", Gender.MALE)
        );

        assertThat(member.getPhone()).isEqualTo("010-0000-0000");
        assertThat(member.getAge()).isEqualTo("02");
        assertThat(member.getTerm()).isEqualTo("10기");
        assertThat(member.getCourt()).isEqualTo(2);
        assertThat(member.getEmail()).isEqualTo("hong@example.com");
        assertThat(member.getGender()).isEqualTo(Gender.MALE);
        assertThat(response.court()).isEqualTo(2);
    }

    @Test
    @DisplayName("부원 검색 시 이름으로 첫 번째 부원을 반환한다")
    void searchMemberReturnsMemberByName() {
        ClubMember member = savedMember(1L, "홍길동", "한국대학교", "010-1234-5678", "03", "2기", 1);
        when(clubMemberRepository.findFirstByNameOrderByIdAsc("홍길동")).thenReturn(Optional.of(member));

        ClubMemberResponse response = clubMemberService.searchMember("홍길동");

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("부원 삭제 시 저장소에서 삭제한다")
    void deleteMemberDeletesExistingMember() {
        ClubMember member = savedMember(1L, "홍길동", "한국대학교", "010-1234-5678", "03", "2기", 1);
        when(clubMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        clubMemberService.deleteMember(1L);

        verify(clubMemberRepository).delete(member);
    }

    @Test
    @DisplayName("존재하지 않는 부원 수정 시 예외가 발생한다")
    void updateMemberThrowsWhenMemberIsMissing() {
        when(clubMemberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clubMemberService.updateMember(
                99L,
                new ClubMemberUpdateRequest("홍길동", "02", "한국대학교", "10기", 2)
        ))
                .isInstanceOf(YbcException.class)
                .hasMessage("존재하지 않는 부원입니다.");
    }

    @Test
    @DisplayName("코트 번호가 1부터 4를 벗어나면 예외가 발생한다")
    void createMemberThrowsWhenCourtIsInvalid() {
        ClubMemberCreateRequest request = new ClubMemberCreateRequest("홍길동", "한국대학교", "010-1234-5678", "03", "2기", 5);

        assertThatThrownBy(() -> clubMemberService.createMember(request))
                .isInstanceOf(YbcException.class)
                .hasMessage("코트 번호는 1부터 4까지만 가능합니다.");
    }

    private ClubMember savedMember(Long id,
                                   String name,
                                   String university,
                                   String phone,
                                   String age,
                                   String term,
                                   Integer court) {
        ClubMember clubMember = ClubMember.create(name, university, phone, age, term, court);
        ReflectionTestUtils.setField(clubMember, "id", id);
        return clubMember;
    }
}
