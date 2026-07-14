package com.ybc.ybc.contents.repository;

import com.ybc.ybc.contents.domain.PrivacyPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrivacyPolicyRepository extends JpaRepository<PrivacyPolicy, Long> {
    Optional<PrivacyPolicy> findFirstByOrderByIdAsc();
}
