package com.ybc.ybc.members.dto.response;

import com.ybc.ybc.members.domain.ClubMember;

public record ClubMemberResponse(
        Long memberId,
        String name,
        String university,
        String phone,
        String age,
        String term,
        Integer court
) {
    public static ClubMemberResponse from(ClubMember clubMember) {
        return new ClubMemberResponse(
                clubMember.getId(),
                clubMember.getName(),
                clubMember.getUniversity(),
                clubMember.getPhone(),
                clubMember.getAge(),
                clubMember.getTerm(),
                clubMember.getCourt()
        );
    }
}
