package com.ybc.ybc.applications.controller;

import com.ybc.ybc.applications.dto.request.ApplicationRequest;
import com.ybc.ybc.applications.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    /*
    * 지원서 작성
    * */
    @PostMapping
    public ResponseEntity<Void> createApplication(@Valid @RequestBody ApplicationRequest request) {
        applicationService.createApplication(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
