package com.ybc.ybc.members.service;

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

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubMemberService {
    private final ClubMemberRepository clubMemberRepository;
    private final ClubMemberSyncService clubMemberSyncService;

    public ClubMemberListResponse getMembers() {
        clubMemberSyncService.reconcileFinalPassMembers();
        List<ClubMember> members = clubMemberRepository.findAll(Sort.by(Sort.Order.asc("id")));
        return new ClubMemberListResponse(
                summarize(members),
                members.stream().map(ClubMemberResponse::from).toList()
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
                request.court()
        );
        return ClubMemberResponse.from(clubMemberRepository.save(clubMember));
    }

    // 부원 정보 수정
    @Transactional
    public ClubMemberResponse updateMember(Long memberId, ClubMemberUpdateRequest request) {
        ClubMember clubMember = getClubMember(memberId);
        clubMember.update(
                request.name(),
                request.age(),
                request.university(),
                request.term(),
                request.court()
        );
        return ClubMemberResponse.from(clubMember);
    }

    public ClubMemberResponse searchMember(String name) {
        if (!StringUtils.hasText(name)) {
            throw new YbcException(ExceptionCode.ILLEGAL_ARGUMENT);
        }

        ClubMember clubMember = clubMemberRepository.findFirstByNameOrderByIdAsc(name.trim())
                .orElseThrow(() -> new YbcException(ExceptionCode.MEMBER_NOT_FOUND));
        return ClubMemberResponse.from(clubMember);
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

    private ClubMemberSummaryResponse summarize(List<ClubMember> members) {
        return new ClubMemberSummaryResponse(
                members.size(),
                countByCourt(members, 1),
                countByCourt(members, 2),
                countByCourt(members, 3),
                countByCourt(members, 4)
        );
    }

    private long countByCourt(List<ClubMember> members, int court) {
        return members.stream()
                .filter(member -> member.getCourt() != null && member.getCourt() == court)
                .count();
    }
}
