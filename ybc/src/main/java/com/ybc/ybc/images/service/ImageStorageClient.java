package com.ybc.ybc.images.service;

public interface ImageStorageClient {
    String upload(String objectName, byte[] fileBytes, String contentType, String cacheControl);

    void delete(String objectName);
}
