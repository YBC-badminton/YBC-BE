package com.ybc.ybc.members.service;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubMemberLookupServiceTest {

    @Mock
    private ClubMemberRepository clubMemberRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private ClubMemberLookupService clubMemberLookupService;

    @Test
    @DisplayName("member_id로 연결된 부원이 있으면 이메일 조회 없이 바로 반환한다")
    void getClubMembersByMemberIdReturnsLinkedClubMembersFirst() {
        Member member = authMember(10L, "member@example.com", "닉네임");
        ClubMember clubMember = clubMember(100L, "실명 회원", "member@example.com");
        clubMember.linkMember(member.getId());

        when(clubMemberRepository.findAllByMemberIdInOrderByIdAsc(Set.of(10L))).thenReturn(List.of(clubMember));

        Map<Long, ClubMember> result = clubMemberLookupService.getClubMembersByMemberId(List.of(member));

        assertThat(result).containsEntry(10L, clubMember);
        verify(clubMemberRepository, never()).findAllByNormalizedEmailInOrderByIdAsc(any());
    }

    @Test
    @DisplayName("member_id 연결이 없으면 이메일이 같은 부원을 찾아 연결하고 반환한다")
    void getClubMembersByMemberIdFallsBackToEmail() {
        Member member = authMember(10L, "Member@example.com", "닉네임");
        ClubMember clubMember = clubMember(100L, "실명 회원", "member@example.com");

        when(clubMemberRepository.findAllByMemberIdInOrderByIdAsc(Set.of(10L))).thenReturn(List.of());
        when(clubMemberRepository.findAllByNormalizedEmailInOrderByIdAsc(Set.of("member@example.com")))
                .thenReturn(List.of(clubMember));

        Map<Long, ClubMember> result = clubMemberLookupService.getClubMembersByMemberId(List.of(member));

        assertThat(result).containsEntry(10L, clubMember);
        assertThat(clubMemberLookupService.resolveDisplayName(member, result)).isEqualTo("실명 회원");
    }

    @Test
    @DisplayName("이메일이 같은 부원이 이전에 삭제된 member_id를 들고 있으면 새 member_id로 다시 매칭한다")
    void getClubMembersByMemberIdIgnoresStaleLinkedMemberId() {
        Member member = authMember(10L, "member@example.com", "닉네임");
        ClubMember clubMember = clubMember(100L, "실명 회원", "member@example.com");
        clubMember.relinkMember(99L);

        when(clubMemberRepository.findAllByMemberIdInOrderByIdAsc(Set.of(10L))).thenReturn(List.of());
        when(clubMemberRepository.findAllByNormalizedEmailInOrderByIdAsc(Set.of("member@example.com")))
                .thenReturn(List.of(clubMember));
        when(memberRepository.findAllById(Set.of(99L))).thenReturn(List.of());

        Map<Long, ClubMember> result = clubMemberLookupService.getClubMembersByMemberId(List.of(member));

        assertThat(result).containsEntry(10L, clubMember);
        assertThat(clubMemberLookupService.resolveDisplayName(member, result)).isEqualTo("실명 회원");
    }

    @Test
    @DisplayName("연결된 부원이 없으면 회원 닉네임을 fallback으로 사용한다")
    void resolveDisplayNameFallsBackToMemberNickname() {
        Member member = authMember(10L, "member@example.com", "닉네임");

        String displayName = clubMemberLookupService.resolveDisplayName(member, Map.of());

        assertThat(displayName).isEqualTo("닉네임");
    }

    private Member authMember(Long id, String email, String nickname) {
        Member member = Member.createSocialMember(SocialProvider.KAKAO, String.valueOf(id), nickname, email);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private ClubMember clubMember(Long id, String name, String email) {
        ClubMember clubMember = ClubMember.create(
                name,
                "이화여대",
                "01012345678",
                "01",
                "1기",
                email,
                Gender.FEMALE
        );
        ReflectionTestUtils.setField(clubMember, "id", id);
        return clubMember;
    }
}
