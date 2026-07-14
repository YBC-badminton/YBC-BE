package com.ybc.ybc.images.service;

import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.service.MemberService;
import com.ybc.ybc.global.exception.YbcException;
import com.ybc.ybc.images.config.GcsImageStorageProperties;
import com.ybc.ybc.images.domain.AdminImage;
import com.ybc.ybc.images.dto.response.ImageUploadResponse;
import com.ybc.ybc.images.repository.AdminImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminImageServiceTest {
    private static final Long ADMIN_MEMBER_ID = 1L;
    private static final Long NON_ADMIN_MEMBER_ID = 2L;

    @Mock
    private MemberService memberService;

    @Mock
    private AdminImageRepository adminImageRepository;

    @Mock
    private ImageStorageClient imageStorageClient;

    private AdminImageService adminImageService;

    @BeforeEach
    void setUp() {
        GcsImageStorageProperties properties = new GcsImageStorageProperties(
                "test-bucket",
                "test-project",
                "",
                "",
                "images",
                6
        );
        adminImageService = new AdminImageService(memberService, adminImageRepository, imageStorageClient, properties);

        lenient().when(memberService.getMemberById(ADMIN_MEMBER_ID)).thenReturn(adminMember());
        lenient().when(memberService.getMemberById(NON_ADMIN_MEMBER_ID)).thenReturn(nonAdminMember());
    }

    @Test
    @DisplayName("유효한 이미지 파일을 업로드하면 GCS URL을 응답으로 반환한다")
    void uploadImageReturnsUploadedImageUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "main.jpg", "image/jpeg", "image-data".getBytes());

        when(imageStorageClient.upload(any(), any(), any()))
                .thenReturn("https://storage.googleapis.com/test-bucket/images/uploaded.jpg");
        when(adminImageRepository.save(any(AdminImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(adminImageRepository.findAllByOrderByCreatedAtAsc())
                .thenReturn(List.of(AdminImage.create(
                        "images/uploaded.jpg",
                        "https://storage.googleapis.com/test-bucket/images/uploaded.jpg"
                )));

        ImageUploadResponse response = adminImageService.uploadImage(ADMIN_MEMBER_ID, file, null);

        assertThat(response.imageUrl()).isEqualTo("https://storage.googleapis.com/test-bucket/images/uploaded.jpg");

        ArgumentCaptor<AdminImage> savedImageCaptor = ArgumentCaptor.forClass(AdminImage.class);
        verify(adminImageRepository).save(savedImageCaptor.capture());
        assertThat(savedImageCaptor.getValue().getObjectName()).startsWith("images/");
        assertThat(savedImageCaptor.getValue().getObjectName()).endsWith(".jpg");
    }

    @Test
    @DisplayName("이미지 파일이 없으면 업로드할 수 없다")
    void uploadImageThrowsWhenFileIsMissing() {
        assertThatThrownBy(() -> adminImageService.uploadImage(ADMIN_MEMBER_ID, null, null))
                .isInstanceOf(YbcException.class)
                .hasMessage("이미지 파일은 필수입니다.");
    }

    @Test
    @DisplayName("관리자가 아니면 이미지를 업로드할 수 없다")
    void uploadImageThrowsWhenMemberIsNotAdmin() {
        MockMultipartFile file = new MockMultipartFile("file", "main.jpg", "image/jpeg", "image-data".getBytes());

        assertThatThrownBy(() -> adminImageService.uploadImage(NON_ADMIN_MEMBER_ID, file, null))
                .isInstanceOf(YbcException.class)
                .hasMessage("접근 권한이 없는 회원입니다.");

        verify(imageStorageClient, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("지원하지 않는 파일 형식이면 업로드할 수 없다")
    void uploadImageThrowsWhenFileTypeIsUnsupported() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "not-image".getBytes());

        assertThatThrownBy(() -> adminImageService.uploadImage(ADMIN_MEMBER_ID, file, null))
                .isInstanceOf(YbcException.class)
                .hasMessage("지원하지 않는 파일 형식입니다.");

        verify(imageStorageClient, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("이미지 용량이 10MB를 초과하면 업로드할 수 없다")
    void uploadImageThrowsWhenFileSizeIsTooLarge() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "main.jpg",
                "image/jpeg",
                new byte[10 * 1024 * 1024 + 1]
        );

        assertThatThrownBy(() -> adminImageService.uploadImage(ADMIN_MEMBER_ID, file, null))
                .isInstanceOf(YbcException.class)
                .hasMessage("이미지 파일은 10MB 이하여야 합니다.");

        verify(imageStorageClient, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("기존 이미지 URL이 전달되면 새 이미지 업로드 후 기존 이미지를 삭제한다")
    void uploadImageDeletesExistingImageWhenImageUrlProvided() {
        MockMultipartFile file = new MockMultipartFile("file", "main.png", "image/png", "image-data".getBytes());
        String existingImageUrl = "https://storage.googleapis.com/test-bucket/images/old-image.png";
        AdminImage existingImage = AdminImage.create("images/old-image.png", existingImageUrl);

        when(imageStorageClient.upload(any(), any(), any()))
                .thenReturn("https://storage.googleapis.com/test-bucket/images/new-image.png");
        when(adminImageRepository.save(any(AdminImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(adminImageRepository.findByObjectName("images/old-image.png"))
                .thenReturn(Optional.of(existingImage));
        when(adminImageRepository.findAllByOrderByCreatedAtAsc())
                .thenReturn(List.of(AdminImage.create(
                        "images/new-image.png",
                        "https://storage.googleapis.com/test-bucket/images/new-image.png"
                )));

        adminImageService.uploadImage(ADMIN_MEMBER_ID, file, existingImageUrl);

        verify(imageStorageClient).delete("images/old-image.png");
        verify(adminImageRepository).delete(existingImage);
    }

    @Test
    @DisplayName("저장된 이미지가 최대 개수를 넘으면 가장 오래된 이미지를 삭제한다")
    void uploadImageDeletesOldestImageWhenLimitIsExceeded() {
        MockMultipartFile file = new MockMultipartFile("file", "main.webp", "image/webp", "image-data".getBytes());
        AdminImage oldestImage = AdminImage.create(
                "images/oldest.jpg",
                "https://storage.googleapis.com/test-bucket/images/oldest.jpg"
        );

        when(imageStorageClient.upload(any(), any(), any()))
                .thenReturn("https://storage.googleapis.com/test-bucket/images/newest.webp");
        when(adminImageRepository.save(any(AdminImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(adminImageRepository.findAllByOrderByCreatedAtAsc())
                .thenReturn(List.of(
                        oldestImage,
                        AdminImage.create("images/second.jpg", "https://storage.googleapis.com/test-bucket/images/second.jpg"),
                        AdminImage.create("images/third.jpg", "https://storage.googleapis.com/test-bucket/images/third.jpg"),
                        AdminImage.create("images/fourth.jpg", "https://storage.googleapis.com/test-bucket/images/fourth.jpg"),
                        AdminImage.create("images/fifth.jpg", "https://storage.googleapis.com/test-bucket/images/fifth.jpg"),
                        AdminImage.create("images/sixth.jpg", "https://storage.googleapis.com/test-bucket/images/sixth.jpg"),
                        AdminImage.create("images/newest.webp", "https://storage.googleapis.com/test-bucket/images/newest.webp")
                ));

        adminImageService.uploadImage(ADMIN_MEMBER_ID, file, null);

        verify(imageStorageClient).delete("images/oldest.jpg");
        verify(adminImageRepository).delete(oldestImage);
    }

    @Test
    @DisplayName("이미지 ID로 이미지를 삭제하면 GCS와 DB에서 함께 제거한다")
    void deleteImageDeletesStoredImage() {
        AdminImage adminImage = AdminImage.create(
                "images/main.jpg",
                "https://storage.googleapis.com/test-bucket/images/main.jpg"
        );

        when(adminImageRepository.findById(1L)).thenReturn(Optional.of(adminImage));

        adminImageService.deleteImage(ADMIN_MEMBER_ID, 1L);

        verify(imageStorageClient).delete("images/main.jpg");
        verify(adminImageRepository).delete(adminImage);
    }

    @Test
    @DisplayName("관리자가 아니면 이미지를 삭제할 수 없다")
    void deleteImageThrowsWhenMemberIsNotAdmin() {
        assertThatThrownBy(() -> adminImageService.deleteImage(NON_ADMIN_MEMBER_ID, 1L))
                .isInstanceOf(YbcException.class)
                .hasMessage("접근 권한이 없는 회원입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 이미지 ID로 삭제하면 예외가 발생한다")
    void deleteImageThrowsWhenImageIsMissing() {
        when(adminImageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminImageService.deleteImage(ADMIN_MEMBER_ID, 999L))
                .isInstanceOf(YbcException.class)
                .hasMessage("이미지를 찾을 수 없습니다.");
    }

    private Member adminMember() {
        return Member.createSocialMember(
                SocialProvider.KAKAO,
                "123456789",
                "관리자",
                "admin@example.com",
                true
        );
    }

    private Member nonAdminMember() {
        return Member.createSocialMember(
                SocialProvider.KAKAO,
                "987654321",
                "일반회원",
                "user@example.com",
                false
        );
    }
}
