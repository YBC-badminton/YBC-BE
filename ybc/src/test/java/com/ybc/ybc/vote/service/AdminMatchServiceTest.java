package com.ybc.ybc.vote.service;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.members.service.ClubMemberLookupService;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.domain.VoteMatchGroup;
import com.ybc.ybc.vote.dto.response.AdminMatchRegistrationResponse;
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
import static org.mockito.Mockito.inOrder;
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
    private ClubMemberLookupService clubMemberLookupService;

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
                clubMemberLookupService,
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
        when(clubMemberLookupService.getClubMembersByMemberId(any()))
                .thenThrow(new RuntimeException("broken club_member rows"));

        AdminMatchParticipantListResponse response = adminMatchService.getParticipants(1L);

        assertThat(response.voteId()).isEqualTo(1L);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.participants()).hasSize(1);
        assertThat(response.participants().get(0).participantId()).isEqualTo(100L);
        assertThat(response.participants().get(0).name()).isEqualTo("닉네임회원");
        assertThat(response.participants().get(0).gender()).isNull();
        assertThat(response.participants().get(0).level()).isNull();
    }

    @Test
    @DisplayName("정기 운동이 마감된 뒤 남은 회원 정원만큼 게스트도 대진 참가자에 포함된다")
    void getParticipantsIncludesAdditionalGuestsFromClosedVoteVacancies() {
        Vote vote = Vote.builder()
                .activityType(ActivityType.REGULAR)
                .title("정기 운동")
                .activityDate(LocalDate.of(2026, 5, 22))
                .activityTime("14:00 ~ 17:00")
                .location("망원체육관")
                .memo("memo")
                .voteStartAt(LocalDateTime.of(2026, 5, 22, 18, 0))
                .voteEndAt(LocalDateTime.of(2026, 5, 22, 21, 0))
                .capacity(4)
                .build();
        ReflectionTestUtils.setField(vote, "id", 1L);

        VoteAttendance firstAttendance = attendance(vote, member(10L, "member1"), 100L);
        VoteAttendance secondAttendance = attendance(vote, member(11L, "member2"), 101L);

        Guest firstGuest = guest(vote, member(20L, "host1"), 200L, "게스트1");
        Guest secondGuest = guest(vote, member(21L, "host2"), 201L, "게스트2");
        Guest thirdGuest = guest(vote, member(22L, "host3"), 202L, "게스트3");
        Guest fourthGuest = guest(vote, member(23L, "host4"), 203L, "게스트4");
        Guest fifthGuest = guest(vote, member(24L, "host5"), 204L, "게스트5");
        Guest sixthGuest = guest(vote, member(25L, "host6"), 205L, "게스트6");

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteRepository.findAllByOriginalVote_IdOrderByIdAsc(1L)).thenReturn(List.of());
        when(voteRepository
                .findAllByActivityTypeAndActivityDateAndActivityTimeAndLocationAndVoteStartAtAndTitleAndOriginalVoteIsNullOrderByIdAsc(
                        any(), any(), any(), any(), any(), any()
                ))
                .thenReturn(List.of());
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteIds(Set.of(1L)))
                .thenReturn(List.of(firstAttendance, secondAttendance));
        when(guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(Set.of(1L)))
                .thenReturn(List.of(firstGuest, secondGuest, thirdGuest, fourthGuest, fifthGuest, sixthGuest));
        when(clubMemberLookupService.getClubMembersByMemberId(any())).thenReturn(java.util.Map.of());

        AdminMatchParticipantListResponse response = adminMatchService.getParticipants(1L);

        assertThat(response.totalCount()).isEqualTo(7);
        assertThat(response.participants()).filteredOn(participant -> participant.participantType().equals("GUEST"))
                .hasSize(5);
        assertThat(response.participants()).filteredOn(participant -> participant.participantType().equals("GUEST"))
                .extracting(participant -> participant.name())
                .containsExactly("게스트1", "게스트2", "게스트3", "게스트4", "게스트5");
    }

    @Test
    @DisplayName("이벤트 운동이 마감된 뒤 여석만큼 게스트를 대진 참가자에 포함한다")
    void getParticipantsIncludesEventGuestsWithinVacanciesAfterVoteClosed() {
        LocalDateTime now = LocalDateTime.now();
        Vote vote = Vote.builder()
                .activityType(ActivityType.EVENT)
                .title("이벤트 운동")
                .activityDate(now.toLocalDate().plusDays(1))
                .activityTime("14:00 ~ 17:00")
                .location("망원체육관")
                .memo("memo")
                .voteStartAt(now.minusDays(1))
                .voteEndAt(now.minusMinutes(1))
                .capacity(4)
                .build();
        ReflectionTestUtils.setField(vote, "id", 1L);

        VoteAttendance firstAttendance = attendance(vote, member(10L, "member1"), 100L);
        VoteAttendance secondAttendance = attendance(vote, member(11L, "member2"), 101L);
        Guest firstGuest = guest(vote, member(20L, "host1"), 200L, "게스트1");
        Guest secondGuest = guest(vote, member(21L, "host2"), 201L, "게스트2");
        Guest waitingGuest = guest(vote, member(22L, "host3"), 202L, "게스트3");

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteRepository.findAllByOriginalVote_IdOrderByIdAsc(1L)).thenReturn(List.of());
        when(voteRepository
                .findAllByActivityTypeAndActivityDateAndActivityTimeAndLocationAndVoteStartAtAndTitleAndOriginalVoteIsNullOrderByIdAsc(
                        any(), any(), any(), any(), any(), any()
                ))
                .thenReturn(List.of());
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteIds(Set.of(1L)))
                .thenReturn(List.of(firstAttendance, secondAttendance));
        when(guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(Set.of(1L)))
                .thenReturn(List.of(firstGuest, secondGuest, waitingGuest));
        when(clubMemberLookupService.getClubMembersByMemberId(any())).thenReturn(java.util.Map.of());

        AdminMatchParticipantListResponse response = adminMatchService.getParticipants(1L);

        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.participants()).filteredOn(participant -> participant.participantType().equals("GUEST"))
                .extracting(participant -> participant.name())
                .containsExactly("게스트1", "게스트2");
    }

    @Test
    @DisplayName("추가참여 투표로 대진 조회 시 원본 투표 대진을 반환한다")
    void getMatchByVoteIdReturnsBaseVoteMatchForAdditionalVote() {
        Vote baseVote = Vote.builder()
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
        ReflectionTestUtils.setField(baseVote, "id", 1L);

        Vote additionalVote = Vote.createAdditionalVote(
                baseVote,
                LocalDateTime.of(2026, 5, 23, 21, 0),
                LocalDateTime.of(2026, 5, 24, 12, 0),
                5
        );
        ReflectionTestUtils.setField(additionalVote, "id", 2L);

        VoteMatchGroup matchGroup = VoteMatchGroup.create(baseVote);
        ReflectionTestUtils.setField(matchGroup, "id", 100L);

        when(voteRepository.findById(2L)).thenReturn(Optional.of(additionalVote));
        when(voteMatchGroupRepository.findByVote_Id(1L)).thenReturn(Optional.of(matchGroup));
        when(voteMatchRepository.findAllWithParticipantsByMatchGroupId(100L)).thenReturn(List.of());

        AdminMatchRegistrationResponse response = adminMatchService.getMatchByVoteId(2L);

        assertThat(response.voteId()).isEqualTo(1L);
        assertThat(response.matchId()).isEqualTo(100L);
        assertThat(response.courtCount()).isZero();
        assertThat(response.matches()).isEmpty();
    }

    @Test
    @DisplayName("대진 완성을 취소하면 경기 데이터와 대진 그룹을 순서대로 삭제한다")
    void deleteMatchDeletesMatchesBeforeMatchGroup() {
        Vote vote = Vote.builder()
                .activityType(ActivityType.REGULAR)
                .title("정기 운동")
                .activityDate(LocalDate.of(2026, 5, 23))
                .activityTime("14:00 ~ 17:00")
                .location("망원체육관")
                .voteStartAt(LocalDateTime.of(2026, 5, 22, 18, 0))
                .voteEndAt(LocalDateTime.of(2026, 5, 22, 21, 0))
                .capacity(24)
                .build();
        ReflectionTestUtils.setField(vote, "id", 1L);
        VoteMatchGroup matchGroup = VoteMatchGroup.create(vote);
        ReflectionTestUtils.setField(matchGroup, "id", 100L);
        when(voteMatchGroupRepository.findById(100L)).thenReturn(Optional.of(matchGroup));

        adminMatchService.deleteMatch(100L);

        var deletionOrder = inOrder(voteMatchRepository, voteMatchGroupRepository);
        deletionOrder.verify(voteMatchRepository).deleteAllByMatchGroup_Id(100L);
        deletionOrder.verify(voteMatchGroupRepository).delete(matchGroup);
    }

    private VoteAttendance attendance(Vote vote, Member member, Long id) {
        VoteAttendance attendance = VoteAttendance.builder()
                .vote(vote)
                .member(member)
                .attendanceStatus(true)
                .build();
        ReflectionTestUtils.setField(attendance, "id", id);
        return attendance;
    }

    private Guest guest(Vote vote, Member inviter, Long id, String name) {
        Guest guest = Guest.builder()
                .vote(vote)
                .member(inviter)
                .name(name)
                .gender(Gender.FEMALE)
                .level("A")
                .build();
        ReflectionTestUtils.setField(guest, "id", id);
        return guest;
    }

    private Member member(Long id, String socialId) {
        Member member = Member.createSocialMember(
                SocialProvider.KAKAO,
                socialId,
                "member-" + socialId,
                socialId + "@example.com"
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
