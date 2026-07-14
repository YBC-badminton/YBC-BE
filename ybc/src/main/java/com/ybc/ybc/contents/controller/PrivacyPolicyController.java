package com.ybc.ybc.contents.controller;

import com.ybc.ybc.contents.dto.response.PrivacyPolicyResponse;
import com.ybc.ybc.contents.service.PrivacyPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PrivacyPolicyController {
    private final PrivacyPolicyService privacyPolicyService;

    @GetMapping("/privacy-policy")
    public ResponseEntity<PrivacyPolicyResponse> getPrivacyPolicy() {
        return ResponseEntity.ok(privacyPolicyService.getPrivacyPolicy());
    }
}
