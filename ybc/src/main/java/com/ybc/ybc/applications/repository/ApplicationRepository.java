package com.ybc.ybc.applications.repository;

import com.ybc.ybc.applications.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
}
