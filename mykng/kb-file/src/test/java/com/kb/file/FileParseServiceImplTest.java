package com.kb.file;

import com.kb.file.entity.KbFile;
import com.kb.file.mapper.KbFileMapper;
import com.kb.file.mongo.doc.FileContent;
import com.kb.file.mongo.repository.FileContentRepository;
import com.kb.file.service.EventPublisher;
import com.kb.file.service.MinioService;
import com.kb.file.service.SearchIndexService;
import com.kb.file.service.impl.FileParseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("文件解析服务单元测试")
class FileParseServiceImplTest {

    @Mock private KbFileMapper kbFileMapper;
    @Mock private MinioService minioService;
    @Mock private FileContentRepository fileContentRepository;
    @Mock private SearchIndexService searchIndexService;
    @Mock private EventPublisher eventPublisher;

    @InjectMocks
    private FileParseServiceImpl fileParseService;

    private KbFile testFile;

    @BeforeEach
    void setUp() {
        testFile = new KbFile();
        testFile.setId(1L);
        testFile.setUserId(100L);
        testFile.setName("document.txt");
        testFile.setType("txt");
        testFile.setMinioPath("files/doc.txt");
        testFile.setParseStatus("PENDING");
    }

    @Test
    @DisplayName("解析文件 - 文件不存在跳过解析")
    void parseFile_fileNotFound_skipsAndReturns() {
        when(kbFileMapper.selectById(999L)).thenReturn(null);

        assertDoesNotThrow(() -> fileParseService.parseFile(999L, "files/doc.txt", "txt"));
        verify(kbFileMapper, never()).updateById(any());
        verify(minioService, never()).download(anyString(), anyString());
    }

    @Test
    @DisplayName("解析 txt 文件 - 成功保存内容并建立索引")
    void parseFile_txtFile_success() {
        when(kbFileMapper.selectById(1L)).thenReturn(testFile);
        when(minioService.download("kb-file", "files/doc.txt"))
                .thenReturn(new ByteArrayInputStream("hello world".getBytes()));
        when(fileContentRepository.findByFileIdAndIsCurrentTrue(1L)).thenReturn(Optional.empty());
        when(fileContentRepository.findByFileIdOrderByVersionDesc(1L)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> fileParseService.parseFile(1L, "files/doc.txt", "txt"));

        verify(kbFileMapper, times(2)).updateById(any(KbFile.class));
        verify(fileContentRepository).save(any(FileContent.class));
        verify(searchIndexService).indexFile(any(KbFile.class), eq("hello world"));
        verify(eventPublisher).publishFileParsed(eq(1L), eq(100L), eq("document.txt"), eq("hello world"));
    }

    @Test
    @DisplayName("解析 txt 文件 - 归档旧版本并保存新版本")
    void parseFile_txtFile_archivesOldCurrentVersion() {
        FileContent oldCurrent = new FileContent();
        oldCurrent.setFileId(1L);
        oldCurrent.setVersion(1);
        oldCurrent.setIsCurrent(true);

        when(kbFileMapper.selectById(1L)).thenReturn(testFile);
        when(minioService.download("kb-file", "files/doc.txt"))
                .thenReturn(new ByteArrayInputStream("new content".getBytes()));
        when(fileContentRepository.findByFileIdAndIsCurrentTrue(1L)).thenReturn(Optional.of(oldCurrent));
        when(fileContentRepository.findByFileIdOrderByVersionDesc(1L)).thenReturn(Arrays.asList(oldCurrent));

        assertDoesNotThrow(() -> fileParseService.parseFile(1L, "files/doc.txt", "txt"));

        verify(fileContentRepository).save(oldCurrent);
        assertFalse(oldCurrent.getIsCurrent());
        verify(fileContentRepository).save(argThat(fc -> fc.getIsCurrent() && fc.getVersion() == 2));
    }

