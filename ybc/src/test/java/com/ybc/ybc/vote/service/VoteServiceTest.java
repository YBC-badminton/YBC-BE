package com.ybc.ybc.vote.service;

import com.ybc.ybc.applications.domain.Gender;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.vote.domain.ActivityType;
import com.ybc.ybc.vote.domain.Guest;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.domain.VoteAttendance;
import com.ybc.ybc.vote.dto.request.VoteAttendanceRequest;
import com.ybc.ybc.vote.dto.request.VoteGuestRequest;
import com.ybc.ybc.vote.dto.response.VoteAbsenteeListResponse;
import com.ybc.ybc.vote.dto.response.VoteAttendanceStatusResponse;
import com.ybc.ybc.vote.dto.response.VoteAttendeeListResponse;
import com.ybc.ybc.vote.dto.response.VoteDetailResponse;
import com.ybc.ybc.vote.dto.response.VoteGuestListResponse;
import com.ybc.ybc.vote.dto.response.VoteHistoryResponse;
import com.ybc.ybc.vote.dto.response.VoteListResponse;
import com.ybc.ybc.vote.dto.response.VoteSummaryResponse;
import com.ybc.ybc.vote.repository.GuestRepository;
import com.ybc.ybc.vote.repository.VoteAttendanceRepository;
import com.ybc.ybc.vote.repository.VoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {
    @Mock
    private VoteRepository voteRepository;

    @Mock
    private VoteAttendanceRepository voteAttendanceRepository;

    @Mock
    private GuestRepository guestRepository;

    @Mock
    private MemberService memberService;

    @InjectMocks
    private VoteService voteService;

    @Test
    @DisplayName("참여 가능한 투표 목록 조회 시 activityDate를 포함해 반환한다")
    void getVotesReturnsActivityDate() {
        VoteSummaryResponse summary = new VoteSummaryResponse(
                1L,
                "목요 정기 운동",
                ActivityType.REGULAR,
                LocalDate.of(2026, 4, 10),
                "이화체육관",
                LocalDateTime.of(2026, 4, 9, 18, 0),
                LocalDateTime.of(2026, 4, 9, 23, 0),
                "19:00",
                20,
                3L,
                true
        );
        when(voteRepository.findJoinableVoteSummaries(any(LocalDateTime.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 10), 1));

        VoteListResponse response = voteService.getVotes(true, 0, 10);

        assertThat(response.votes()).hasSize(1);
        assertThat(response.votes().get(0).activityDate()).isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(response.votes().get(0).matchRegistered()).isTrue();
        verify(voteRepository).findJoinableVoteSummaries(any(LocalDateTime.class), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    @DisplayName("투표 상세 조회 시 투표 정보와 현재 참석 인원 수를 반환한다")
    void getVoteReturnsDetailResponse() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(2L);

        VoteDetailResponse response = voteService.getVote(1L);

        assertThat(response.voteId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("목요 정기 운동");
        assertThat(response.type()).isEqualTo(ActivityType.REGULAR);
        assertThat(response.activityDate()).isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(response.location()).isEqualTo("이화체육관");
        assertThat(response.memo()).isEqualTo("테스트 모임");
        assertThat(response.currentParticipantCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("특정 투표의 게스트 목록을 반환한다")
    void getVoteGuestsReturnsGuestList() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "activityDate", LocalDate.of(2026, 4, 7));
        Member inviter = savedMember(2L, "2001");
        ReflectionTestUtils.setField(inviter, "nickname", "host-2001");

        Guest guest = Guest.builder()
                .vote(vote)
                .member(inviter)
                .name("게스트")
                .gender(Gender.FEMALE)
                .level("A")
                .build();
        ReflectionTestUtils.setField(guest, "id", 10L);

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(anyCollection())).thenReturn(List.of(guest));
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteIds(anyCollection())).thenReturn(List.of());

        VoteGuestListResponse response = voteService.getVoteGuests(1L);

        assertThat(response.totalGuestCount()).isEqualTo(1L);
        assertThat(response.confirmedGuestCount()).isEqualTo(1L);
        assertThat(response.waitingGuestCount()).isZero();
        assertThat(response.guestQuota()).isEqualTo(3);
        assertThat(response.guests()).hasSize(1);
        assertThat(response.guests().get(0).guestId()).isEqualTo(10L);
        assertThat(response.guests().get(0).voteId()).isEqualTo(1L);
        assertThat(response.guests().get(0).inviterName()).isEqualTo("host-2001");
        assertThat(response.guests().get(0).guestName()).isEqualTo("게스트");
        assertThat(response.guests().get(0).gender()).isEqualTo(Gender.FEMALE);
        assertThat(response.guests().get(0).level()).isEqualTo("A");
        assertThat(response.guests().get(0).isWaiting()).isFalse();
    }

    @Test
    @DisplayName("화요일 정원보다 많은 게스트는 대기 상태로 반환한다")
    void getVoteGuestsMarksOverflowGuestsAsWaiting() {
        Vote vote = savedVote(1L, "화요 정기 운동");
        ReflectionTestUtils.setField(vote, "activityDate", LocalDate.of(2026, 4, 7));

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteIds(anyCollection())).thenReturn(List.of());
        when(guestRepository.findAllByVote_IdInOrderByVote_IdAscIdAsc(anyCollection())).thenReturn(List.of(
                guest(vote, savedMember(2L, "2001"), 10L, "게스트1"),
                guest(vote, savedMember(3L, "2002"), 11L, "게스트2"),
                guest(vote, savedMember(4L, "2003"), 12L, "게스트3"),
                guest(vote, savedMember(5L, "2004"), 13L, "게스트4")
        ));

        VoteGuestListResponse response = voteService.getVoteGuests(1L);

        assertThat(response.totalGuestCount()).isEqualTo(4L);
        assertThat(response.confirmedGuestCount()).isEqualTo(3L);
        assertThat(response.waitingGuestCount()).isEqualTo(1L);
        assertThat(response.guestQuota()).isEqualTo(3);
        assertThat(response.guests()).hasSize(4);
        assertThat(response.guests().get(0).isWaiting()).isFalse();
        assertThat(response.guests().get(1).isWaiting()).isFalse();
        assertThat(response.guests().get(2).isWaiting()).isFalse();
        assertThat(response.guests().get(3).isWaiting()).isTrue();
    }

    @Test
    @DisplayName("현재 로그인한 회원의 투표 참석 여부를 반환한다")
    void getVoteAttendanceReturnsAttendanceStatus() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member member = savedMember(2L, "2001");
        VoteAttendance attendance = VoteAttendance.builder()
                .vote(vote)
                .member(member)
                .attendanceStatus(true)
                .build();

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.of(attendance));

        VoteAttendanceStatusResponse response = voteService.getVoteAttendance(1L, 2L);

        assertThat(response.attendanceStatus()).isTrue();
    }

    @Test
    @DisplayName("아직 응답하지 않은 회원의 투표 참석 여부는 null을 반환한다")
    void getVoteAttendanceReturnsNullWhenAttendanceIsMissing() {
        Vote vote = savedVote(1L, "목요 정기 운동");

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.empty());

        VoteAttendanceStatusResponse response = voteService.getVoteAttendance(1L, 2L);

        assertThat(response.attendanceStatus()).isNull();
    }

    @Test
    @DisplayName("특정 투표에서 참석 상태가 true인 회원 목록을 참석 순서대로 반환하고 초과 인원은 대기 처리한다")
    void getVoteAttendeesReturnsAttendeeList() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "capacity", 1);
        Member firstMember = savedMember(2L, "2001");
        Member secondMember = savedMember(3L, "2002");
        ReflectionTestUtils.setField(firstMember, "nickname", "member-2001");
        ReflectionTestUtils.setField(secondMember, "nickname", "member-2002");

        VoteAttendance firstAttendance = VoteAttendance.builder()
                .vote(vote)
                .member(firstMember)
                .attendanceStatus(true)
                .build();
        VoteAttendance secondAttendance = VoteAttendance.builder()
                .vote(vote)
                .member(secondMember)
                .attendanceStatus(true)
                .build();
        ReflectionTestUtils.setField(firstAttendance, "attendingAt", LocalDateTime.of(2026, 6, 12, 10, 0));
        ReflectionTestUtils.setField(secondAttendance, "attendingAt", LocalDateTime.of(2026, 6, 12, 10, 5));

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findAllActiveWithMemberByVoteId(1L))
                .thenReturn(List.of(firstAttendance, secondAttendance));

        VoteAttendeeListResponse response = voteService.getVoteAttendees(1L);

        assertThat(response.totalAttendeeCount()).isEqualTo(2L);
        assertThat(response.attendees()).hasSize(2);
        assertThat(response.attendees().get(0).attendeeNumber()).isEqualTo(1);
        assertThat(response.attendees().get(0).memberId()).isEqualTo(2L);
        assertThat(response.attendees().get(0).nickname()).isEqualTo("member-2001");
        assertThat(response.attendees().get(0).isWaiting()).isFalse();
        assertThat(response.attendees().get(1).attendeeNumber()).isEqualTo(2);
        assertThat(response.attendees().get(1).memberId()).isEqualTo(3L);
        assertThat(response.attendees().get(1).nickname()).isEqualTo("member-2002");
        assertThat(response.attendees().get(1).isWaiting()).isTrue();
    }

    @Test
    @DisplayName("특정 투표에서 참석 상태가 false인 회원 목록과 총원을 반환한다")
    void getVoteAbsenteesReturnsAbsenteeList() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member firstMember = savedMember(2L, "2001");
        Member secondMember = savedMember(3L, "2002");
        ReflectionTestUtils.setField(firstMember, "nickname", "member-2001");
        ReflectionTestUtils.setField(secondMember, "nickname", "member-2002");

        VoteAttendance firstAttendance = VoteAttendance.builder()
                .vote(vote)
                .member(firstMember)
                .attendanceStatus(false)
                .build();
        VoteAttendance secondAttendance = VoteAttendance.builder()
                .vote(vote)
                .member(secondMember)
                .attendanceStatus(false)
                .build();

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.findAllByVote_IdAndAttendanceStatusFalseOrderByIdAsc(1L))
                .thenReturn(List.of(firstAttendance, secondAttendance));

        VoteAbsenteeListResponse response = voteService.getVoteAbsentees(1L);

        assertThat(response.totalAbsenteeCount()).isEqualTo(2L);
        assertThat(response.absentees()).hasSize(2);
        assertThat(response.absentees().get(0).memberId()).isEqualTo(2L);
        assertThat(response.absentees().get(0).nickname()).isEqualTo("member-2001");
        assertThat(response.absentees().get(1).memberId()).isEqualTo(3L);
        assertThat(response.absentees().get(1).nickname()).isEqualTo("member-2002");
    }

    @Test
    @DisplayName("게스트 참여 정보를 생성한다")
    void createGuestSavesGuest() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member member = savedMember(2L, "2001");
        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(1L);

        voteService.createGuest(1L, 2L, new VoteGuestRequest("게스트", Gender.FEMALE, "A"));

        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(guestRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("게스트");
        assertThat(captor.getValue().getGender()).isEqualTo(Gender.FEMALE);
        assertThat(captor.getValue().getLevel()).isEqualTo("A");
    }

    @Test
    @DisplayName("게스트 신청은 정원이 꽉 차도 저장된다")
    void createGuestAllowsWaitlistWhenQuotaIsFull() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "capacity", 2);
        ReflectionTestUtils.setField(vote, "activityDate", LocalDate.of(2026, 4, 7));
        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(savedMember(2L, "2001"));

        voteService.createGuest(1L, 2L, new VoteGuestRequest("게스트", Gender.FEMALE, "A"));

        verify(guestRepository).save(any(Guest.class));
    }

    @Test
    @DisplayName("게스트를 추가한 사람은 본인 게스트를 수정할 수 있다")
    void updateGuestUpdatesGuest() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member inviter = savedMember(2L, "2001");
        Guest guest = Guest.builder()
                .vote(vote)
                .member(inviter)
                .name("기존 게스트")
                .gender(Gender.FEMALE)
                .level("A")
                .build();

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(guestRepository.findByIdAndVote_IdIn(eq(10L), anyCollection())).thenReturn(Optional.of(guest));

        voteService.updateGuest(1L, 10L, 2L, new VoteGuestRequest("수정 게스트", Gender.MALE, "B"));

        assertThat(guest.getName()).isEqualTo("수정 게스트");
        assertThat(guest.getGender()).isEqualTo(Gender.MALE);
        assertThat(guest.getLevel()).isEqualTo("B");
    }

    @Test
    @DisplayName("게스트를 추가한 사람만 해당 게스트를 수정할 수 있다")
    void updateGuestThrowsWhenRequesterIsNotInviter() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member inviter = savedMember(2L, "2001");
        Guest guest = Guest.builder()
                .vote(vote)
                .member(inviter)
                .name("기존 게스트")
                .gender(Gender.FEMALE)
                .level("A")
                .build();

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(guestRepository.findByIdAndVote_IdIn(eq(10L), anyCollection())).thenReturn(Optional.of(guest));

        assertThatThrownBy(() -> voteService.updateGuest(1L, 10L, 3L, new VoteGuestRequest("수정 게스트", Gender.MALE, "B")))
                .isInstanceOfSatisfying(YbcException.class,
                        exception -> assertThat(exception.getExceptionCodeName()).isEqualTo("GUEST_UPDATE_FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 게스트는 수정할 수 없다")
    void updateGuestThrowsWhenGuestIsMissing() {
        Vote vote = savedVote(1L, "목요 정기 운동");

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(guestRepository.findByIdAndVote_IdIn(eq(10L), anyCollection())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voteService.updateGuest(1L, 10L, 2L, new VoteGuestRequest("수정 게스트", Gender.MALE, "B")))
                .isInstanceOfSatisfying(YbcException.class,
                        exception -> assertThat(exception.getExceptionCodeName()).isEqualTo("GUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("투표 참여 정보가 없으면 새로 생성한다")
    void saveVoteAttendanceCreatesNewAttendance() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member member = savedMember(2L, "2001");
        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.empty());
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(0L);

        voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(true));

        ArgumentCaptor<VoteAttendance> captor = ArgumentCaptor.forClass(VoteAttendance.class);
        verify(voteAttendanceRepository).save(captor.capture());
        assertThat(captor.getValue().isAttendanceStatus()).isTrue();
        assertThat(captor.getValue().getVote()).isEqualTo(vote);
        assertThat(captor.getValue().getMember()).isEqualTo(member);
    }

    @Test
    @DisplayName("기존 투표 참여 정보가 있으면 참석 여부만 수정한다")
    void saveVoteAttendanceUpdatesExistingAttendance() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member member = savedMember(2L, "2001");
        VoteAttendance attendance = VoteAttendance.builder()
                .vote(vote)
                .member(member)
                .attendanceStatus(true)
                .build();

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.of(attendance));

        voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(false));

        assertThat(attendance.isAttendanceStatus()).isFalse();
        verify(voteAttendanceRepository, never()).save(any(VoteAttendance.class));
    }

    @Test
    @DisplayName("마감된 투표에는 참석 여부를 저장할 수 없다")
    void saveVoteAttendanceThrowsWhenVoteIsClosed() {
        Vote vote = Vote.builder()
                .title("목요 정기 운동")
                .activityType(ActivityType.REGULAR)
                .activityDate(LocalDate.of(2026, 4, 10))
                .activityTime("19:00")
                .location("이화체육관")
                .memo("테스트 모임")
                .voteStartAt(LocalDateTime.now().minusDays(2))
                .voteEndAt(LocalDateTime.now().minusMinutes(1))
                .capacity(20)
                .build();
        ReflectionTestUtils.setField(vote, "id", 1L);

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));

        assertThatThrownBy(() -> voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(true)))
                .isInstanceOf(YbcException.class)
                .hasMessage("마감된 투표에는 참여할 수 없습니다.");
    }

    @Test
    @DisplayName("정원이 가득 차도 새 참석 투표는 저장할 수 있다")
    void saveVoteAttendanceAllowsWaitlistOnCreate() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "capacity", 2);
        Member member = savedMember(2L, "2001");

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.empty());
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(2L);

        voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(true));

        verify(voteAttendanceRepository).save(any(VoteAttendance.class));
    }

    @Test
    @DisplayName("같은 운동의 추가 투표에 이미 참석한 회원은 다시 참석할 수 없다")
    void saveVoteAttendanceThrowsWhenMemberAlreadyJoinedRelatedVote() {
        Vote baseVote = savedVote(1L, "목요 정기 운동");
        Vote additionalVote = Vote.createAdditionalVote(
                baseVote,
                LocalDateTime.now().minusMinutes(30),
                LocalDateTime.now().plusHours(1),
                5
        );
        ReflectionTestUtils.setField(additionalVote, "id", 2L);

        Member member = savedMember(2L, "2001");

        when(voteRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(additionalVote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(2L, 2L)).thenReturn(Optional.empty());
        when(voteRepository.findAllByOriginalVote_IdOrderByIdAsc(1L)).thenReturn(List.of(additionalVote));
        when(voteRepository.findAllByActivityTypeAndActivityDateAndActivityTimeAndLocationAndVoteStartAtAndTitleAndOriginalVoteIsNullOrderByIdAsc(
                ActivityType.REGULAR,
                baseVote.getActivityDate(),
                baseVote.getActivityTime(),
                baseVote.getLocation(),
                baseVote.getVoteEndAt(),
                baseVote.getTitle() + Vote.ADDITIONAL_PARTICIPATION_SUFFIX
        )).thenReturn(List.of());
        when(voteAttendanceRepository.existsByVote_IdInAndMember_IdAndAttendanceStatusTrue(anyCollection(), eq(2L)))
                .thenReturn(true);

        assertThatThrownBy(() -> voteService.saveVoteAttendance(2L, 2L, new VoteAttendanceRequest(true)))
                .isInstanceOf(YbcException.class)
                .hasMessage("같은 운동에는 한 번만 참여할 수 있습니다.");
    }

    @Test
    @DisplayName("정원이 가득 차도 불참을 참석으로 바꾸면 대기열에 포함된다")
    void saveVoteAttendanceAllowsWaitlistOnUpdate() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "capacity", 2);
        Member member = savedMember(2L, "2001");
        VoteAttendance attendance = VoteAttendance.builder()
                .vote(vote)
                .member(member)
                .attendanceStatus(false)
                .build();

        when(voteRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.of(attendance));
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(2L);

        voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(true));

        assertThat(attendance.isAttendanceStatus()).isTrue();
        assertThat(attendance.getAttendingAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 투표를 상세 조회하면 예외가 발생한다")
    void getVoteThrowsWhenVoteIsMissing() {
        when(voteRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voteService.getVote(999L))
                .isInstanceOf(YbcException.class)
                .hasMessage("존재하지 않는 투표입니다.");
    }

    @Test
    @DisplayName("투표 히스토리 조회 시 현재 참여 가능한 투표 수와 올해 생성된 투표 수를 반환한다")
    void getVoteHistoryReturnsJoinableAndThisYearVoteCounts() {
        when(voteRepository.countByVoteEndAtAfter(any(LocalDateTime.class))).thenReturn(2L);
        when(voteRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(18L);

        VoteHistoryResponse response = voteService.getVoteHistory();

        assertThat(response.joinableVotes()).isEqualTo(2L);
        assertThat(response.thisYearVotes()).isEqualTo(18L);
    }

    @Test
    @DisplayName("투표 히스토리 조회 시 올해 생성 건수 집계를 위해 올해 시작 시각과 다음 해 시작 시각을 사용한다")
    void getVoteHistoryUsesThisYearRange() {
        when(voteRepository.countByVoteEndAtAfter(any(LocalDateTime.class))).thenReturn(0L);
        when(voteRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(0L);

        voteService.getVoteHistory();

        ArgumentCaptor<LocalDateTime> voteEndAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(voteRepository).countByVoteEndAtAfter(voteEndAtCaptor.capture());
        verify(voteRepository).countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(startCaptor.capture(), endCaptor.capture());

        LocalDateTime now = voteEndAtCaptor.getValue();
        LocalDateTime startOfYear = startCaptor.getValue();
        LocalDateTime startOfNextYear = endCaptor.getValue();

        assertThat(startOfYear).isEqualTo(LocalDateTime.of(now.getYear(), 1, 1, 0, 0));
        assertThat(startOfNextYear).isEqualTo(LocalDateTime.of(now.getYear() + 1, 1, 1, 0, 0));
    }

    private Vote savedVote(Long id, String title) {
        LocalDateTime now = LocalDateTime.now();
        Vote vote = Vote.builder()
                .title(title)
                .activityType(ActivityType.REGULAR)
                .activityDate(LocalDate.of(2026, 4, 10))
                .activityTime("19:00")
                .location("이화체육관")
                .memo("테스트 모임")
                .voteStartAt(now.minusHours(1))
                .voteEndAt(now.plusDays(1))
                .capacity(20)
                .build();
        ReflectionTestUtils.setField(vote, "id", id);
        return vote;
    }

    private Member savedMember(Long id, String socialId) {
        Member member = Member.createSocialMember(SocialProvider.KAKAO, socialId, "member-" + socialId, socialId + "@example.com");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
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
}
