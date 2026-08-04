package com.ybc.ybc.members.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.dto.request.ClubMemberCreateRequest;
import com.ybc.ybc.members.dto.request.ClubMemberUpdateRequest;
import com.ybc.ybc.members.dto.response.ClubMemberListResponse;
import com.ybc.ybc.members.dto.response.ClubMemberResponse;
import com.ybc.ybc.members.dto.response.ClubMemberSummaryResponse;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubMemberService {
    private final ClubMemberRepository clubMemberRepository;
    private final ClubMemberSyncService clubMemberSyncService;
    private final MemberRepository memberRepository;

    public ClubMemberListResponse getMembers() {
        clubMemberSyncService.reconcileFinalPassMembersSafely();
        List<ClubMember> members = clubMemberRepository.findAll(Sort.by(Sort.Order.asc("id")));
        MemberLookup memberLookup = createMemberLookup(members);
        return new ClubMemberListResponse(
                summarize(members),
                members.stream()
                        .map(member -> ClubMemberResponse.from(
                                member,
                                findLinkedMember(member, memberLookup).map(Member::isAdmin).orElse(false)
                        ))
                        .toList()
        );
    }

    // 부원 추가
    @Transactional
    public ClubMemberResponse createMember(ClubMemberCreateRequest request) {
        ClubMember clubMember = ClubMember.create(
                request.name(),
                request.university(),
                request.phone(),
                request.age(),
                request.term(),
                request.isMapoResident(),
                request.email(),
                request.gender(),
                request.court()
        );
        Member linkedMember = linkLinkedMember(clubMember);
        ClubMember savedClubMember = clubMemberRepository.save(clubMember);
        return ClubMemberResponse.from(savedClubMember, linkedMember != null && linkedMember.isAdmin());
    }

    // 부원 정보 수정
    @Transactional
    public ClubMemberResponse updateMember(Long memberId, ClubMemberUpdateRequest request) {
        ClubMember clubMember = getClubMember(memberId);
        clubMember.update(
                request.name(),
                request.age(),
                request.university(),
                request.phone(),
                request.term(),
                request.isMapoResident(),
                request.email(),
                request.gender(),
                request.court()
        );
        Member linkedMember = linkLinkedMember(clubMember);
        return ClubMemberResponse.from(clubMember, linkedMember != null && linkedMember.isAdmin());
    }

    public ClubMemberResponse searchMember(String name) {
        if (!StringUtils.hasText(name)) {
            throw new YbcException(ExceptionCode.ILLEGAL_ARGUMENT);
        }

        ClubMember clubMember = clubMemberRepository.findFirstByNameOrderByIdAsc(name.trim())
                .orElseThrow(() -> new YbcException(ExceptionCode.MEMBER_NOT_FOUND));
        return ClubMemberResponse.from(
                clubMember,
                findLinkedMember(clubMember, createMemberLookup(List.of(clubMember)))
                        .map(Member::isAdmin)
                        .orElse(false)
        );
    }

    // 부원 삭제
    @Transactional
    public void deleteMember(Long memberId) {
        ClubMember clubMember = getClubMember(memberId);
        clubMemberRepository.delete(clubMember);
    }

    private ClubMember getClubMember(Long memberId) {
        return clubMemberRepository.findById(memberId)
                .orElseThrow(() -> new YbcException(ExceptionCode.MEMBER_NOT_FOUND));
    }

    private Member linkLinkedMember(ClubMember clubMember) {
        Member linkedMember = findLinkedMember(clubMember, createMemberLookup(List.of(clubMember)))
                .orElse(null);
        if (linkedMember != null && clubMember.getMemberId() == null) {
            clubMember.linkMember(linkedMember.getId());
        }
        return linkedMember;
    }

    private Optional<Member> findLinkedMember(ClubMember clubMember, MemberLookup memberLookup) {
        if (clubMember.getMemberId() != null) {
            Member member = memberLookup.membersById().get(clubMember.getMemberId());
            if (member != null) {
                return Optional.of(member);
            }
        }

        String normalizedEmail = normalizeEmail(clubMember.getEmail());
        if (!StringUtils.hasText(normalizedEmail)) {
            return Optional.empty();
        }

        return Optional.ofNullable(memberLookup.membersByEmail().get(normalizedEmail));
    }

    private MemberLookup createMemberLookup(List<ClubMember> clubMembers) {
        Set<Long> memberIds = clubMembers.stream()
                .map(ClubMember::getMemberId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Member> membersById = findMembersById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        Set<String> emails = clubMembers.stream()
                .map(ClubMember::getEmail)
                .map(this::normalizeEmail)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, Member> membersByEmail = findMembersByEmail(emails).stream()
                .collect(Collectors.toMap(
                        member -> normalizeEmail(member.getEmail()),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        return new MemberLookup(membersById, membersByEmail);
    }

    private List<Member> findMembersById(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return StreamSupport.stream(memberRepository.findAllById(memberIds).spliterator(), false)
                .toList();
    }

    private List<Member> findMembersByEmail(Collection<String> emails) {
        if (emails.isEmpty()) {
            return List.of();
        }
        return memberRepository.findAllByNormalizedEmailInOrderByIdAsc(emails);
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ClubMemberSummaryResponse summarize(List<ClubMember> members) {
        long mapoResidentCount = members.stream()
                .filter(ClubMember::isMapoResident)
                .count();
        return new ClubMemberSummaryResponse(members.size(), mapoResidentCount);
    }

    private record MemberLookup(
            Map<Long, Member> membersById,
            Map<String, Member> membersByEmail
    ) {
    }
}
