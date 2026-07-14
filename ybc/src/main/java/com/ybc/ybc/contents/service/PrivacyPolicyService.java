package com.ybc.ybc.contents.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.contents.domain.PrivacyPolicy;
import com.ybc.ybc.contents.dto.request.PrivacyPolicyUpdateRequest;
import com.ybc.ybc.contents.dto.response.PrivacyPolicyResponse;
import com.ybc.ybc.contents.repository.PrivacyPolicyRepository;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrivacyPolicyService {
    private final MemberService memberService;
    private final PrivacyPolicyRepository privacyPolicyRepository;

    public PrivacyPolicyResponse getPrivacyPolicy() {
        return PrivacyPolicyResponse.from(getPrivacyPolicyEntity());
    }

    @Transactional
    public PrivacyPolicyResponse updatePrivacyPolicy(Long memberId, PrivacyPolicyUpdateRequest request) {
        validateAdmin(memberId);
        String content = extractContent(request);

        PrivacyPolicy privacyPolicy = privacyPolicyRepository.findFirstByOrderByIdAsc()
                .map(existingPrivacyPolicy -> {
                    existingPrivacyPolicy.update(content);
                    return existingPrivacyPolicy;
                })
                .orElseGet(() -> PrivacyPolicy.create(content));

        PrivacyPolicy savedPrivacyPolicy = privacyPolicyRepository.saveAndFlush(privacyPolicy);
        return PrivacyPolicyResponse.from(savedPrivacyPolicy);
    }

    private void validateAdmin(Long memberId) {
        Member member = memberService.getMemberById(memberId);
        if (!member.isAdmin()) {
            throw new YbcException(ExceptionCode.UNAUTHORIZED_USER);
        }
    }

    private String extractContent(PrivacyPolicyUpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new YbcException(ExceptionCode.ILLEGAL_ARGUMENT);
        }
        return request.content();
    }

    private PrivacyPolicy getPrivacyPolicyEntity() {
        return privacyPolicyRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new YbcException(ExceptionCode.PRIVACY_POLICY_NOT_FOUND));
    }
}
