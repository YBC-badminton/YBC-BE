package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.dto.response.VoteSummaryResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    long countByVoteEndAtAfter(LocalDateTime now);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vote v where v.id = :voteId")
    java.util.Optional<Vote> findByIdForUpdate(@Param("voteId") Long voteId);

    /**
     * 투표 마감 시간이 현재보다 뒤인 항목만 조회하고 참석 회원 수와 게스트 수를 함께 집계한다.
     */
    @Query(
            value = """
                    select new com.ybc.ybc.vote.dto.response.VoteSummaryResponse(
                        v.id,
                        v.title,
                        v.activityType,
                        v.location,
                        v.voteStartAt,
                        v.voteEndAt,
                        v.activityTime,
                        v.capacity,
                        coalesce(sum(case when va.attendanceStatus = true then 1 else 0 end), 0)
                        + (select count(g) from Guest g where g.vote = v)
                    )
                    from Vote v
                    left join VoteAttendance va on va.vote = v
                    where v.voteEndAt > :now
                    group by v.id, v.title, v.activityType, v.location, v.voteStartAt, v.voteEndAt, v.activityTime, v.capacity
                    """,
            countQuery = """
                    select count(v)
                    from Vote v
                    where v.voteEndAt > :now
                    """
    )
    Page<VoteSummaryResponse> findJoinableVoteSummaries(
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    /**
     * 투표 마감 시간이 현재와 같거나 지난 항목만 조회하고 참석 회원 수와 게스트 수를 함께 집계한다.
     */
    @Query(
            value = """
                    select new com.ybc.ybc.vote.dto.response.VoteSummaryResponse(
                        v.id,
                        v.title,
                        v.activityType,
                        v.location,
                        v.voteStartAt,
                        v.voteEndAt,
                        v.activityTime,
                        v.capacity,
                        coalesce(sum(case when va.attendanceStatus = true then 1 else 0 end), 0)
                        + (select count(g) from Guest g where g.vote = v)
                    )
                    from Vote v
                    left join VoteAttendance va on va.vote = v
                    where v.voteEndAt <= :now
                    group by v.id, v.title, v.activityType, v.location, v.voteStartAt, v.voteEndAt, v.activityTime, v.capacity
                    """,
            countQuery = """
                    select count(v)
                    from Vote v
                    where v.voteEndAt <= :now
                    """
    )
    Page<VoteSummaryResponse> findClosedVoteSummaries(
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    List<Vote> findAllByVoteEndAtLessThanEqualOrderByVoteEndAtAscIdAsc(LocalDateTime now);

    List<Vote> findAllByOriginalVote_IdOrderByIdAsc(Long originalVoteId);

    List<Vote> findAllByActivityTypeAndActivityDateAndActivityTimeAndLocationAndVoteStartAtAndTitleAndOriginalVoteIsNullOrderByIdAsc(
            com.ybc.ybc.vote.domain.ActivityType activityType,
            LocalDate activityDate,
            String activityTime,
            String location,
            LocalDateTime voteStartAt,
            String title
    );

    java.util.Optional<Vote> findFirstByTitleAndActivityTypeAndActivityDateAndActivityTimeAndLocationAndVoteEndAtAndOriginalVoteIsNullOrderByIdAsc(
            String title,
            com.ybc.ybc.vote.domain.ActivityType activityType,
            LocalDate activityDate,
            String activityTime,
            String location,
            LocalDateTime voteEndAt
    );

    boolean existsByOriginalVote_Id(Long originalVoteId);

    boolean existsByTitleAndActivityTypeAndActivityDateAndVoteStartAtAndVoteEndAt(
            String title,
            com.ybc.ybc.vote.domain.ActivityType activityType,
            LocalDate activityDate,
            LocalDateTime voteStartAt,
            LocalDateTime voteEndAt
    );
}
