package com.ybc.ybc.images.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ybc.ybc.auth.domain.Member;
import com.ybc.ybc.auth.domain.MemberPrincipal;
import com.ybc.ybc.auth.domain.SocialProvider;
import com.ybc.ybc.auth.dto.MemberSummary;
import com.ybc.ybc.auth.repository.MemberRepository;
import com.ybc.ybc.images.repository.AdminImageRepository;
import com.ybc.ybc.images.service.ImageStorageClient;
import com.ybc.ybc.support.TestMailConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestMailConfig.class, AdminImageControllerIntegrationTest.ImageStorageTestConfig.class})
@TestPropertySource(properties = {
        "app.storage.gcs.bucket=test-bucket",
        "app.storage.gcs.folder=images",
        "app.storage.gcs.max-image-count=6"
})
class AdminImageControllerIntegrationTest {
    private static final String ADMIN_IMAGES_API = "/admin/images";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminImageRepository adminImageRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InMemoryImageStorageClient imageStorageClient;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private Member adminMember;
    private Member nonAdminMember;

    @BeforeEach
    void setUp() {
        adminImageRepository.deleteAll();
        memberRepository.deleteAll();
        imageStorageClient.clear();
        adminMember = memberRepository.save(Member.createSocialMember(
                SocialProvider.KAKAO,
                "123456789",
                "admin",
                "admin@example.com",
                true
        ));
        nonAdminMember = memberRepository.save(Member.createSocialMember(
                SocialProvider.KAKAO,
                "987654321",
                "user",
                "user@example.com",
                false
        ));
    }

