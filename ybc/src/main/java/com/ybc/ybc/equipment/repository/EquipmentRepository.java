package com.ybc.ybc.equipment.repository;

import com.ybc.ybc.equipment.domain.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
}
