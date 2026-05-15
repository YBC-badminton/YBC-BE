package com.ybc.ybc.members.repository;

import com.ybc.ybc.members.domain.ClubMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClubMemberRepository extends JpaRepository<ClubMember, Long> {
    Optional<ClubMember> findFirstByNameOrderByIdAsc(String name);

    Optional<ClubMember> findByApplicationId(Long applicationId);

    List<ClubMember> findAllByApplicationIdIsNotNull();

    List<ClubMember> findAllByMemberIdInOrderByIdAsc(Collection<Long> memberIds);

    @Query("""
            select cm
            from ClubMember cm
            where cm.email is not null
              and lower(trim(cm.email)) in :emails
            order by cm.id
            """)
    List<ClubMember> findAllByNormalizedEmailInOrderByIdAsc(@Param("emails") Collection<String> emails);
}
