package com.kb.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.exception.BusinessException;
import com.kb.common.page.PageResult;
import com.kb.file.dto.file.FileMergeRequest;
import com.kb.file.dto.file.FileMoveRequest;
import com.kb.file.entity.FileChunk;
import com.kb.file.entity.KbFile;
import com.kb.file.mapper.FileChunkMapper;
import com.kb.file.mapper.KbFileMapper;
import com.kb.file.mongo.doc.FileContent;
import com.kb.file.mongo.repository.FileContentRepository;
import com.kb.file.service.EventPublisher;
import com.kb.file.service.FileParseTrigger;
import com.kb.file.service.MinioService;
import com.kb.file.service.SearchIndexService;
import com.kb.file.service.impl.KbFileServiceImpl;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("文件服务单元测试")
class KbFileServiceImplTest {

    @Mock private KbFileMapper kbFileMapper;
    @Mock private FileChunkMapper fileChunkMapper;
    @Mock private MinioService minioService;
    @Mock private FileParseTrigger fileParseTrigger;
    @Mock private EventPublisher eventPublisher;
    @Mock private SearchIndexService searchIndexService;
    @Mock private FileContentRepository fileContentRepository;

    @InjectMocks
    private KbFileServiceImpl fileService;

    @Test
    @DisplayName("简单上传 - 无 fileId 直接上传文件")
    void simpleUploadSuccess() {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        when(kbFileMapper.insert(any(KbFile.class))).thenAnswer(invocation -> {
            KbFile f = invocation.getArgument(0);
            f.setId(1L);
            return 1;
        });

        String result = fileService.uploadChunk(1L, null, null, file);

        assertNotNull(result);
        verify(minioService).upload(eq("kb-file"), anyString(), eq(file));
        verify(kbFileMapper).insert(any(KbFile.class));
        verify(fileParseTrigger).trigger(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("分片上传 - 带 fileId 和 chunkNumber")
    void chunkUploadSuccess() {
        MultipartFile file = new MockMultipartFile("file", "chunk1", "application/octet-stream", "data".getBytes());

        when(fileChunkMapper.insert(any(FileChunk.class))).thenReturn(1);

        String result = fileService.uploadChunk(1L, "file-abc", 1, file);

        assertNotNull(result);
        verify(minioService).upload(eq("kb-file"), eq("chunks/file-abc/1"), eq(file));
        verify(fileChunkMapper).insert(any(FileChunk.class));
    }

    @Test
    @DisplayName("文件列表 - 分页查询")
    void listFiles() {
        KbFile f1 = new KbFile();
        f1.setId(1L);
        f1.setName("doc1.pdf");
        KbFile f2 = new KbFile();
        f2.setId(2L);
        f2.setName("doc2.pdf");

        Page<KbFile> page = new Page<>(1, 20);
        page.setRecords(Arrays.asList(f1, f2));
        page.setTotal(2);

        when(kbFileMapper.selectPage(any(Page.class), any())).thenReturn(page);

        PageResult<KbFile> result = fileService.list(1L, 0L, 1, 20);

        assertNotNull(result);
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getList().size());
    }

    @Test
    @DisplayName("收藏/取消收藏文件")
    void toggleStar() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        file.setStarred(0);

        when(kbFileMapper.selectById(1L)).thenReturn(file);
        when(kbFileMapper.updateById(any(KbFile.class))).thenReturn(1);

        assertDoesNotThrow(() -> fileService.star(1L, 1L));
        verify(kbFileMapper).updateById(any(KbFile.class));
    }

    @Test
    @DisplayName("删除文件 - 物理删除+清除MinIO+清除索引")
    void deleteFile() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        file.setMinioPath("files/abc.txt");

