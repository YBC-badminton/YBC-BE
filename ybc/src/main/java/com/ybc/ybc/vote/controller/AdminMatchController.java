package com.ybc.ybc.vote.controller;

import com.ybc.ybc.vote.dto.request.AdminMatchCourtRequest;
import com.ybc.ybc.vote.dto.response.AdminMatchDetailResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchRegistrationResponse;
import com.ybc.ybc.vote.service.AdminMatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/matches")
public class AdminMatchController {
    private final AdminMatchService adminMatchService;

    /**
     * GET /admin/matches/{matchId}
     * 대진 조회
     */
    @GetMapping("/{matchId}")
    public AdminMatchDetailResponse getMatch(@PathVariable Long matchId) {
        return adminMatchService.getMatch(matchId);
    }

    /**
     * PATCH /admin/matches/{matchId}
     * 대진 수정
     */
    @PatchMapping("/{matchId}")
    public AdminMatchRegistrationResponse updateMatch(@PathVariable Long matchId,
                                                      @Valid @RequestBody List<@Valid AdminMatchCourtRequest> request) {
        return adminMatchService.updateMatch(matchId, request);
    }
}
