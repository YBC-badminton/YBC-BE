package com.ybc.ybc.vote.controller;

import com.ybc.ybc.vote.dto.VoteListResponse;
import com.ybc.ybc.vote.service.VoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/votes")
@RequiredArgsConstructor
public class VoteController {
    private final VoteService voteService;

    /**
     * joinable 여부와 페이지 조건에 따라 투표 목록 조회를 요청받고 응답으로 반환한다.
     */
    @GetMapping
    public ResponseEntity<VoteListResponse> getVotes(@RequestParam boolean joinable,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(voteService.getVotes(joinable, page, size));
    }
}
