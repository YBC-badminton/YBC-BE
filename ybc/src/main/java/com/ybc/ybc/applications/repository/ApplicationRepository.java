package com.ybc.ybc.applications.repository;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findAllByStatusIn(Collection<Status> statuses, Sort sort);
}
