package com.ybc.ybc.equipment.repository;

import com.ybc.ybc.equipment.domain.Equipment;
import com.ybc.ybc.equipment.domain.EquipmentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    Optional<Equipment> findByNameAndCategoryAndBrand_Name(String name, EquipmentCategory category, String brandName);
}
