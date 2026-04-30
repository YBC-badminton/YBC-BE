package com.ybc.ybc.vote.controller;

import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.vote.dto.request.VoteAttendanceRequest;
import com.ybc.ybc.vote.dto.request.VoteGuestRequest;
import com.ybc.ybc.vote.dto.response.VoteAttendanceStatusResponse;
import com.ybc.ybc.vote.dto.response.VoteAttendeeListResponse;
import com.ybc.ybc.vote.dto.response.VoteDetailResponse;
import com.ybc.ybc.vote.dto.response.VoteGuestListResponse;
import com.ybc.ybc.vote.dto.response.VoteHistoryResponse;
import com.ybc.ybc.vote.dto.response.VoteListResponse;
import com.ybc.ybc.vote.service.VoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * 투표 식별자에 해당하는 상세 정보를 조회하여 반환한다.
     */
    @GetMapping("/{voteId}")
    public ResponseEntity<VoteDetailResponse> getVote(@PathVariable Long voteId) {
        return ResponseEntity.ok(voteService.getVote(voteId));
    }

    /**
     * 특정 투표에 대한 게스트 수와 게스트 상세 목록을 조회하여 반환한다.
     */
    @GetMapping("/{voteId}/guests")
    public ResponseEntity<VoteGuestListResponse> getVoteGuests(@PathVariable Long voteId) {
        return ResponseEntity.ok(voteService.getVoteGuests(voteId));
    }

    /**
     * 현재 로그인한 회원의 투표 참석 여부를 조회하여 반환한다.
     */
    @GetMapping("/{voteId}/attendance")
    public ResponseEntity<VoteAttendanceStatusResponse> getVoteAttendance(@PathVariable Long voteId,
                                                                          @AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        return ResponseEntity.ok(voteService.getVoteAttendance(voteId, memberPrincipal.getId()));
    }

    /**
     * 특정 투표에서 참석 상태가 true인 회원 목록을 조회하여 반환한다.
     */
    @GetMapping("/{voteId}/attendees")
    public ResponseEntity<VoteAttendeeListResponse> getVoteAttendees(@PathVariable Long voteId) {
        return ResponseEntity.ok(voteService.getVoteAttendees(voteId));
    }

    /**
     * 특정 투표에서 참석 상태가 false인 회원 목록을 조회하여 반환한다.
     */
    @GetMapping("/{voteId}/absentees")
    public ResponseEntity<VoteAttendeeListResponse> getVoteAbsentees(@PathVariable Long voteId) {
        return ResponseEntity.ok(voteService.getVoteAbsentees(voteId));
    }

    /**
     * 현재 로그인한 회원의 투표 참석 여부를 생성하거나 수정한다.
     */
    @PutMapping("/{voteId}/attendance")
    public ResponseEntity<Void> saveVoteAttendance(@PathVariable Long voteId,
                                                   @AuthenticationPrincipal MemberPrincipal memberPrincipal,
                                                   @Valid @RequestBody VoteAttendanceRequest request) {
        voteService.saveVoteAttendance(voteId, memberPrincipal.getId(), request);
        return ResponseEntity.ok().build();
    }

    /**
     * 현재 로그인한 회원 소속의 게스트 참여 정보를 생성한다.
     */
    @PostMapping("/{voteId}/guests")
    public ResponseEntity<Void> createGuest(@PathVariable Long voteId,
                                            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
                                            @Valid @RequestBody VoteGuestRequest request) {
        voteService.createGuest(voteId, memberPrincipal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 현재 열려 있는 투표 수와 올해 생성된 투표 수를 함께 조회하여 반환한다.
     */
    @GetMapping("/history")
    public ResponseEntity<VoteHistoryResponse> getVoteHistory() {
        return ResponseEntity.ok(voteService.getVoteHistory());
    }
}
