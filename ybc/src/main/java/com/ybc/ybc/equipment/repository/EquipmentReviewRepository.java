package com.ybc.ybc.equipment.repository;

import com.ybc.ybc.equipment.domain.EquipmentReview;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentReviewRepository extends JpaRepository<EquipmentReview, Long> {
    @EntityGraph(attributePaths = {"member", "equipment", "equipment.brand"})
    Optional<EquipmentReview> findWithDetailsById(Long id);
}
