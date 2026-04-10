package com.ybc.ybc.vote.controller;

import com.ybc.ybc.vote.dto.request.AdminVoteCreateRequest;
import com.ybc.ybc.vote.dto.response.AdminVoteListResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteQueueResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteReservationResponse;
import com.ybc.ybc.vote.service.AdminVoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/votes")
public class AdminVoteController {
    private final AdminVoteService adminVoteService;

    @PostMapping
    public AdminVoteReservationResponse createVote(@Valid @RequestBody AdminVoteCreateRequest request) {
        return adminVoteService.createVote(request);
    }

    @GetMapping("/queue")
    public AdminVoteQueueResponse getVoteQueue() {
        return adminVoteService.getVoteQueue();
    }

    @GetMapping
    public AdminVoteListResponse getVoteManagement() {
        return adminVoteService.getVoteManagement();
    }
}
