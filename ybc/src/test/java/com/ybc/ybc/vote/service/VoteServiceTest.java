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
import com.ybc.ybc.vote.dto.response.VoteAttendanceStatusResponse;
import com.ybc.ybc.vote.dto.response.VoteDetailResponse;
import com.ybc.ybc.vote.dto.response.VoteHistoryResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("투표 상세 조회 시 투표 정보와 현재 참석 인원 수를 반환한다")
    void getVoteReturnsDetailResponse() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(2L);
        when(guestRepository.countByVote_Id(1L)).thenReturn(1L);

        VoteDetailResponse response = voteService.getVote(1L);

        assertThat(response.voteId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("목요 정기 운동");
        assertThat(response.type()).isEqualTo(ActivityType.REGULAR);
        assertThat(response.activityDate()).isEqualTo(LocalDate.of(2026, 4, 10));
        assertThat(response.location()).isEqualTo("이화체육관");
        assertThat(response.memo()).isEqualTo("테스트 모임");
        assertThat(response.currentParticipantCount()).isEqualTo(3L);
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
    @DisplayName("게스트 참여 정보를 생성한다")
    void createGuestSavesGuest() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member member = savedMember(2L, "2001");
        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(1L);
        when(guestRepository.countByVote_Id(1L)).thenReturn(0L);

        voteService.createGuest(1L, 2L, new VoteGuestRequest("게스트", Gender.FEMALE, "A"));

        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(guestRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("게스트");
        assertThat(captor.getValue().getGender()).isEqualTo(Gender.FEMALE);
        assertThat(captor.getValue().getLevel()).isEqualTo("A");
    }

    @Test
    @DisplayName("게스트 참여도 정원 계산에 포함된다")
    void createGuestThrowsWhenSessionIsFull() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "capacity", 2);
        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(1L);
        when(guestRepository.countByVote_Id(1L)).thenReturn(1L);

        assertThatThrownBy(() -> voteService.createGuest(1L, 2L, new VoteGuestRequest("게스트", Gender.FEMALE, "A")))
                .isInstanceOf(YbcException.class)
                .hasMessage("해당 모임의 정원이 모두 찼습니다.");
    }

    @Test
    @DisplayName("투표 참여 정보가 없으면 새로 생성한다")
    void saveVoteAttendanceCreatesNewAttendance() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        Member member = savedMember(2L, "2001");
        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.empty());
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(0L);
        when(guestRepository.countByVote_Id(1L)).thenReturn(0L);

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

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
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

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));

        assertThatThrownBy(() -> voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(true)))
                .isInstanceOf(YbcException.class)
                .hasMessage("마감된 투표에는 참여할 수 없습니다.");
    }

    @Test
    @DisplayName("정원이 가득 찬 투표에 새로 참석하면 예외가 발생한다")
    void saveVoteAttendanceThrowsWhenSessionIsFullOnCreate() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "capacity", 2);
        Member member = savedMember(2L, "2001");

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.empty());
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(1L);
        when(guestRepository.countByVote_Id(1L)).thenReturn(1L);

        assertThatThrownBy(() -> voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(true)))
                .isInstanceOf(YbcException.class)
                .hasMessage("해당 모임의 정원이 모두 찼습니다.");
    }

    @Test
    @DisplayName("정원이 가득 찬 투표에서 불참을 참석으로 바꾸면 예외가 발생한다")
    void saveVoteAttendanceThrowsWhenSessionIsFullOnUpdate() {
        Vote vote = savedVote(1L, "목요 정기 운동");
        ReflectionTestUtils.setField(vote, "capacity", 2);
        Member member = savedMember(2L, "2001");
        VoteAttendance attendance = VoteAttendance.builder()
                .vote(vote)
                .member(member)
                .attendanceStatus(false)
                .build();

        when(voteRepository.findById(1L)).thenReturn(Optional.of(vote));
        when(memberService.getMemberById(2L)).thenReturn(member);
        when(voteAttendanceRepository.findByVote_IdAndMember_Id(1L, 2L)).thenReturn(Optional.of(attendance));
        when(voteAttendanceRepository.countAttendancesByVoteId(1L)).thenReturn(1L);
        when(guestRepository.countByVote_Id(1L)).thenReturn(1L);

        assertThatThrownBy(() -> voteService.saveVoteAttendance(1L, 2L, new VoteAttendanceRequest(true)))
                .isInstanceOf(YbcException.class)
                .hasMessage("해당 모임의 정원이 모두 찼습니다.");
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
        Vote vote = Vote.builder()
                .title(title)
                .activityType(ActivityType.REGULAR)
                .activityDate(LocalDate.of(2026, 4, 10))
                .activityTime("19:00")
                .location("이화체육관")
                .memo("테스트 모임")
                .voteStartAt(LocalDateTime.of(2026, 4, 9, 10, 0))
                .voteEndAt(LocalDateTime.of(2026, 4, 10, 22, 0))
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
}
