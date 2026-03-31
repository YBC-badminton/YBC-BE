package com.ybc.ybc.auth.repository;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findBySocialProviderAndSocialId(SocialProvider socialProvider, String socialId);
}
