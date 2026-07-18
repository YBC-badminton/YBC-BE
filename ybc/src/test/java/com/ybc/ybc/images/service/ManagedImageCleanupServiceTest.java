package com.ybc.ybc.images.service;

import com.ybc.ybc.contents.domain.ActivitiesContent;
import com.ybc.ybc.contents.domain.ActivitiesEvent;
import com.ybc.ybc.contents.domain.HomeContent;
import com.ybc.ybc.contents.repository.ActivitiesContentRepository;
import com.ybc.ybc.contents.repository.HomeContentRepository;
import com.ybc.ybc.images.domain.AdminImage;
import com.ybc.ybc.images.repository.AdminImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagedImageCleanupServiceTest {
    @Mock
    private AdminImageRepository adminImageRepository;

    @Mock
    private HomeContentRepository homeContentRepository;

    @Mock
    private ActivitiesContentRepository activitiesContentRepository;

    @Mock
    private ImageStorageClient imageStorageClient;

    @InjectMocks
    private ManagedImageCleanupService managedImageCleanupService;

    @Test
    @DisplayName("더 이상 어떤 콘텐츠에서도 참조하지 않는 관리 이미지면 삭제한다")
    void deleteUnusedImagesDeletesManagedImageWhenItIsNoLongerReferenced() {
        String oldImageUrl = "https://storage.googleapis.com/test-bucket/images/old-image.jpg";
        String newImageUrl = "https://storage.googleapis.com/test-bucket/images/new-image.jpg";
        AdminImage adminImage = AdminImage.create("images/old-image.jpg", oldImageUrl);

        when(homeContentRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(
                HomeContent.create("소개글", newImageUrl, 2, 45)
        ));
        when(activitiesContentRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(adminImageRepository.findByImageUrl(oldImageUrl)).thenReturn(Optional.of(adminImage));

        managedImageCleanupService.deleteUnusedImages(Set.of(oldImageUrl), Set.of(newImageUrl));

        verify(imageStorageClient).delete("images/old-image.jpg");
        verify(adminImageRepository).delete(adminImage);
    }

    @Test
    @DisplayName("다른 콘텐츠에서 아직 참조 중인 이미지는 삭제하지 않는다")
    void deleteUnusedImagesSkipsImageWhenItIsStillReferenced() {
        String imageUrl = "https://storage.googleapis.com/test-bucket/images/shared-image.jpg";
        ActivitiesContent activitiesContent = ActivitiesContent.create(
                "지난 활동",
                "설명",
                "정기모임 설명",
                List.of(imageUrl, "https://storage.googleapis.com/test-bucket/images/regular2.jpg"),
                List.of(
                        ActivitiesEvent.create(
                                "리그전",
                                "설명",
                                "https://storage.googleapis.com/test-bucket/images/event1.jpg"
                        ),
                        ActivitiesEvent.create(
                                "MT",
                                "설명",
                                "https://storage.googleapis.com/test-bucket/images/event2.jpg"
                        )
                ),
                List.of("뒤풀이1", "뒤풀이2", "뒤풀이3"),
                "https://storage.googleapis.com/test-bucket/images/after-party.jpg"
        );

        when(homeContentRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(activitiesContentRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(activitiesContent));

        managedImageCleanupService.deleteUnusedImages(
                Set.of(imageUrl),
                Set.of("https://storage.googleapis.com/test-bucket/images/new-image.jpg")
        );

        verify(adminImageRepository, never()).findByImageUrl(imageUrl);
        verify(imageStorageClient, never()).delete("images/shared-image.jpg");
    }

    @Test
    @DisplayName("변경되지 않은 이미지 URL은 삭제 후보로 보지 않는다")
    void deleteUnusedImagesSkipsUnchangedImageUrl() {
        String imageUrl = "https://storage.googleapis.com/test-bucket/images/same-image.jpg";

        managedImageCleanupService.deleteUnusedImages(Set.of(imageUrl), Set.of(imageUrl));

        verify(adminImageRepository, never()).findByImageUrl(imageUrl);
        verify(imageStorageClient, never()).delete("images/same-image.jpg");
    }
}
