package com.ybc.ybc.interviews.repository;

import com.ybc.ybc.interviews.domain.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    Optional<Interview> findByApplication_Id(Long applicationId);

    List<Interview> findAllByApplication_IdIn(Collection<Long> applicationIds);
}
