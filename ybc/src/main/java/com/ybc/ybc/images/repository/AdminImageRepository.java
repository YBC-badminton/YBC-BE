package com.ybc.ybc.images.repository;

import com.ybc.ybc.images.domain.AdminImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminImageRepository extends JpaRepository<AdminImage, Long> {
    Optional<AdminImage> findByObjectName(String objectName);

    Optional<AdminImage> findByImageUrl(String imageUrl);

    List<AdminImage> findAllByOrderByCreatedAtAsc();
}
