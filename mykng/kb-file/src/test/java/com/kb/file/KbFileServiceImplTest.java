package com.kb.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.exception.BusinessException;
import com.kb.common.page.PageResult;
import com.kb.file.dto.file.FileMergeRequest;
import com.kb.file.entity.FileChunk;
import com.kb.file.entity.KbFile;
import com.kb.file.mapper.FileChunkMapper;
import com.kb.file.mapper.KbFileMapper;
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

import java.util.Arrays;
import java.util.List;

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

    @InjectMocks
    private KbFileServiceImpl fileService;

    @Test
    @DisplayName("简单上传 - 无 fileId 直接上传文件")
    void simpleUploadSuccess() {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        when(kbFileMapper.insert(any(KbFile.class))).thenReturn(1);

        String result = fileService.uploadChunk(1L, null, null, file);

        assertNotNull(result);
        verify(minioService).upload(eq("kb-file"), anyString(), eq(file));
        verify(kbFileMapper).insert(any(KbFile.class));
        verify(searchIndexService).indexFile(any(KbFile.class), anyString());
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
    @DisplayName("删除文件 - 软删除")
    void deleteFile() {
        KbFile file = new KbFile();
        file.setId(1L);
        file.setUserId(1L);

        when(kbFileMapper.selectById(1L)).thenReturn(file);
        when(kbFileMapper.updateById(any(KbFile.class))).thenReturn(1);

        assertDoesNotThrow(() -> fileService.delete(1L, 1L));
        verify(kbFileMapper).updateById(any(KbFile.class));
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
}
