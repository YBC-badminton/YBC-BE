package com.ybc.ybc.vote.repository;

import com.ybc.ybc.vote.domain.Vote;
import com.ybc.ybc.vote.repository.projection.VoteSummaryProjection;
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
    long countByVoteStartAtLessThanEqualAndVoteEndAtAfter(LocalDateTime voteStartAt, LocalDateTime voteEndAt);

    @Query("""
            select count(v)
            from Vote v
            where v.voteEndAt <= :now
              and v.originalVote is null
              and v.title not like concat('%', :additionalParticipationSuffix)
            """)
    long countCompletedVotesExcludingAdditional(@Param("now") LocalDateTime now,
                                                @Param("additionalParticipationSuffix") String additionalParticipationSuffix);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vote v where v.id = :voteId")
    java.util.Optional<Vote> findByIdForUpdate(@Param("voteId") Long voteId);

    /**
     * 투표 시작 30분 전 이후이거나 이미 시작된 항목 중 아직 마감되지 않은 항목만 조회하고 참석 회원 수를 집계한다.
     */
    @Query(
            value = """
                    select new com.ybc.ybc.vote.repository.projection.VoteSummaryProjection(
                        v.id,
                        v.title,
                        v.activityType,
                        v.activityDate,
                        v.location,
                        v.voteStartAt,
                        v.voteEndAt,
                        v.activityTime,
                        v.capacity,
                        coalesce(sum(case when va.attendanceStatus = true then 1 else 0 end), 0),
                        case when (
                            select count(mg)
                            from VoteMatchGroup mg
                            where mg.vote.id = case when ov is not null then ov.id else v.id end
                        ) > 0 then true else false end
                    )
                    from Vote v
                    left join v.originalVote ov
                    left join VoteAttendance va on va.vote = v
                    where v.voteEndAt > :now
                      and v.voteStartAt <= :openAt
                    group by v.id, v.title, v.activityType, v.activityDate, v.location, v.voteStartAt, v.voteEndAt, v.activityTime, v.capacity, v.createdAt, ov.id
                    order by v.activityDate asc, v.createdAt asc, v.id asc
                    """,
            countQuery = """
                    select count(v)
                    from Vote v
                    where v.voteEndAt > :now
                      and v.voteStartAt <= :openAt
                    """
    )
    Page<VoteSummaryProjection> findOpenVoteSummaries(
            @Param("now") LocalDateTime now,
            @Param("openAt") LocalDateTime openAt,
            Pageable pageable
    );

    /**
     * 투표 마감 시간이 현재와 같거나 지난 항목만 조회하고 참석 회원 수를 집계한다.
     */
    @Query(
            value = """
                    select new com.ybc.ybc.vote.repository.projection.VoteSummaryProjection(
                        v.id,
                        v.title,
                        v.activityType,
                        v.activityDate,
                        v.location,
                        v.voteStartAt,
                        v.voteEndAt,
                        v.activityTime,
                        v.capacity,
                        coalesce(sum(case when va.attendanceStatus = true then 1 else 0 end), 0),
                        case when (
                            select count(mg)
                            from VoteMatchGroup mg
                            where mg.vote.id = case when ov is not null then ov.id else v.id end
                        ) > 0 then true else false end
                    )
                    from Vote v
                    left join v.originalVote ov
                    left join VoteAttendance va on va.vote = v
                    where v.voteEndAt <= :now
                    group by v.id, v.title, v.activityType, v.activityDate, v.location, v.voteStartAt, v.voteEndAt, v.activityTime, v.capacity, v.createdAt, ov.id
                    order by v.activityDate desc, v.createdAt desc, v.id desc
                    """,
            countQuery = """
                    select count(v)
                    from Vote v
                    where v.voteEndAt <= :now
                    """
    )
    Page<VoteSummaryProjection> findClosedVoteSummaries(
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            select v
            from Vote v
            where v.voteStartAt <= :now
              and (v.voteEndAt > :now or v.activityDate >= :today)
            order by case when v.activityDate < :today then 1 else 0 end asc,
                     case when v.activityDate >= :today then v.activityDate end asc,
                     case when v.activityDate < :today then v.activityDate end desc,
                     case
                         when v.activityType = com.ybc.ybc.vote.domain.ActivityType.REGULAR then 0
                         when v.activityType = com.ybc.ybc.vote.domain.ActivityType.EVENT then 1
                         when v.activityType = com.ybc.ybc.vote.domain.ActivityType.FLASH then 2
                         else 3
                     end asc,
                     v.createdAt desc,
                     v.id desc
            """)
    List<Vote> findRecentVoteCandidates(@Param("now") LocalDateTime now,
                                        @Param("today") LocalDate today);

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
