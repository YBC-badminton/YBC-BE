package com.ybc.ybc.images.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.storage.gcs")
public record GcsImageStorageProperties(
        String bucket,
        String projectId,
        String credentialsPath,
        String credentialsJson,
        String folder,
        int maxImageCount
) {
    public GcsImageStorageProperties {
        folder = normalizeFolder(folder);
        maxImageCount = maxImageCount > 0 ? maxImageCount : 6;
    }

    public String buildObjectName(String fileName) {
        return folder + "/" + fileName;
    }

    public String buildPublicUrl(String objectName) {
        return "https://storage.googleapis.com/" + bucket + "/" + objectName;
    }

    public boolean managesObject(String objectName) {
        return StringUtils.hasText(objectName) && objectName.startsWith(folder + "/");
    }

    public boolean hasBucketConfigured() {
        return StringUtils.hasText(bucket);
    }

    private static String normalizeFolder(String folder) {
        if (!StringUtils.hasText(folder)) {
            return "images";
        }

        int start = 0;
        int end = folder.length();
        while (start < end && folder.charAt(start) == '/') {
            start++;
        }
        while (end > start && folder.charAt(end - 1) == '/') {
            end--;
        }

        return start == end ? "images" : folder.substring(start, end);
    }
}
