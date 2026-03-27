package com.ybc.ybc.auth.dto;

public record KakaoLoginResult(
        boolean firstLogin,
        MemberSummary member
) {
}
