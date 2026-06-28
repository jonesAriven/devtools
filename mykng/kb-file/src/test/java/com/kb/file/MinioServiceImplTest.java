package com.kb.file;

import com.kb.file.service.impl.MinioServiceImpl;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MinIO 存储服务单元测试")
class MinioServiceImplTest {

    @Mock private MinioClient minioClient;

    @InjectMocks
    private MinioServiceImpl minioService;

    private MultipartFile multipartFile;

    @BeforeEach
    void setUp() {
        multipartFile = new MockMultipartFile("file", "test.txt", "text/plain", "hello world".getBytes());
    }

    @Test
    @DisplayName("上传文件(MultipartFile) - 成功返回objectName")
    void uploadMultipartFileSuccess() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        String result = minioService.upload("bucket", "obj/test.txt", multipartFile);

        assertEquals("obj/test.txt", result);
    }

    @Test
    @DisplayName("上传文件(MultipartFile) - 失败抛出RuntimeException")
    void uploadMultipartFileFailure() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("network error"));

        assertThrows(RuntimeException.class, () -> minioService.upload("bucket", "obj", multipartFile));
    }

    @Test
    @DisplayName("上传文件(InputStream) - 成功返回objectName")
    void uploadStreamSuccess() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        InputStream is = new ByteArrayInputStream("data".getBytes());
        String result = minioService.upload("bucket", "obj", is, 4, "text/plain");

        assertEquals("obj", result);
    }

    @Test
    @DisplayName("上传文件(InputStream) - 失败抛出RuntimeException")
    void uploadStreamFailure() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("disk full"));

        InputStream is = new ByteArrayInputStream("data".getBytes());
        assertThrows(RuntimeException.class, () -> minioService.upload("bucket", "obj", is, 4, "text/plain"));
    }

    @Test
    @DisplayName("流式分片上传 - 成功无异常")
    void uploadStreamMultipartSuccess() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        InputStream is = new ByteArrayInputStream("data".getBytes());
        long partSize = 5 * 1024 * 1024L; // MinIO 要求 partSize 至少 5MiB
        assertDoesNotThrow(() -> minioService.uploadStream("bucket", "obj", is, 4, partSize, "application/octet-stream"));
    }

    @Test
    @DisplayName("流式分片上传 - 失败抛出RuntimeException")
    void uploadStreamMultipartFailure() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("upload error"));

        InputStream is = new ByteArrayInputStream("data".getBytes());
        long partSize = 5 * 1024 * 1024L; // MinIO 要求 partSize 至少 5MiB
        assertThrows(RuntimeException.class,
                () -> minioService.uploadStream("bucket", "obj", is, 4, partSize, "application/octet-stream"));
    }

    @Test
    @DisplayName("下载文件 - 成功返回InputStream")
    void downloadSuccess() throws Exception {
        Headers headers = new Headers.Builder().build();
        InputStream body = new ByteArrayInputStream("file content".getBytes());
        GetObjectResponse response = new GetObjectResponse(headers, "bucket", "region", "obj", body);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        InputStream result = minioService.download("bucket", "obj");
        assertNotNull(result);
        assertSame(response, result);
    }

    @Test
    @DisplayName("下载文件 - 失败抛出RuntimeException")
    void downloadFailure() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("not found"));

        assertThrows(RuntimeException.class, () -> minioService.download("bucket", "obj"));
    }

    @Test
    @DisplayName("删除文件 - 成功无异常")
    void removeSuccess() throws Exception {
        assertDoesNotThrow(() -> minioService.remove("bucket", "obj"));
    }

    @Test
    @DisplayName("删除文件 - 失败被捕获不抛异常")
    void removeFailureCaught() throws Exception {
        doThrow(new RuntimeException("permission denied"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertDoesNotThrow(() -> minioService.remove("bucket", "obj"));
    }

    @Test
    @DisplayName("获取预签名URL - 成功返回URL")
    void getPresignedUrlSuccess() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.local/signed-url");

        String url = minioService.getPresignedUrl("bucket", "obj", 3600);
        assertEquals("https://minio.local/signed-url", url);
    }

    @Test
    @DisplayName("获取预签名URL - 失败抛出RuntimeException")
    void getPresignedUrlFailure() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("signing error"));

        assertThrows(RuntimeException.class, () -> minioService.getPresignedUrl("bucket", "obj", 3600));
    }

    @Test
    @DisplayName("检查Bucket - 已存在不创建")
    void ensureBucketAlreadyExists() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        assertDoesNotThrow(() -> minioService.ensureBucket("existing-bucket"));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("检查Bucket - 不存在则创建")
    void ensureBucketNotExistsCreatesBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        assertDoesNotThrow(() -> minioService.ensureBucket("new-bucket"));
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("检查Bucket - 检查失败抛出RuntimeException")
    void ensureBucketFailure() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThrows(RuntimeException.class, () -> minioService.ensureBucket("bucket"));
    }
}
