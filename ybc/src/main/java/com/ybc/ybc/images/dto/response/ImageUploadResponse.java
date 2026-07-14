package com.ybc.ybc.images.dto.response;

import com.ybc.ybc.images.domain.AdminImage;

public record ImageUploadResponse(
        String imageUrl
) {
    public static ImageUploadResponse from(AdminImage adminImage) {
        return new ImageUploadResponse(adminImage.getImageUrl());
    }
}