    @Test
    @DisplayName("인증된 사용자는 이미지를 업로드하고 공개 URL을 받을 수 있다")
    void uploadImageReturnsImageUrlWhenAuthenticated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "main.jpg",
                "image/jpeg",
                "image-data".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart(ADMIN_IMAGES_API)
                        .file(file)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(startsWith("https://storage.googleapis.com/test-bucket/images/")));

        assertThat(adminImageRepository.count()).isEqualTo(1);
        assertThat(imageStorageClient.storedObjectCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("인증 정보가 없으면 이미지 업로드는 401을 반환한다")
    void uploadImageReturnsUnauthorizedWithoutAccessToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "main.jpg",
                "image/jpeg",
                "image-data".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart(ADMIN_IMAGES_API).file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("관리자가 아닌 사용자가 이미지를 업로드하면 401을 반환한다")
    void uploadImageReturnsUnauthorizedWhenUserIsNotAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "main.jpg",
                "image/jpeg",
                "image-data".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart(ADMIN_IMAGES_API)
                        .file(file)
                        .with(authorizedNonAdminUser()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없는 회원입니다."));
    }

    @Test
    @DisplayName("이미지 파일 없이 업로드하면 400을 반환한다")
    void uploadImageReturnsBadRequestWhenFileIsMissing() throws Exception {
        mockMvc.perform(multipart(ADMIN_IMAGES_API)
                        .with(authorizedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("IMAGE_FILE_REQUIRED"))
                .andExpect(jsonPath("$.message").value("이미지 파일은 필수입니다."));
    }

    @Test
    @DisplayName("지원하지 않는 파일 형식이면 400을 반환한다")
    void uploadImageReturnsBadRequestWhenFileTypeIsUnsupported() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "not-image".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart(ADMIN_IMAGES_API)
                        .file(file)
                        .with(authorizedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_IMAGE_FILE_TYPE"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 파일 형식입니다."));
    }

    @Test
    @DisplayName("이미지 용량이 10MB를 초과하면 400을 반환한다")
    void uploadImageReturnsBadRequestWhenFileSizeIsTooLarge() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "main.jpg",
                "image/jpeg",
                new byte[10 * 1024 * 1024 + 1]
        );

        mockMvc.perform(multipart(ADMIN_IMAGES_API)
                        .file(file)
                        .with(authorizedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("IMAGE_FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("이미지 파일은 10MB 이하여야 합니다."));
    }

    @Test
    @DisplayName("기존 이미지 URL을 함께 보내면 새 이미지 업로드 후 기존 이미지를 삭제한다")
    void uploadImageDeletesExistingImageWhenImageUrlIsProvided() throws Exception {
        MockMultipartFile firstFile = new MockMultipartFile(
                "file",
                "main.jpg",
                "image/jpeg",
                "first-image".getBytes(StandardCharsets.UTF_8)
        );
        String firstResponseBody = mockMvc.perform(multipart(ADMIN_IMAGES_API)
                        .file(firstFile)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String existingImageUrl = objectMapper.readTree(firstResponseBody).get("imageUrl").asText();

        MockMultipartFile secondFile = new MockMultipartFile(
                "file",
                "activity.png",
                "image/png",
                "second-image".getBytes(StandardCharsets.UTF_8)
        );

        String secondResponseBody = mockMvc.perform(multipart(ADMIN_IMAGES_API)
                        .file(secondFile)
                        .param("existingImageUrl", existingImageUrl)
                        .with(authorizedUser()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(secondResponseBody);
        String newImageUrl = json.get("imageUrl").asText();

        assertThat(adminImageRepository.count()).isEqualTo(1);
        assertThat(imageStorageClient.containsUrl(existingImageUrl)).isFalse();
        assertThat(imageStorageClient.containsUrl(newImageUrl)).isTrue();
    }

    @Test
    @DisplayName("인증된 사용자는 imageId로 이미지를 삭제할 수 있다")
    void deleteImageReturnsOkWhenAuthenticated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "main.jpg",
                "image/jpeg",
                "image-data".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart(ADMIN_IMAGES_API)
                        .file(file)
                        .with(authorizedUser()))
                .andExpect(status().isOk());

        Long imageId = adminImageRepository.findAll().get(0).getId();
        String imageUrl = adminImageRepository.findAll().get(0).getImageUrl();

        mockMvc.perform(delete(ADMIN_IMAGES_API + "/{imageId}", imageId)
                        .with(authorizedUser()))
                .andExpect(status().isOk());

        assertThat(adminImageRepository.findById(imageId)).isEmpty();
        assertThat(imageStorageClient.containsUrl(imageUrl)).isFalse();
    }

    @Test
    @DisplayName("관리자가 아닌 사용자는 이미지를 삭제할 수 없다")
    void deleteImageReturnsUnauthorizedWhenUserIsNotAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "main.jpg",
                "image/jpeg",
                "image-data".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart(ADMIN_IMAGES_API)
                        .file(file)
                        .with(authorizedUser()))
                .andExpect(status().isOk());

        Long imageId = adminImageRepository.findAll().get(0).getId();

        mockMvc.perform(delete(ADMIN_IMAGES_API + "/{imageId}", imageId)
                        .with(authorizedNonAdminUser()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없는 회원입니다."));
    }

    @Test
    @DisplayName("인증 정보가 없으면 이미지 삭제는 401을 반환한다")
    void deleteImageReturnsUnauthorizedWithoutAccessToken() throws Exception {
        mockMvc.perform(delete(ADMIN_IMAGES_API + "/{imageId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED_USER"));
    }

    @Test
    @DisplayName("존재하지 않는 이미지 삭제는 404를 반환한다")
    void deleteImageReturnsNotFoundWhenImageDoesNotExist() throws Exception {
        mockMvc.perform(delete(ADMIN_IMAGES_API + "/{imageId}", 999L)
                        .with(authorizedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("IMAGE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("이미지를 찾을 수 없습니다."));
    }

    private RequestPostProcessor authorizedUser() {
        return authenticatedMember(adminMember);
    }

    private RequestPostProcessor authorizedNonAdminUser() {
        return authenticatedMember(nonAdminMember);
    }

    private RequestPostProcessor authenticatedMember(Member member) {
        MemberPrincipal principal = MemberPrincipal.from(MemberSummary.from(member));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        ));
    }

    @TestConfiguration
    static class ImageStorageTestConfig {
        @Bean
        InMemoryImageStorageClient inMemoryImageStorageClient() {
            return new InMemoryImageStorageClient("test-bucket");
        }

        @Bean
        @Primary
        ImageStorageClient imageStorageClient(InMemoryImageStorageClient inMemoryImageStorageClient) {
            return inMemoryImageStorageClient;
        }
    }

    static class InMemoryImageStorageClient implements ImageStorageClient {
        private final String bucket;
        private final Map<String, String> storedImages = new ConcurrentHashMap<>();

        InMemoryImageStorageClient(String bucket) {
            this.bucket = bucket;
        }

        @Override
        public String upload(String objectName, byte[] fileBytes, String contentType) {
            String imageUrl = "https://storage.googleapis.com/" + bucket + "/" + objectName;
            storedImages.put(objectName, imageUrl);
            return imageUrl;
        }

        @Override
        public void delete(String objectName) {
            storedImages.remove(objectName);
        }

        void clear() {
            storedImages.clear();
        }

        int storedObjectCount() {
            return storedImages.size();
        }

        boolean containsUrl(String imageUrl) {
            return storedImages.containsValue(imageUrl);
        }
    }
}
