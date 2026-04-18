package com.ybc.ybc.vote.controller;

import com.ybc.ybc.vote.dto.request.AdminVoteCreateRequest;
import com.ybc.ybc.vote.dto.response.AdminCompletedVoteResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteListItemResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteListResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteQueueResponse;
import com.ybc.ybc.vote.dto.response.AdminVoteReservationResponse;
import com.ybc.ybc.vote.service.AdminVoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/votes")
public class AdminVoteController {
    private final AdminVoteService adminVoteService;

    /**
     * POST /admin/votes
     * 투표 예약 (추가 참여는 자동 생성)
     */
    @PostMapping
    public ResponseEntity<AdminVoteReservationResponse> createVote(@Valid @RequestBody AdminVoteCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminVoteService.createVote(request));
    }

    /**
     * PATCH /admin/votes/{voteId}
     * 투표 정보 수정
     */
    @PatchMapping("/{voteId}")
    public AdminVoteListItemResponse updateVote(@PathVariable Long voteId,
                                                @Valid @RequestBody AdminVoteCreateRequest request) {
        return adminVoteService.updateVote(voteId, request);
    }

    /**
     * GET /admin/votes/queue
     * 투표 예약 대기열 조회
     */
    @GetMapping("/queue")
    public AdminVoteQueueResponse getVoteQueue() {
        return adminVoteService.getVoteQueue();
    }

    /**
     * GET /admin/votes
     * 투표 현황 리스트 조회
     */
    @GetMapping(params = "!status")
    public AdminVoteListResponse getVoteManagement() {
        return adminVoteService.getVoteManagement();
    }

    /**
     * GET /admin/votes?status=completed
     * 투표 완료된 운동 리스트 조회
     */
    @GetMapping(params = "status=completed")
    public List<AdminCompletedVoteResponse> getCompletedVotes() {
        return adminVoteService.getCompletedVotes();
    }

    /**
     * DELETE /admin/votes/{voteId}
     * 투표 삭제
     */
    @DeleteMapping("/{voteId}")
    public ResponseEntity<Void> deleteVote(@PathVariable Long voteId) {
        adminVoteService.deleteVote(voteId);
        return ResponseEntity.ok().build();
    }
}
