package com.ybc.ybc.faqs.controller;

import com.ybc.ybc.faqs.dto.request.FaqCreateRequest;
import com.ybc.ybc.faqs.dto.request.FaqUpdateRequest;
import com.ybc.ybc.faqs.dto.response.FaqResponse;
import com.ybc.ybc.faqs.service.FaqService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/content/faqs")
public class AdminFaqController {
    private final FaqService faqService;

    @PostMapping
    public ResponseEntity<FaqResponse> createFaq(@Valid @RequestBody FaqCreateRequest request) {
        return ResponseEntity.ok(faqService.createFaq(request));
    }

    @PatchMapping("/{faqId}")
    public ResponseEntity<FaqResponse> updateFaq(@PathVariable Long faqId,
                                                 @Valid @RequestBody FaqUpdateRequest request) {
        return ResponseEntity.ok(faqService.updateFaq(faqId, request));
    }

    @DeleteMapping("/{faqId}")
    public ResponseEntity<Void> deleteFaq(@PathVariable Long faqId) {
        faqService.deleteFaq(faqId);
        return ResponseEntity.ok().build();
    }
}
