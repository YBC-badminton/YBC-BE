package com.ybc.ybc.images.controller;

import com.ybc.ybc.images.dto.response.ImageUploadResponse;
import com.ybc.ybc.images.service.AdminImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/images")
public class AdminImageController {
    private final AdminImageService adminImageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> uploadImage(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "existingImageUrl", required = false) String existingImageUrl
    ) {
        return ResponseEntity.ok(adminImageService.uploadImage(file, existingImageUrl));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long imageId) {
        adminImageService.deleteImage(imageId);
        return ResponseEntity.ok().build();
    }
}
