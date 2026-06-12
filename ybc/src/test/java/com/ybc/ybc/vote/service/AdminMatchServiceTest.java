package com.ybc.ybc.vote.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.members.repository.ClubMemberRepository;
import com.ybc.ybc.members.service.ClubMemberSyncService;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.dto.response.AdminMatchParticipantListResponse;
import com.ybc.ybc.vote.repository.GuestRepository;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.VoteMatchGroupRepository;
import com.ybc.ybc.vote.repository.VoteMatchRepository;
import com.ybc.ybc.vote.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMatchServiceTest {

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private VoteAttendanceRepository voteAttendanceRepository;

    @Mock
    private GuestRepository guestRepository;

    @Mock
    private ClubMemberRepository clubMemberRepository;

    @Mock
    private ClubMemberSyncService clubMemberSyncService;

    @Mock
    private VoteMatchRepository voteMatchRepository;

    @Mock
    private VoteMatchGroupRepository voteMatchGroupRepository;

    private AdminMatchService adminMatchService;

    @BeforeEach
    void setUp() {
        adminMatchService = new AdminMatchService(
                voteRepository,
                voteAttendanceRepository,
                guestRepository,
                clubMemberRepository,
                clubMemberSyncService,
                voteMatchRepository,
                voteMatchGroupRepository
        );
    }

    @Test
    @DisplayName("부원 프로필 조회가 실패해도 대진 참석자 목록은 회원 닉네임으로 반환한다")
    void getParticipantsFallsBackToMemberNicknameWhenClubMemberLookupFails() {
        Vote vote = Vote.builder()
                .activityType(ActivityType.REGULAR)
                .title("정기 운동")
                .activityDate(LocalDate.of(2026, 5, 23))
                .activityTime("14:00 ~ 17:00")
                .location("망원체육관")
                .memo("memo")
                .voteStartAt(LocalDateTime.of(2026, 5, 23, 18, 0))
                .voteEndAt(LocalDateTime.of(2026, 5, 23, 21, 0))
                .capacity(24)
                .build();
        ReflectionTestUtils.setField(vote, "id", 1L);

        Member member = Member.createSocialMember(
                SocialProvider.KAKAO,
                "123456",
                "닉네임회원",
                "member@example.com"
        );
        ReflectionTestUtils.setField(member, "id", 10L);

        VoteAttendance attendance = VoteAttendance.builder()
                .vote(vote)
                .member(member)
                .attendanceStatus(true)
                .build();
        ReflectionTestUtils.setField(attendance, "id", 100L);

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteRepository.findAllByOriginalVote_IdOrderByIdAsc(1L)).thenReturn(List.of());
        when(voteRepository
                .findAllByActivityTypeAndActivityDateAndActivityTimeAndLocationAndVoteStartAtAndTitleAndOriginalVoteIsNullOrderByIdAsc(
                        any(), any(), any(), any(), any(), any()
                ))
                .thenReturn(List.of());
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteIds(Set.of(1L))).thenReturn(List.of(attendance));
        when(guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(Set.of(1L))).thenReturn(List.of());
        when(clubMemberRepository.findAllByMemberIdInOrderByIdAsc(Set.of(10L)))
                .thenThrow(new RuntimeException("broken club_member rows"));

        AdminMatchParticipantListResponse response = adminMatchService.getParticipants(1L);

        verify(clubMemberSyncService).reconcileFinalPassMembersSafely();
        assertThat(response.voteId()).isEqualTo(1L);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().get(0).participantId()).isEqualTo(100L);
        assertThat(response.participants().get(0).name()).isEqualTo("닉네임회원");
        assertThat(response.participants().get(0).gender()).isNull();
    }
}
