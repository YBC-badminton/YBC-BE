package com.ybc.ybc.members.repository;

import com.ybc.ybc.members.domain.ClubMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClubMemberRepository extends JpaRepository<ClubMember, Long> {
    Optional<ClubMember> findFirstByNameOrderByIdAsc(String name);
}
