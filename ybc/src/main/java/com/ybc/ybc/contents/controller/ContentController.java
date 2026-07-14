package com.ybc.ybc.contents.controller;

import com.ybc.ybc.contents.dto.response.HomeContentResponse;
import com.ybc.ybc.contents.dto.response.PrivacyPolicyResponse;
import com.ybc.ybc.contents.service.HomeContentService;
import com.ybc.ybc.contents.service.PrivacyPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ContentController {
    private final HomeContentService homeContentService;
    private final PrivacyPolicyService privacyPolicyService;

    @GetMapping("/")
    public ResponseEntity<HomeContentResponse> getHomeContent() {
        return ResponseEntity.ok(homeContentService.getHomeContent());
    }

    @GetMapping("/privacy-policy")
    public ResponseEntity<PrivacyPolicyResponse> getPrivacyPolicy() {
        return ResponseEntity.ok(privacyPolicyService.getPrivacyPolicy());
    }
}
