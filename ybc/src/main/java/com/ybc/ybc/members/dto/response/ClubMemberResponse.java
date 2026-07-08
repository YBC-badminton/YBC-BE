package com.ybc.ybc.members.dto.response;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.global.util.PhoneNumberUtils;
import com.ybc.ybc.members.domain.ClubMember;

public record ClubMemberResponse(
        Long memberId,
        String name,
        String university,
        String phone,
        String email,
        Gender gender,
        String age,
        String term,
        boolean isMapoResident,
        boolean isAdmin
) {
    public static ClubMemberResponse from(ClubMember clubMember, boolean isAdmin) {
        return new ClubMemberResponse(
                clubMember.getId(),
                clubMember.getName(),
                clubMember.getUniversity(),
                PhoneNumberUtils.format(clubMember.getPhone()),
                clubMember.getEmail(),
                clubMember.getGender(),
                clubMember.getAge(),
                clubMember.getTerm(),
                clubMember.isMapoResident(),
                isAdmin
        );
    }
}
