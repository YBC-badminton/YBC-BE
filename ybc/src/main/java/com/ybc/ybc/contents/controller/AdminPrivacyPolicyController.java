package com.ybc.ybc.contents.controller;

import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.contents.dto.request.PrivacyPolicyUpdateRequest;
import com.ybc.ybc.contents.dto.response.PrivacyPolicyResponse;
import com.ybc.ybc.contents.service.PrivacyPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/content/privacy-policy")
public class AdminPrivacyPolicyController {
    private final PrivacyPolicyService privacyPolicyService;

    @PatchMapping
    public ResponseEntity<PrivacyPolicyResponse> updatePrivacyPolicy(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestBody(required = false) PrivacyPolicyUpdateRequest request
    ) {
        return ResponseEntity.ok(privacyPolicyService.updatePrivacyPolicy(memberPrincipal.getId(), request));
    }
}
