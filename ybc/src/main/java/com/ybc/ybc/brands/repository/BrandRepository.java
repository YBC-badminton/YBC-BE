package com.ybc.ybc.brands.repository;

import com.ybc.ybc.brands.domain.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByName(String name);

    List<Brand> findByNameContainingIgnoreCaseOrderByNameAsc(String keyword);
}
