package com.ybc.ybc.applications.repository;

import com.ybc.ybc.applications.domain.Application;
import com.ybc.ybc.applications.domain.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findAllByStatusIn(Collection<Status> statuses, Sort sort);

    List<Application> findAllByTerm(String term, Sort sort);

    @Query("""
            select currentApplication.id
            from Application currentApplication
            where currentApplication.id in :applicationIds
              and exists (
                  select previousApplication.id
                  from Application previousApplication
                  where previousApplication.name = currentApplication.name
                    and previousApplication.normalizedPhone = currentApplication.normalizedPhone
                    and (previousApplication.term is null or previousApplication.term <> currentApplication.term)
                    and previousApplication.id < currentApplication.id
              )
            """)
    List<Long> findReapplicantIdsByApplicationIds(@Param("applicationIds") Collection<Long> applicationIds);

    @Query("""
            select count(previousApplication) > 0
            from Application previousApplication
            where previousApplication.name = :name
              and previousApplication.normalizedPhone = :normalizedPhone
              and (previousApplication.term is null or previousApplication.term <> :term)
              and previousApplication.id < :id
            """)
    boolean existsReapplicantHistory(@Param("name") String name,
                                     @Param("normalizedPhone") String normalizedPhone,
                                     @Param("id") Long id,
                                     @Param("term") String term);

    @Modifying
    @Query(value = """
            update application
            set normalized_phone = replace(phone, '-', '')
            where normalized_phone is null or normalized_phone = ''
            """, nativeQuery = true)
    int backfillNormalizedPhone();
}
