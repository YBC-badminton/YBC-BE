package com.ybc.ybc.faqs.repository;

import com.ybc.ybc.faqs.domain.Faq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    List<Faq> findAllByOrderByIdAsc();
}
