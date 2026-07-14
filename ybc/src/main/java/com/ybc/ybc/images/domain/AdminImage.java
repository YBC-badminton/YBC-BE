package com.ybc.ybc.images.domain;

import com.ybc.ybc.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "admin_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminImage extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_name", nullable = false, unique = true, length = 255)
    private String objectName;

    @Column(name = "image_url", nullable = false, unique = true, length = 500)
    private String imageUrl;

    private AdminImage(String objectName, String imageUrl) {
        this.objectName = objectName;
        this.imageUrl = imageUrl;
    }

    public static AdminImage create(String objectName, String imageUrl) {
        return new AdminImage(objectName, imageUrl);
    }
}