    @Test
    @DisplayName("解析 txt 文件 - 存在多版本时版本号递增")
    void parseFile_txtFile_incrementsVersion() {
        FileContent v2 = new FileContent();
        v2.setVersion(2);
        FileContent v1 = new FileContent();
        v1.setVersion(1);

        when(kbFileMapper.selectById(1L)).thenReturn(testFile);
        when(minioService.download("kb-file", "files/doc.txt"))
                .thenReturn(new ByteArrayInputStream("v3 content".getBytes()));
        when(fileContentRepository.findByFileIdAndIsCurrentTrue(1L)).thenReturn(Optional.empty());
        when(fileContentRepository.findByFileIdOrderByVersionDesc(1L)).thenReturn(Arrays.asList(v2, v1));

        assertDoesNotThrow(() -> fileParseService.parseFile(1L, "files/doc.txt", "txt"));

        verify(fileContentRepository).save(argThat(fc -> fc.getVersion() == 3));
    }

    @Test
    @DisplayName("解析文件 - 不支持的文件类型返回默认内容")
    void parseFile_unsupportedType_success() {
        testFile.setType("exe");
        when(kbFileMapper.selectById(1L)).thenReturn(testFile);
        when(minioService.download("kb-file", "files/doc.txt"))
                .thenReturn(new ByteArrayInputStream("binary".getBytes()));
        when(fileContentRepository.findByFileIdAndIsCurrentTrue(1L)).thenReturn(Optional.empty());
        when(fileContentRepository.findByFileIdOrderByVersionDesc(1L)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> fileParseService.parseFile(1L, "files/doc.txt", "exe"));

        verify(searchIndexService).indexFile(any(KbFile.class), contains("暂不支持解析"));
        verify(eventPublisher).publishFileParsed(eq(1L), eq(100L), eq("document.txt"), contains("暂不支持解析"));
    }

    @Test
    @DisplayName("解析文件 - 文件类型为 null 走默认分支")
    void parseFile_nullType_success() {
        testFile.setType(null);
        when(kbFileMapper.selectById(1L)).thenReturn(testFile);
        when(minioService.download("kb-file", "files/doc.txt"))
                .thenReturn(new ByteArrayInputStream("data".getBytes()));
        when(fileContentRepository.findByFileIdAndIsCurrentTrue(1L)).thenReturn(Optional.empty());
        when(fileContentRepository.findByFileIdOrderByVersionDesc(1L)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> fileParseService.parseFile(1L, "files/doc.txt", null));

        verify(searchIndexService).indexFile(any(KbFile.class), contains("暂不支持解析"));
    }

    @Test
    @DisplayName("解析文件 - 下载失败设置 PARSE_FAILED 状态")
    void parseFile_downloadFails_setsParseFailed() {
        when(kbFileMapper.selectById(1L)).thenReturn(testFile);
        when(minioService.download("kb-file", "files/doc.txt"))
                .thenThrow(new RuntimeException("MinIO download failed"));

        assertDoesNotThrow(() -> fileParseService.parseFile(1L, "files/doc.txt", "txt"));

        assertEquals("PARSE_FAILED", testFile.getParseStatus());
        assertNotNull(testFile.getParseError());
        verify(kbFileMapper, times(2)).updateById(any(KbFile.class));
        verify(fileContentRepository, never()).save(any(FileContent.class));
        verify(searchIndexService, never()).indexFile(any(), anyString());
    }

    @Test
    @DisplayName("解析 md 文件 - 成功提取内容")
    void parseFile_mdFile_success() {
        testFile.setName("readme.md");
        testFile.setType("md");
        when(kbFileMapper.selectById(1L)).thenReturn(testFile);
        when(minioService.download("kb-file", "files/doc.txt"))
                .thenReturn(new ByteArrayInputStream("# Title\ncontent".getBytes()));
        when(fileContentRepository.findByFileIdAndIsCurrentTrue(1L)).thenReturn(Optional.empty());
        when(fileContentRepository.findByFileIdOrderByVersionDesc(1L)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> fileParseService.parseFile(1L, "files/doc.txt", "md"));

        verify(searchIndexService).indexFile(any(KbFile.class), eq("# Title\ncontent"));
    }
}
