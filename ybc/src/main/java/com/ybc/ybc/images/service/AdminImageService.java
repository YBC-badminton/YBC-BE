package com.ybc.ybc.images.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.global.exception.ExceptionCode;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.images.config.GcsImageStorageProperties;
import com.ybc.ybc.images.domain.AdminImage;
import com.ybc.ybc.images.dto.response.ImageUploadResponse;
import com.ybc.ybc.images.repository.AdminImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminImageService {
    private static final long MAX_IMAGE_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final String IMAGE_CACHE_CONTROL = "public, max-age=2592000";
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif"
    );

    private final MemberService memberService;
    private final AdminImageRepository adminImageRepository;
    private final ImageStorageClient imageStorageClient;
    private final GcsImageStorageProperties properties;

    @Transactional
    public ImageUploadResponse uploadImage(Long memberId, MultipartFile file, String existingImageUrl) {
        validateAdmin(memberId);
        validateFile(file);

        String extension = resolveExtension(file);
        String objectName = properties.buildObjectName(UUID.randomUUID() + "." + extension);
        String imageUrl = imageStorageClient.upload(
                objectName,
                readBytes(file),
                CONTENT_TYPE_BY_EXTENSION.get(extension),
                IMAGE_CACHE_CONTROL
        );

        try {
            AdminImage uploadedImage = adminImageRepository.save(AdminImage.create(objectName, imageUrl));
            deleteExistingImage(existingImageUrl);
            trimStoredImages();
            return ImageUploadResponse.from(uploadedImage);
        } catch (RuntimeException e) {
            deleteUploadedImageQuietly(objectName);
            throw e;
        }
    }

    @Transactional
    public void deleteImage(Long memberId, Long imageId) {
        validateAdmin(memberId);
        AdminImage adminImage = getImageById(imageId);
        imageStorageClient.delete(adminImage.getObjectName());
        adminImageRepository.delete(adminImage);
    }

    private void validateAdmin(Long memberId) {
        Member member = memberService.getMemberById(memberId);
        if (!member.isAdmin()) {
            throw new YbcException(ExceptionCode.UNAUTHORIZED_USER);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new YbcException(ExceptionCode.IMAGE_FILE_REQUIRED);
        }

        if (file.getSize() > MAX_IMAGE_FILE_SIZE_BYTES) {
            throw new YbcException(ExceptionCode.IMAGE_FILE_TOO_LARGE);
        }
    }

    private String resolveExtension(MultipartFile file) {
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)) {
            String supportedExtension = EXTENSION_BY_CONTENT_TYPE.get(contentType.toLowerCase(Locale.ROOT));
            if (supportedExtension != null) {
                return supportedExtension;
            }
        }

        String filename = file.getOriginalFilename();
        String extension = filename == null ? null : StringUtils.getFilenameExtension(filename);
        if (!StringUtils.hasText(extension)) {
            throw new YbcException(ExceptionCode.UNSUPPORTED_IMAGE_FILE_TYPE);
        }

        String normalizedExtension = extension.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(normalizedExtension)) {
            throw new YbcException(ExceptionCode.UNSUPPORTED_IMAGE_FILE_TYPE);
        }

        return "jpeg".equals(normalizedExtension) ? "jpg" : normalizedExtension;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read image file.", e);
        }
    }

    private void deleteExistingImage(String existingImageUrl) {
        extractManagedObjectName(existingImageUrl).ifPresent(objectName -> {
            imageStorageClient.delete(objectName);
            adminImageRepository.findByObjectName(objectName)
                    .ifPresent(adminImageRepository::delete);
        });
    }

    private AdminImage getImageById(Long imageId) {
        return adminImageRepository.findById(imageId)
                .orElseThrow(() -> new YbcException(ExceptionCode.IMAGE_NOT_FOUND));
    }

    private Optional<String> extractManagedObjectName(String imageUrl) {
        if (!StringUtils.hasText(imageUrl) || !properties.hasBucketConfigured()) {
            return Optional.empty();
        }

        try {
            URI uri = URI.create(imageUrl);
            if (!"storage.googleapis.com".equalsIgnoreCase(uri.getHost())) {
                return Optional.empty();
            }

            String path = uri.getPath();
            String bucketPrefix = "/" + properties.bucket() + "/";
            if (!StringUtils.hasText(path) || !path.startsWith(bucketPrefix)) {
                return Optional.empty();
            }

            String objectName = path.substring(bucketPrefix.length());
            return properties.managesObject(objectName) ? Optional.of(objectName) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void trimStoredImages() {
        List<AdminImage> storedImages = adminImageRepository.findAllByOrderByCreatedAtAsc();
        int deleteCount = storedImages.size() - properties.maxImageCount();
        if (deleteCount <= 0) {
            return;
        }

        storedImages.stream()
                .limit(deleteCount)
                .forEach(this::deleteStoredImage);
    }

    private void deleteStoredImage(AdminImage storedImage) {
        imageStorageClient.delete(storedImage.getObjectName());
        adminImageRepository.delete(storedImage);
    }

    private void deleteUploadedImageQuietly(String objectName) {
        try {
            imageStorageClient.delete(objectName);
        } catch (RuntimeException e) {
            log.warn("Failed to roll back uploaded image. objectName={}", objectName, e);
        }
    }
}
