package com.ybc.ybc.faqs.controller;

import com.ybc.ybc.faqs.dto.response.FaqListResponse;
import com.ybc.ybc.faqs.service.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/faqs")
public class FaqController {
    private final FaqService faqService;

    @GetMapping
    public ResponseEntity<FaqListResponse> getFaqs() {
        return ResponseEntity.ok(faqService.getFaqs());
    }
}
