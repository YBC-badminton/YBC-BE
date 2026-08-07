package com.ybc.ybc.members.service;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.domain.ClubMemberCourt;
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

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private ClubMemberService clubMemberService;

    @Test
    @DisplayName("부원 명단 조회 시 가나다순 정렬로 요약과 목록을 반환한다")
    void getMembersReturnsSummaryAndMembers() {
        ClubMember firstMember = savedMember(1L, "홍길동", "한국대학교", "010-1234-5678", "03", "2기", true, "hong@example.com", ClubMemberCourt.S);
        ClubMember secondMember = savedMember(2L, "강해린", "미래대학교", "010-5555-6666", "05", "7.5기", false, null, ClubMemberCourt.A);
        ClubMember thirdMember = savedMember(15L, "이마크", "글로벌대학교", "010-7777-8888", "01", "8기", false, null, ClubMemberCourt.B);
        when(memberRepository.findAllByNormalizedEmailInOrderByIdAsc(any())).thenReturn(List.of(authMember(101L, "hong@example.com", true)));
        when(clubMemberRepository.findAll(any(Sort.class))).thenReturn(List.of(secondMember, thirdMember, firstMember));

        ClubMemberListResponse response = clubMemberService.getMembers();

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(clubMemberSyncService).reconcileFinalPassMembersSafely();
        verify(clubMemberRepository).findAll(sortCaptor.capture());
        assertThat(response.summary().totalMembers()).isEqualTo(3);
        assertThat(response.summary().mapoResidentCount()).isEqualTo(1);
        assertThat(response.members()).hasSize(3);
        assertThat(sortCaptor.getValue()).isEqualTo(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        assertThat(response.members().get(0).memberId()).isEqualTo(2L);
        assertThat(response.members().get(0).name()).isEqualTo("강해린");
        assertThat(response.members().get(1).name()).isEqualTo("이마크");
        assertThat(response.members().get(2).term()).isEqualTo("2기");
        assertThat(response.members().get(0).court()).isEqualTo(ClubMemberCourt.A);
        assertThat(response.members().get(1).court()).isEqualTo(ClubMemberCourt.B);
        assertThat(response.members().get(2).court()).isEqualTo(ClubMemberCourt.S);
        assertThat(response.members().get(0).isMapoResident()).isFalse();
        assertThat(response.members().get(2).isMapoResident()).isTrue();
        assertThat(response.members().get(2).isAdmin()).isTrue();
        assertThat(response.members().get(0).isAdmin()).isFalse();
    }

    @Test
    @DisplayName("부원 추가 시 저장된 부원 정보를 반환한다")
    void createMemberSavesAndReturnsMember() {
        ClubMemberCreateRequest request = new ClubMemberCreateRequest(
                "홍길동", "한국대학교", "010-1234-5678", "03", "2기", true, "hong@example.com", Gender.MALE, ClubMemberCourt.S
        );
        Member authMember = authMember(7L, "hong@example.com", false);
        when(memberRepository.findAllByNormalizedEmailInOrderByIdAsc(any())).thenReturn(List.of(authMember));
        when(clubMemberRepository.save(any(ClubMember.class))).thenAnswer(invocation -> {
            ClubMember clubMember = invocation.getArgument(0);
            ReflectionTestUtils.setField(clubMember, "id", 50L);
            return clubMember;
        });

        ClubMemberResponse response = clubMemberService.createMember(request);

        ArgumentCaptor<ClubMember> memberCaptor = ArgumentCaptor.forClass(ClubMember.class);
        verify(clubMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getName()).isEqualTo("홍길동");
        assertThat(memberCaptor.getValue().getEmail()).isEqualTo("hong@example.com");
        assertThat(memberCaptor.getValue().getGender()).isEqualTo(Gender.MALE);
        assertThat(memberCaptor.getValue().getCourt()).isEqualTo(ClubMemberCourt.S);
        assertThat(memberCaptor.getValue().isMapoResident()).isTrue();
        assertThat(memberCaptor.getValue().getMemberId()).isEqualTo(7L);
        assertThat(authMember.isAdmin()).isFalse();
        assertThat(response.memberId()).isEqualTo(50L);
        assertThat(response.phone()).isEqualTo("010-1234-5678");
        assertThat(response.court()).isEqualTo(ClubMemberCourt.S);
        assertThat(response.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("부원 수정 시 전화번호를 포함한 정보를 변경한다")
    void updateMemberUpdatesProfile() {
        ClubMember member = savedMember(1L, "홍길동", "한국대학교", "010-0000-0000", "03", "2기", false, "hong@example.com", ClubMemberCourt.C);
        Member authMember = authMember(7L, "hong@example.com", true);
        when(clubMemberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberRepository.findAllByNormalizedEmailInOrderByIdAsc(any())).thenReturn(List.of(authMember));

        ClubMemberResponse response = clubMemberService.updateMember(
                1L,
                new ClubMemberUpdateRequest("홍길동", "02", "한국대학교", "010-1234-5678", "10기", true, "hong@example.com", Gender.MALE, ClubMemberCourt.A)
        );

        assertThat(member.getPhone()).isEqualTo("010-1234-5678");
        assertThat(member.getAge()).isEqualTo("02");
        assertThat(member.getTerm()).isEqualTo("10기");
        assertThat(member.getEmail()).isEqualTo("hong@example.com");
        assertThat(member.getGender()).isEqualTo(Gender.MALE);
        assertThat(member.getCourt()).isEqualTo(ClubMemberCourt.A);
        assertThat(member.isMapoResident()).isTrue();
        assertThat(member.getMemberId()).isEqualTo(7L);
        assertThat(authMember.isAdmin()).isTrue();
        assertThat(response.phone()).isEqualTo("010-1234-5678");
        assertThat(response.email()).isEqualTo("hong@example.com");
        assertThat(response.court()).isEqualTo(ClubMemberCourt.A);
        assertThat(response.isMapoResident()).isTrue();
        assertThat(response.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("부원 수정 요청에 마포구민 여부가 없으면 기존 값을 유지한다")
    void updateMemberKeepsMapoResidentWhenRequestOmitsIt() {
        ClubMember member = savedMember(1L, "홍길동", "한국대학교", "010-0000-0000", "03", "2기", true, "hong@example.com", ClubMemberCourt.B);
        when(memberRepository.findAllByNormalizedEmailInOrderByIdAsc(any())).thenReturn(List.of(authMember(7L, "hong@example.com", false)));
        when(clubMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        clubMemberService.updateMember(
                1L,
                new ClubMemberUpdateRequest("홍길동", "02", "한국대학교", "010-1234-5678", "10기", null, "hong@example.com", Gender.MALE, null)
        );

        assertThat(member.isMapoResident()).isTrue();
        assertThat(member.getCourt()).isEqualTo(ClubMemberCourt.B);
    }

    @Test
    @DisplayName("회원 계정이 없어도 부원은 생성할 수 있다")
    void createMemberCreatesMemberWithoutLinkedAccount() {
        ClubMemberCreateRequest request = new ClubMemberCreateRequest(
                "홍길동", "한국대학교", "010-1234-5678", "03", "2기", true, "hong@example.com", Gender.MALE, ClubMemberCourt.B
        );
        when(memberRepository.findAllByNormalizedEmailInOrderByIdAsc(any())).thenReturn(List.of());
        when(clubMemberRepository.save(any(ClubMember.class))).thenAnswer(invocation -> {
            ClubMember clubMember = invocation.getArgument(0);
            ReflectionTestUtils.setField(clubMember, "id", 50L);
            return clubMember;
        });

        ClubMemberResponse response = clubMemberService.createMember(request);

        assertThat(response.memberId()).isEqualTo(50L);
        assertThat(response.court()).isEqualTo(ClubMemberCourt.B);
        assertThat(response.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("부원 검색 시 이름으로 첫 번째 부원을 반환한다")
    void searchMemberReturnsMemberByName() {
        ClubMember member = savedMember(1L, "홍길동", "한국대학교", "010-1234-5678", "03", "2기");
        when(clubMemberRepository.findFirstByNameOrderByIdAsc("홍길동")).thenReturn(Optional.of(member));

        ClubMemberResponse response = clubMemberService.searchMember("홍길동");

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("부원 삭제 시 저장소에서 삭제한다")
    void deleteMemberDeletesExistingMember() {
        ClubMember member = savedMember(1L, "홍길동", "한국대학교", "010-1234-5678", "03", "2기");
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
                new ClubMemberUpdateRequest("홍길동", "02", "한국대학교", "010-1234-5678", "10기", null, null, null, null)
        ))
                .isInstanceOf(YbcException.class)
                .hasMessage("존재하지 않는 부원입니다.");
    }

    private ClubMember savedMember(Long id,
                                   String name,
                                   String university,
                                   String phone,
                                   String age,
                                   String term) {
        return savedMember(id, name, university, phone, age, term, false);
    }

    private ClubMember savedMember(Long id,
                                   String name,
                                   String university,
                                   String phone,
                                   String age,
                                   String term,
                                   boolean isMapoResident) {
        return savedMember(id, name, university, phone, age, term, isMapoResident, null);
    }

    private ClubMember savedMember(Long id,
                                   String name,
                                   String university,
                                   String phone,
                                   String age,
                                   String term,
                                   boolean isMapoResident,
                                   String email) {
        return savedMember(id, name, university, phone, age, term, isMapoResident, email, null);
    }

    private ClubMember savedMember(Long id,
                                   String name,
                                   String university,
                                   String phone,
                                   String age,
                                   String term,
                                   boolean isMapoResident,
                                   String email,
                                   ClubMemberCourt court) {
        ClubMember clubMember = ClubMember.create(name, university, phone, age, term, isMapoResident, email, null, court);
        ReflectionTestUtils.setField(clubMember, "id", id);
        return clubMember;
    }

    private Member authMember(Long id, String email, boolean isAdmin) {
        Member member = Member.createSocialMember(SocialProvider.KAKAO, String.valueOf(id), "운영진", email, isAdmin);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
