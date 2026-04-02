package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.AttendanceStatus;
import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.dto.VoteSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    /**
     * 투표 마감 시간이 현재보다 뒤인 항목만 조회하고 참석 상태가 ATTEND인 인원 수를 함께 집계한다.
     */
    @Query(
            value = """
                    select new com.ybc.ybc.vote.dto.VoteSummaryResponse(
                        v.id,
                        v.title,
                        v.activityType,
                        v.location,
                        v.voteStartAt,
                        v.voteEndAt,
                        v.activityTime,
                        v.capacity,
                        coalesce(sum(case when va.attendanceStatus = :attendStatus then 1 else 0 end), 0)
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
            @Param("attendStatus") AttendanceStatus attendStatus,
            Pageable pageable
    );

    /**
     * 투표 마감 시간이 현재와 같거나 지난 항목만 조회하고 참석 상태가 ATTEND인 인원 수를 함께 집계한다.
     */
    @Query(
            value = """
                    select new com.ybc.ybc.vote.dto.VoteSummaryResponse(
                        v.id,
                        v.title,
                        v.activityType,
                        v.location,
                        v.voteStartAt,
                        v.voteEndAt,
                        v.activityTime,
                        v.capacity,
                        coalesce(sum(case when va.attendanceStatus = :attendStatus then 1 else 0 end), 0)
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
            @Param("attendStatus") AttendanceStatus attendStatus,
            Pageable pageable
    );
}
