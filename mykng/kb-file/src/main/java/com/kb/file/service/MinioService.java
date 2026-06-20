package com.kb.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface MinioService {

    String upload(String bucket, String objectName, MultipartFile file);

    String upload(String bucket, String objectName, InputStream inputStream, long size, String contentType);

    /**
     * 流式上传（支持分片上传，避免 OOM）
     * <p>
     * 当 size 未知时传 -1，MinIO 将使用 partSize 进行分片上传。
     *
     * @param bucket      桶名
     * @param objectName  对象名
     * @param stream      输入流
     * @param size        总大小（字节），未知传 -1
     * @param partSize    分片大小（字节），用于 multipart upload
     * @param contentType 内容类型
     */
    void uploadStream(String bucket, String objectName, InputStream stream, long size, long partSize, String contentType);

    InputStream download(String bucket, String objectName);

    void remove(String bucket, String objectName);

    String getPresignedUrl(String bucket, String objectName, int expirySeconds);

    void ensureBucket(String bucketName);
}
