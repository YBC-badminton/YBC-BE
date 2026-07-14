package com.ybc.ybc.images.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.ybc.ybc.images.config.GcsImageStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class GcsImageStorageClient implements ImageStorageClient {
    private final GcsImageStorageProperties properties;

    private volatile Storage storage;

    @Override
    public String upload(String objectName, byte[] fileBytes, String contentType) {
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.bucket(), objectName))
                    .setContentType(contentType)
                    .build();
            getStorage().create(blobInfo, fileBytes);
            return properties.buildPublicUrl(objectName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize GCS client.", e);
        }
    }

    @Override
    public void delete(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return;
        }

        try {
            getStorage().delete(BlobId.of(properties.bucket(), objectName));
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize GCS client.", e);
        }
    }

    private Storage getStorage() throws IOException {
        if (!properties.hasBucketConfigured()) {
            throw new IllegalStateException("GCS bucket is not configured.");
        }

        Storage currentStorage = storage;
        if (currentStorage != null) {
            return currentStorage;
        }

        synchronized (this) {
            if (storage == null) {
                storage = createStorage();
            }
            return storage;
        }
    }

    private Storage createStorage() throws IOException {
        StorageOptions.Builder storageOptionsBuilder = StorageOptions.newBuilder();
        if (StringUtils.hasText(properties.projectId())) {
            storageOptionsBuilder.setProjectId(properties.projectId());
        }

        if (StringUtils.hasText(properties.credentialsJson())) {
            try (InputStream inputStream = new ByteArrayInputStream(
                    properties.credentialsJson().getBytes(StandardCharsets.UTF_8)
            )) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream);
                return storageOptionsBuilder
                        .setCredentials(credentials)
                        .build()
                        .getService();
            }
        }

        if (StringUtils.hasText(properties.credentialsPath())) {
            try (InputStream inputStream = Files.newInputStream(Path.of(properties.credentialsPath()))) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream);
                return storageOptionsBuilder
                        .setCredentials(credentials)
                        .build()
                        .getService();
            }
        }

        return storageOptionsBuilder.build().getService();
    }
}