        when(kbFileMapper.selectById(1L)).thenReturn(file);
        when(kbFileMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> fileService.delete(1L, 1L));
        verify(minioService).remove("kb-file", "files/abc.txt");
        verify(searchIndexService).removeIndex(1L);
        verify(kbFileMapper).deleteById(1L);
        verify(eventPublisher).publishFileDeleted(1L, 1L);
    }

    @Test
    @DisplayName("删除文件 - 无权限")
    void deleteFileNoPermission() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(2L); // 不同用户

        when(kbFileMapper.selectById(1L)).thenReturn(file);

        assertThrows(BusinessException.class, () -> fileService.delete(1L, 1L));
    }

    @Test
    @DisplayName("合并分片 - 成功合并并触发解析")
    void mergeChunksSuccess() {
        FileMergeRequest request = new FileMergeRequest();
        request.setFileId("file-merge-1");
        request.setName("merged.pdf");
        request.setFolderId(0L);
        request.setSize(100L);
        request.setTotalChunks(2);

        when(minioService.download(eq("kb-file"), anyString()))
                .thenReturn(new ByteArrayInputStream("chunk data".getBytes()));
        when(kbFileMapper.insert(any(KbFile.class))).thenAnswer(invocation -> {
            KbFile f = invocation.getArgument(0);
            f.setId(99L);
            return 1;
        });

        KbFile result = fileService.mergeChunks(1L, request);

        assertNotNull(result);
        assertEquals("merged.pdf", result.getName());
        assertEquals("pdf", result.getType());
        assertEquals(0L, result.getFolderId());
        verify(minioService).uploadStream(eq("kb-file"), anyString(), any(), eq(100L), anyLong(), anyString());
        verify(minioService, times(2)).remove(eq("kb-file"), anyString());
        verify(fileChunkMapper).delete(any());
        verify(fileParseTrigger).trigger(eq(99L), anyString(), eq("pdf"));
    }

    @Test
    @DisplayName("合并分片 - 上传失败抛出 BusinessException")
    void mergeChunksUploadFails() {
        FileMergeRequest request = new FileMergeRequest();
        request.setFileId("file-merge-2");
        request.setName("merged.pdf");
        request.setFolderId(0L);
        request.setSize(100L);
        request.setTotalChunks(1);

        when(minioService.download(eq("kb-file"), anyString()))
                .thenReturn(new ByteArrayInputStream("chunk data".getBytes()));
        doThrow(new RuntimeException("MinIO 不可用"))
                .when(minioService).uploadStream(anyString(), anyString(), any(), anyLong(), anyLong(), anyString());

        assertThrows(BusinessException.class, () -> fileService.mergeChunks(1L, request));
        verify(kbFileMapper, never()).insert(any(KbFile.class));
    }

    @Test
    @DisplayName("获取文件 - 成功返回")
    void getByIdSuccess() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        when(kbFileMapper.selectById(1L)).thenReturn(file);

        KbFile result = fileService.getById(1L, 1L);
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("获取文件 - 文件不存在")
    void getByIdNotFound() {
        when(kbFileMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> fileService.getById(1L, 1L));
    }

    @Test
    @DisplayName("获取文件 - 用户无权限")
    void getByIdWrongUser() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(2L);
        when(kbFileMapper.selectById(1L)).thenReturn(file);
        assertThrows(BusinessException.class, () -> fileService.getById(1L, 1L));
    }

    @Test
    @DisplayName("获取解析状态 - 成功返回状态")
    void getParseStatusSuccess() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        file.setParseStatus("READY");
        when(kbFileMapper.selectById(1L)).thenReturn(file);

        String status = fileService.getParseStatus(1L, 1L);
        assertEquals("READY", status);
    }

    @Test
    @DisplayName("获取下载URL - 成功返回预签名URL")
    void getDownloadUrlSuccess() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        file.setMinioPath("files/abc.pdf");
        when(kbFileMapper.selectById(1L)).thenReturn(file);
        when(minioService.getPresignedUrl("kb-file", "files/abc.pdf", 3600)).thenReturn("https://minio.local/signed");

        String url = fileService.getDownloadUrl(1L, 1L);
        assertEquals("https://minio.local/signed", url);
    }

    @Test
    @DisplayName("获取下载URL - 文件路径为null抛异常")
    void getDownloadUrlNullPath() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        file.setMinioPath(null);
        when(kbFileMapper.selectById(1L)).thenReturn(file);

        assertThrows(BusinessException.class, () -> fileService.getDownloadUrl(1L, 1L));
    }

    @Test
    @DisplayName("重新解析 - 重置状态并触发解析")
    void reparseSuccess() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        file.setMinioPath("files/abc.pdf");
        file.setType("pdf");
        file.setParseStatus("READY");
        file.setParseError("some error");
        when(kbFileMapper.selectById(1L)).thenReturn(file);

        assertDoesNotThrow(() -> fileService.reparse(1L, 1L));
        assertEquals("PENDING", file.getParseStatus());
        assertNull(file.getParseError());
        verify(kbFileMapper).updateById(any(KbFile.class));
        verify(fileParseTrigger).trigger(1L, "files/abc.pdf", "pdf");
    }

    @Test
    @DisplayName("删除文件 - minioPath 为null时跳过MinIO删除")
    void deleteFileNullMinioPath() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        file.setMinioPath(null);
        when(kbFileMapper.selectById(1L)).thenReturn(file);
        when(kbFileMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> fileService.delete(1L, 1L));
        verify(minioService, never()).remove(anyString(), anyString());
        verify(searchIndexService).removeIndex(1L);
    }

    @Test
    @DisplayName("收藏文件 - 取消收藏")
    void starToggleOff() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        file.setStarred(1);
        when(kbFileMapper.selectById(1L)).thenReturn(file);

        assertDoesNotThrow(() -> fileService.star(1L, 1L));
        assertEquals(0, file.getStarred());
        verify(kbFileMapper).updateById(any(KbFile.class));
    }

    @Test
    @DisplayName("移动文件 - 成功移动到目标文件夹")
    void moveSuccess() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        file.setFolderId(0L);
        when(kbFileMapper.selectById(1L)).thenReturn(file);

        FileMoveRequest request = new FileMoveRequest();
        request.setFolderId(5L);

        assertDoesNotThrow(() -> fileService.move(1L, 1L, request));
        assertEquals(5L, file.getFolderId());
        verify(kbFileMapper).updateById(any(KbFile.class));
    }

    @Test
    @DisplayName("获取文件内容 - 有内容返回内容")
    void getContentWithContent() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        when(kbFileMapper.selectById(1L)).thenReturn(file);

        FileContent fc = new FileContent();
        fc.setContent("文件内容");
        when(fileContentRepository.findByFileIdAndIsCurrentTrue(1L)).thenReturn(Optional.of(fc));

        String content = fileService.getContent(1L, 1L);
        assertEquals("文件内容", content);
    }

    @Test
    @DisplayName("获取文件内容 - 无内容记录返回空串")
    void getContentNoRecord() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        when(kbFileMapper.selectById(1L)).thenReturn(file);
        when(fileContentRepository.findByFileIdAndIsCurrentTrue(1L)).thenReturn(Optional.empty());

        String content = fileService.getContent(1L, 1L);
        assertEquals("", content);
    }

    @Test
    @DisplayName("获取文件内容 - 内容为null返回空串")
    void getContentNullContent() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        when(kbFileMapper.selectById(1L)).thenReturn(file);

        FileContent fc = new FileContent();
        fc.setContent(null);
        when(fileContentRepository.findByFileIdAndIsCurrentTrue(1L)).thenReturn(Optional.of(fc));

        String content = fileService.getContent(1L, 1L);
        assertEquals("", content);
    }

    @Test
    @DisplayName("分片上传 - MinIO异常抛出BusinessException")
    void chunkUploadMinioFails() {
        MultipartFile file = new MockMultipartFile("file", "chunk1", "application/octet-stream", "data".getBytes());
        doThrow(new RuntimeException("MinIO error"))
                .when(minioService).upload(anyString(), anyString(), any(MultipartFile.class));

        assertThrows(BusinessException.class, () -> fileService.uploadChunk(1L, "file-abc", 1, file));
    }

    @Test
    @DisplayName("简单上传 - MinIO异常抛出BusinessException")
    void simpleUploadMinioFails() {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
        doThrow(new RuntimeException("MinIO error"))
                .when(minioService).upload(anyString(), anyString(), any(MultipartFile.class));

        assertThrows(BusinessException.class, () -> fileService.uploadChunk(1L, null, null, file));
    }

    // ======================== M4-7 回收站功能测试 ========================

    @Test
    @DisplayName("回收站列表 - 返回用户已删除文件")
    void listTrashReturnsDeletedFiles() {
        KbFile f1 = new KbFile();
        f1.setId(1L);
        f1.setUserId(1L);
        f1.setName("deleted.txt");
        when(kbFileMapper.selectTrashList(1L)).thenReturn(List.of(f1));

        List<KbFile> result = fileService.listTrash(1L);

        assertEquals(1, result.size());
        assertEquals("deleted.txt", result.get(0).getName());
        verify(kbFileMapper).selectTrashList(1L);
    }

    @Test
    @DisplayName("恢复文件 - 成功恢复已删除文件")
    void restoreSuccess() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        when(kbFileMapper.selectDeletedById(1L)).thenReturn(file);
        when(kbFileMapper.restoreById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> fileService.restore(1L, 1L));
        verify(kbFileMapper).restoreById(1L);
    }

    @Test
    @DisplayName("恢复文件 - 文件不在回收站抛异常")
    void restoreNotFound() {
        when(kbFileMapper.selectDeletedById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> fileService.restore(1L, 1L));
        verify(kbFileMapper, never()).restoreById(anyLong());
    }

    @Test
    @DisplayName("恢复文件 - 无权限抛异常")
    void restoreNoPermission() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(2L);
        when(kbFileMapper.selectDeletedById(1L)).thenReturn(file);

        assertThrows(BusinessException.class, () -> fileService.restore(1L, 1L));
        verify(kbFileMapper, never()).restoreById(anyLong());
    }

    @Test
    @DisplayName("永久删除 - 成功删除并清理 MinIO+索引+事件")
    void permanentDeleteSuccess() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        file.setMinioPath("files/abc.txt");
        when(kbFileMapper.selectDeletedById(1L)).thenReturn(file);
        when(kbFileMapper.physicalDeleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> fileService.permanentDelete(1L, 1L));

        verify(minioService).remove("kb-file", "files/abc.txt");
        verify(searchIndexService).removeIndex(1L);
        verify(kbFileMapper).physicalDeleteById(1L);
        verify(eventPublisher).publishFilePermanentDeleted(1L, 1L);
    }

    @Test
    @DisplayName("永久删除 - minioPath 为null时跳过 MinIO 删除")
    void permanentDeleteNullMinioPath() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);
        file.setMinioPath(null);
        when(kbFileMapper.selectDeletedById(1L)).thenReturn(file);
        when(kbFileMapper.physicalDeleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> fileService.permanentDelete(1L, 1L));
        verify(minioService, never()).remove(anyString(), anyString());
        verify(searchIndexService).removeIndex(1L);
        verify(eventPublisher).publishFilePermanentDeleted(1L, 1L);
    }

    @Test
    @DisplayName("永久删除 - 文件不存在抛异常")
    void permanentDeleteNotFound() {
        when(kbFileMapper.selectDeletedById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> fileService.permanentDelete(1L, 1L));
        verify(kbFileMapper, never()).physicalDeleteById(anyLong());
    }

    @Test
    @DisplayName("永久删除 - 无权限抛异常")
    void permanentDeleteNoPermission() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(2L);
        when(kbFileMapper.selectDeletedById(1L)).thenReturn(file);

        assertThrows(BusinessException.class, () -> fileService.permanentDelete(1L, 1L));
        verify(kbFileMapper, never()).physicalDeleteById(anyLong());
    }

    @Test
    @DisplayName("清空回收站 - 成功清空多个文件并发布事件")
    void emptyTrashSuccess() {
        KbFile f1 = new KbFile();
        f1.setId(1L);
        f1.setUserId(1L);
        f1.setMinioPath("files/a.txt");
        KbFile f2 = new KbFile();
        f2.setId(2L);
        f2.setUserId(1L);
        f2.setMinioPath("files/b.txt");
        when(kbFileMapper.selectTrashList(1L)).thenReturn(List.of(f1, f2));
        when(kbFileMapper.physicalDeleteAllByUserId(1L)).thenReturn(2);

        int count = fileService.emptyTrash(1L);

        assertEquals(2, count);
        verify(minioService).remove("kb-file", "files/a.txt");
        verify(minioService).remove("kb-file", "files/b.txt");
        verify(searchIndexService).removeIndex(1L);
        verify(searchIndexService).removeIndex(2L);
        verify(kbFileMapper).physicalDeleteAllByUserId(1L);
        verify(eventPublisher).publishFileTrashEmptied(1L, 2);
    }

    @Test
    @DisplayName("清空回收站 - 回收站为空直接返回0")
    void emptyTrashEmpty() {
        when(kbFileMapper.selectTrashList(1L)).thenReturn(Collections.emptyList());

        int count = fileService.emptyTrash(1L);

        assertEquals(0, count);
        verify(kbFileMapper, never()).physicalDeleteAllByUserId(anyLong());
        verify(eventPublisher, never()).publishFileTrashEmptied(anyLong(), anyInt());
    }

    @Test
    @DisplayName("清空回收站 - 单个 MinIO 删除失败不影响整体流程")
    void emptyTrashPartialMinioFailure() {
        KbFile f1 = new KbFile();
        f1.setId(1L);
        f1.setUserId(1L);
        f1.setMinioPath("files/a.txt");
        KbFile f2 = new KbFile();
        f2.setId(2L);
        f2.setUserId(1L);
        f2.setMinioPath("files/b.txt");
        when(kbFileMapper.selectTrashList(1L)).thenReturn(List.of(f1, f2));
        // 第一个文件 MinIO 删除失败
        doThrow(new RuntimeException("MinIO error")).when(minioService).remove("kb-file", "files/a.txt");
        when(kbFileMapper.physicalDeleteAllByUserId(1L)).thenReturn(2);

        int count = fileService.emptyTrash(1L);

        // 即使 MinIO 删除失败，数据库仍然清空
        assertEquals(2, count);
        verify(minioService).remove("kb-file", "files/a.txt");
        verify(minioService).remove("kb-file", "files/b.txt");
        verify(kbFileMapper).physicalDeleteAllByUserId(1L);
        verify(eventPublisher).publishFileTrashEmptied(1L, 2);
    }
}
