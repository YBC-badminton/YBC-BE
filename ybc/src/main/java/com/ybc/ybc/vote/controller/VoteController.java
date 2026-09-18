package com.ybc.ybc.vote.controller;

import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.vote.dto.request.FlashVoteCreateRequest;
import com.ybc.ybc.vote.dto.request.FlashVoteUpdateRequest;
import com.ybc.ybc.vote.dto.request.VoteAttendanceRequest;
import com.ybc.ybc.vote.dto.request.VoteGuestRequest;
import com.ybc.ybc.vote.dto.response.FlashVoteCreateResponse;
import com.ybc.ybc.vote.dto.response.RecentVoteListResponse;
import com.ybc.ybc.vote.dto.response.VoteAbsenteeListResponse;
import com.ybc.ybc.vote.dto.response.VoteAttendanceStatusResponse;
import com.ybc.ybc.vote.dto.response.VoteAttendeeListResponse;
import com.ybc.ybc.vote.dto.response.VoteDetailResponse;
import com.ybc.ybc.vote.dto.response.VoteGuestListResponse;
import com.ybc.ybc.vote.dto.response.VoteHistoryResponse;
import com.ybc.ybc.vote.dto.response.VoteListResponse;
import com.ybc.ybc.vote.dto.response.AdminMatchRegistrationResponse;
import com.ybc.ybc.vote.service.AdminMatchService;
import com.ybc.ybc.vote.service.VoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final AdminMatchService adminMatchService;

    /**
     * 번개 모임 투표를 생성한다.
     */
    @PostMapping("/flash")
    public ResponseEntity<FlashVoteCreateResponse> createFlashVote(@AuthenticationPrincipal MemberPrincipal memberPrincipal,
                                                                   @Valid @RequestBody FlashVoteCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(voteService.createFlashVote(request, memberPrincipal));
    }

    /**
     * 본인이 생성한 번개 모임 투표만 수정한다.
     */
    @PatchMapping("/flash/{voteId}")
    public ResponseEntity<Void> updateFlashVote(@PathVariable Long voteId,
                                                @AuthenticationPrincipal MemberPrincipal memberPrincipal,
                                                @Valid @RequestBody FlashVoteUpdateRequest request) {
        voteService.updateFlashVote(voteId, memberPrincipal.getId(), request);
        return ResponseEntity.ok().build();
    }

    /**
     * 본인이 생성한 번개 모임 투표만 삭제한다.
     */
    @DeleteMapping("/flash/{voteId}")
    public ResponseEntity<Void> deleteFlashVote(@PathVariable Long voteId,
                                                @AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        voteService.deleteFlashVote(voteId, memberPrincipal.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * open 여부와 페이지 조건에 따라 투표 목록 조회를 요청받고 응답으로 반환한다.
     */
    @GetMapping
    public ResponseEntity<VoteListResponse> getVotes(@RequestParam(required = false) Boolean open,
                                                     @RequestParam(required = false) Boolean joinable,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(voteService.getVotes(resolveOpen(open, joinable), page, size));
    }

    /**
     * 진행 중이거나 활동 예정인 최근 투표 3개를 조회한다. 비로그인 사용자는 참여 인원 수를 숨긴다.
     */
    @GetMapping("/recent")
    public ResponseEntity<RecentVoteListResponse> getRecentVotes(@AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        return ResponseEntity.ok(voteService.getRecentVotes(memberPrincipal != null));
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
     * 특정 투표에 등록된 대진을 조회하여 반환한다.
     */
    @GetMapping("/{voteId}/matches")
    public ResponseEntity<AdminMatchRegistrationResponse> getVoteMatch(@PathVariable Long voteId) {
        return ResponseEntity.ok(adminMatchService.getMatchByVoteId(voteId));
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
    public ResponseEntity<VoteAbsenteeListResponse> getVoteAbsentees(@PathVariable Long voteId) {
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
     * 현재 로그인한 회원이 추가한 게스트 참여 정보를 수정한다.
     */
    @PatchMapping("/{voteId}/guests/{guestId}")
    public ResponseEntity<Void> updateGuest(@PathVariable Long voteId,
                                            @PathVariable Long guestId,
                                            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
                                            @Valid @RequestBody VoteGuestRequest request) {
        voteService.updateGuest(voteId, guestId, memberPrincipal.getId(), request);
        return ResponseEntity.ok().build();
    }

    /**
     * 현재 로그인한 회원이 추가한 게스트 참여 정보를 삭제한다.
     */
    @DeleteMapping("/{voteId}/guests/{guestId}")
    public ResponseEntity<Void> deleteGuest(@PathVariable Long voteId,
                                            @PathVariable Long guestId,
                                            @AuthenticationPrincipal MemberPrincipal memberPrincipal) {
        voteService.deleteGuest(voteId, guestId, memberPrincipal.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 현재 열려 있는 투표 수와 올해 생성된 투표 수를 함께 조회하여 반환한다.
     */
    @GetMapping("/history")
    public ResponseEntity<VoteHistoryResponse> getVoteHistory() {
        return ResponseEntity.ok(voteService.getVoteHistory());
    }

    private boolean resolveOpen(Boolean open, Boolean joinable) {
        if (open != null) {
            return open;
        }
        if (joinable != null) {
            return joinable;
        }
        throw new YbcException(ExceptionCode.ILLEGAL_ARGUMENT);
    }
}
