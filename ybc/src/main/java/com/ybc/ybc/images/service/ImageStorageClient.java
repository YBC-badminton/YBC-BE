package com.ybc.ybc.images.service;

public interface ImageStorageClient {
    String upload(String objectName, byte[] fileBytes, String contentType);

    void delete(String objectName);
}
