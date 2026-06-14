package com.jones.kb.service;

import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface MinioService {

    String upload(String bucket, String objectName, MultipartFile file);

    String upload(String bucket, String objectName, InputStream inputStream, long size, String contentType);

    InputStream download(String bucket, String objectName);

    void remove(String bucket, String objectName);

    String getPresignedUrl(String bucket, String objectName, int expirySeconds);

    void ensureBucket(String bucketName);
}
