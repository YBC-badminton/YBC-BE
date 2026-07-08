package com.ybc.ybc.auth.repository;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findBySocialProviderAndSocialId(SocialProvider socialProvider, String socialId);

    @Query("""
            select m
            from Member m
            where m.email is not null
              and lower(trim(m.email)) in :emails
            order by m.id
            """)
    List<Member> findAllByNormalizedEmailInOrderByIdAsc(@Param("emails") Collection<String> emails);
}
