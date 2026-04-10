package com.ybc.ybc.members.controller;

import com.ybc.ybc.members.dto.request.ClubMemberCreateRequest;
import com.ybc.ybc.members.dto.request.ClubMemberUpdateRequest;
import com.ybc.ybc.members.dto.response.ClubMemberListResponse;
import com.ybc.ybc.members.dto.response.ClubMemberResponse;
import com.ybc.ybc.members.service.ClubMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/members")
public class AdminMemberController {
    private final ClubMemberService clubMemberService;

    /**
     * GET /admin/members
     * 부원 명단 조회
     */
    @GetMapping
    public ClubMemberListResponse getMembers() {
        return clubMemberService.getMembers();
    }

    /**
     * GET /admin/members/search?name={name}
     * 부원 검색
     */
    @GetMapping("/search")
    public ClubMemberResponse searchMember(@RequestParam String name) {
        return clubMemberService.searchMember(name);
    }

    /**
     * POST /admin/members
     * 부원 추가
     */
    @PostMapping
    public ClubMemberResponse createMember(@Valid @RequestBody ClubMemberCreateRequest request) {
        return clubMemberService.createMember(request);
    }

    /**
     * PATCH /admin/members/{memberId}
     * 부원 정보 수정
     */
    @PatchMapping("/{memberId}")
    public ClubMemberResponse updateMember(@PathVariable Long memberId,
                                           @Valid @RequestBody ClubMemberUpdateRequest request) {
        return clubMemberService.updateMember(memberId, request);
    }

    /**
     * DELETE /admin/members/{memberId}
     * 부원 삭제
     */
    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long memberId) {
        clubMemberService.deleteMember(memberId);
        return ResponseEntity.ok().build();
    }
}
