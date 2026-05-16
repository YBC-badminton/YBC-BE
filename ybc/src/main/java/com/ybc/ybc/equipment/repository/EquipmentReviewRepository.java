package com.ybc.ybc.equipment.repository;

import com.ybc.ybc.equipment.domain.EquipmentReview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface EquipmentReviewRepository extends JpaRepository<EquipmentReview, Long> {
    @EntityGraph(attributePaths = {"member", "equipment", "equipment.brand"})
    Optional<EquipmentReview> findWithDetailsById(Long id);

    @Override
    @EntityGraph(attributePaths = {"member", "equipment", "equipment.brand"})
    Page<EquipmentReview> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"member", "equipment", "equipment.brand"})
    Page<EquipmentReview> findByEquipment_Category(com.ybc.ybc.equipment.domain.EquipmentCategory category, Pageable pageable);
}
