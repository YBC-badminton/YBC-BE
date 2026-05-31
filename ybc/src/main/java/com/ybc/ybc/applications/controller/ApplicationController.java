package com.ybc.ybc.applications.controller;

import com.ybc.ybc.applications.dto.request.ApplicationRequest;
import com.ybc.ybc.applications.dto.response.ApplicationAvailabilityResponse;
import com.ybc.ybc.applications.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    /*
    * 현재 지원 가능 여부 조회
    * */
    @GetMapping("/availability")
    public ApplicationAvailabilityResponse getApplicationAvailability() {
        return applicationService.getApplicationAvailability();
    }

    /*
    * 지원서 작성
    * */
    @PostMapping
    public ResponseEntity<Void> createApplication(@Valid @RequestBody ApplicationRequest request) {
        applicationService.createApplication(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
