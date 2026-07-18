package com.ybc.ybc.images.service;

import com.ybc.ybc.contents.domain.ActivitiesContent;
import com.ybc.ybc.contents.repository.ActivitiesContentRepository;
import com.ybc.ybc.contents.repository.HomeContentRepository;
import com.ybc.ybc.images.domain.AdminImage;
import com.ybc.ybc.images.repository.AdminImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManagedImageCleanupService {
    private final AdminImageRepository adminImageRepository;
    private final HomeContentRepository homeContentRepository;
    private final ActivitiesContentRepository activitiesContentRepository;
    private final ImageStorageClient imageStorageClient;

    @Transactional
    public void deleteUnusedImages(Set<String> previousImageUrls, Set<String> currentImageUrls) {
        previousImageUrls.stream()
                .filter(StringUtils::hasText)
                .filter(imageUrl -> !currentImageUrls.contains(imageUrl))
                .filter(this::isNotReferenced)
                .forEach(this::deleteManagedImageByUrl);
    }

    private boolean isNotReferenced(String imageUrl) {
        return !isReferencedByHomeContent(imageUrl) && !isReferencedByActivitiesContent(imageUrl);
    }

    private boolean isReferencedByHomeContent(String imageUrl) {
        return homeContentRepository.findFirstByOrderByIdAsc()
                .map(homeContent -> imageUrl.equals(homeContent.getActivityImageUrl()))
                .orElse(false);
    }

    private boolean isReferencedByActivitiesContent(String imageUrl) {
        return activitiesContentRepository.findFirstByOrderByIdAsc()
                .map(activitiesContent -> containsImageUrl(activitiesContent, imageUrl))
                .orElse(false);
    }

    private boolean containsImageUrl(ActivitiesContent activitiesContent, String imageUrl) {
        return imageUrl.equals(activitiesContent.getAfterPartyImageUrl())
                || activitiesContent.getRegularMeetingImageUrls().contains(imageUrl)
                || activitiesContent.getEvents().stream()
                .anyMatch(event -> imageUrl.equals(event.getImageUrl()));
    }

    private void deleteManagedImageByUrl(String imageUrl) {
        adminImageRepository.findByImageUrl(imageUrl)
                .ifPresent(this::deleteManagedImage);
    }

    private void deleteManagedImage(AdminImage adminImage) {
        imageStorageClient.delete(adminImage.getObjectName());
        adminImageRepository.delete(adminImage);
    }
}
