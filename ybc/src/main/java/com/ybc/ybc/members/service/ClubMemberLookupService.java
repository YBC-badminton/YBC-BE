package com.ybc.ybc.members.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.members.domain.ClubMember;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubMemberLookupService {
    private final ClubMemberRepository clubMemberRepository;
    private final MemberRepository memberRepository;

    public Map<Long, ClubMember> getClubMembersByMemberId(Iterable<Member> members) {
        Set<Long> memberIds = new LinkedHashSet<>();
        Map<Long, String> emailsByMemberId = new LinkedHashMap<>();
        members.forEach(member -> {
            if (member == null || member.getId() == null) {
                return;
            }
            memberIds.add(member.getId());
            emailsByMemberId.put(member.getId(), normalizeEmail(member.getEmail()));
        });
        if (memberIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ClubMember> clubMembersByMemberId = clubMemberRepository.findAllByMemberIdInOrderByIdAsc(memberIds).stream()
                .filter(clubMember -> clubMember.getMemberId() != null)
                .collect(Collectors.toMap(
                        ClubMember::getMemberId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        Set<Long> unresolvedMemberIds = memberIds.stream()
                .filter(memberId -> !clubMembersByMemberId.containsKey(memberId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unresolvedMemberIds.isEmpty()) {
            return clubMembersByMemberId;
        }

        Set<String> unresolvedEmails = unresolvedMemberIds.stream()
                .map(emailsByMemberId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unresolvedEmails.isEmpty()) {
            return clubMembersByMemberId;
        }

        List<ClubMember> emailMatchedClubMembers = clubMemberRepository.findAllByNormalizedEmailInOrderByIdAsc(unresolvedEmails);
        Set<Long> activeLinkedMemberIds = findExistingMemberIds(
                emailMatchedClubMembers.stream()
                        .map(ClubMember::getMemberId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new))
        );

        Map<String, List<ClubMember>> clubMembersByEmail = emailMatchedClubMembers
                .stream()
                .filter(clubMember -> normalizeEmail(clubMember.getEmail()) != null)
                .collect(Collectors.groupingBy(
                        clubMember -> Objects.requireNonNull(normalizeEmail(clubMember.getEmail())),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        unresolvedMemberIds.forEach(memberId -> {
            String email = emailsByMemberId.get(memberId);
            if (email == null) {
                return;
            }

            ClubMember linkedClubMember = clubMembersByEmail.getOrDefault(email, List.of())
                    .stream()
                    .filter(clubMember -> isRelinkable(clubMember, memberId, activeLinkedMemberIds))
                    .findFirst()
                    .orElse(null);
            if (linkedClubMember == null) {
                return;
            }

            clubMembersByMemberId.put(memberId, linkedClubMember);
        });

        return clubMembersByMemberId;
    }

    public String resolveDisplayName(Member member, Map<Long, ClubMember> clubMembersByMemberId) {
        if (member == null) {
            return "알 수 없음";
        }

        ClubMember clubMember = clubMembersByMemberId.get(member.getId());
        if (clubMember != null && StringUtils.hasText(clubMember.getName())) {
            return clubMember.getName();
        }
        return member.getNickname();
    }

    private Set<Long> findExistingMemberIds(Set<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Set.of();
        }
        return StreamSupport.stream(memberRepository.findAllById(memberIds).spliterator(), false)
                .map(Member::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isRelinkable(ClubMember clubMember, Long memberId, Set<Long> activeLinkedMemberIds) {
        if (clubMember.getMemberId() == null) {
            return true;
        }
        if (clubMember.getMemberId().equals(memberId)) {
            return true;
        }
        return !activeLinkedMemberIds.contains(clubMember.getMemberId());
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
