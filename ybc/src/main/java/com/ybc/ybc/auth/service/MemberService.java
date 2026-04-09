package com.ybc.ybc.auth.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public Member getMemberById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new YbcException(ExceptionCode.MEMBER_NOT_FOUND));
    }



}
