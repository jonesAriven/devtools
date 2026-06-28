package com.kb.intelligence;

import com.kb.intelligence.parser.DocType;
import com.kb.intelligence.parser.DocTypeDetector;
import com.kb.intelligence.parser.GeneralParser;
import com.kb.intelligence.parser.ParseResult;
import com.kb.intelligence.parser.PlanDocParser;
import com.kb.intelligence.parser.TableParser;
import com.kb.intelligence.parser.TimelineParser;
import com.kb.intelligence.service.EntityPersister;
import com.kb.intelligence.service.FileScanner;
import com.kb.intelligence.service.KnowledgeEngine;
import com.kb.intelligence.service.impl.KnowledgeEngineImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KnowledgeEngine 单元测试")
class KnowledgeEngineTest {

    @Mock private DocTypeDetector docTypeDetector;
    @Mock private TableParser tableParser;
    @Mock private PlanDocParser planDocParser;
    @Mock private TimelineParser timelineParser;
    @Mock private GeneralParser generalParser;
    @Mock private EntityPersister entityPersister;
    @Mock private FileScanner fileScanner;

    @InjectMocks
    private KnowledgeEngineImpl knowledgeEngine;

    private FileScanner.FileToParse createFile(String path, String name, String content) {
        FileScanner.FileToParse file = new FileScanner.FileToParse();
        file.setFilePath(path);
        file.setFileName(name);
        file.setContent(content);
        file.setContentHash("hash-" + name);
        return file;
    }

    @Test
    @DisplayName("importFromPath - 正常导入单文件成功")
    void importFromPath_singleFile_success() {
        FileScanner.FileToParse file = createFile("/docs/test.md", "test.md", "# Test\n\nContent");
        when(fileScanner.scanDirectory("/docs", false)).thenReturn(List.of(file));
        when(docTypeDetector.detect(anyString(), anyString())).thenReturn(DocType.GENERAL);
        when(generalParser.supports(DocType.GENERAL)).thenReturn(true);
        when(generalParser.parse(anyString(), anyString(), anyString(), any(ParseResult.class)))
                .thenAnswer(invocation -> (ParseResult) invocation.getArgument(3));
        when(entityPersister.persist(any(ParseResult.class))).thenReturn(1L);

        KnowledgeEngine.ImportStats stats = knowledgeEngine.importFromPath("/docs", false);

        assertEquals(1, stats.getTotalFiles());
        assertEquals(1, stats.getSuccessFiles());
        assertEquals(0, stats.getFailedFiles());
        verify(entityPersister, times(1)).persist(any(ParseResult.class));
    }

    @Test
    @DisplayName("importFromPath - 多文件全部成功")
    void importFromPath_multipleFiles_allSuccess() {
        FileScanner.FileToParse file1 = createFile("/docs/a.md", "a.md", "# A");
        FileScanner.FileToParse file2 = createFile("/docs/b.md", "b.md", "# B");
        when(fileScanner.scanDirectory("/docs", false)).thenReturn(List.of(file1, file2));
        when(docTypeDetector.detect(anyString(), anyString())).thenReturn(DocType.GENERAL);
        when(generalParser.supports(DocType.GENERAL)).thenReturn(true);
        when(generalParser.parse(anyString(), anyString(), anyString(), any(ParseResult.class)))
                .thenAnswer(invocation -> (ParseResult) invocation.getArgument(3));
        when(entityPersister.persist(any(ParseResult.class))).thenReturn(1L);

        KnowledgeEngine.ImportStats stats = knowledgeEngine.importFromPath("/docs", false);

        assertEquals(2, stats.getTotalFiles());
        assertEquals(2, stats.getSuccessFiles());
        assertEquals(0, stats.getFailedFiles());
        verify(entityPersister, times(2)).persist(any(ParseResult.class));
    }

    @Test
    @DisplayName("importFromPath - 文件解析异常计入失败数")
    void importFromPath_parseError_countedAsFailed() {
        FileScanner.FileToParse file = createFile("/docs/bad.md", "bad.md", "# Bad");
        when(fileScanner.scanDirectory("/docs", false)).thenReturn(List.of(file));
        when(docTypeDetector.detect(anyString(), anyString())).thenThrow(new RuntimeException("解析异常"));

        KnowledgeEngine.ImportStats stats = knowledgeEngine.importFromPath("/docs", false);

        assertEquals(1, stats.getTotalFiles());
        assertEquals(0, stats.getSuccessFiles());
        assertEquals(1, stats.getFailedFiles());
        verify(entityPersister, never()).persist(any(ParseResult.class));
    }

    @Test
    @DisplayName("importFromPath - 空文件列表返回零统计")
    void importFromPath_emptyFiles_zeroStats() {
        when(fileScanner.scanDirectory("/empty", false)).thenReturn(Collections.emptyList());

        KnowledgeEngine.ImportStats stats = knowledgeEngine.importFromPath("/empty", false);

        assertEquals(0, stats.getTotalFiles());
        assertEquals(0, stats.getSuccessFiles());
        assertEquals(0, stats.getFailedFiles());
        verify(entityPersister, never()).persist(any(ParseResult.class));
    }

    @Test
    @DisplayName("importFromPath - incremental=true传递给FileScanner")
    void importFromPath_incrementalTrue_passedToScanner() {
        when(fileScanner.scanDirectory("/docs", true)).thenReturn(Collections.emptyList());

        KnowledgeEngine.ImportStats stats = knowledgeEngine.importFromPath("/docs", true);

        assertEquals(0, stats.getTotalFiles());
        verify(fileScanner, times(1)).scanDirectory("/docs", true);
        verify(fileScanner, never()).scanDirectory("/docs", false);
    }

    @Test
    @DisplayName("importFromPath - partial failure统计正确")
    void importFromPath_partialFailure_correctStats() {
        FileScanner.FileToParse file1 = createFile("/docs/good.md", "good.md", "# Good");
        FileScanner.FileToParse file2 = createFile("/docs/bad.md", "bad.md", "# Bad");
        when(fileScanner.scanDirectory("/docs", false)).thenReturn(List.of(file1, file2));
        when(docTypeDetector.detect(eq("good.md"), anyString())).thenReturn(DocType.GENERAL);
        when(docTypeDetector.detect(eq("bad.md"), anyString())).thenThrow(new RuntimeException("失败"));
        when(generalParser.supports(DocType.GENERAL)).thenReturn(true);
        when(generalParser.parse(anyString(), anyString(), anyString(), any(ParseResult.class)))
                .thenAnswer(invocation -> (ParseResult) invocation.getArgument(3));
        when(entityPersister.persist(any(ParseResult.class))).thenReturn(1L);

        KnowledgeEngine.ImportStats stats = knowledgeEngine.importFromPath("/docs", false);

        assertEquals(2, stats.getTotalFiles());
        assertEquals(1, stats.getSuccessFiles());
        assertEquals(1, stats.getFailedFiles());
    }

    @Test
    @DisplayName("importFromPath - TABLE类型使用TableParser")
    void importFromPath_tableType_usesTableParser() {
        FileScanner.FileToParse file = createFile("/docs/hosts.md", "hosts.md", "# 主机清单");
        when(fileScanner.scanDirectory("/docs", false)).thenReturn(List.of(file));
        when(docTypeDetector.detect(anyString(), anyString())).thenReturn(DocType.TABLE);
        when(tableParser.supports(DocType.TABLE)).thenReturn(true);
        when(tableParser.parse(anyString(), anyString(), anyString(), any(ParseResult.class)))
                .thenAnswer(invocation -> (ParseResult) invocation.getArgument(3));
        when(entityPersister.persist(any(ParseResult.class))).thenReturn(1L);

        KnowledgeEngine.ImportStats stats = knowledgeEngine.importFromPath("/docs", false);

        assertEquals(1, stats.getSuccessFiles());
        verify(tableParser, times(1)).parse(anyString(), anyString(), anyString(), any(ParseResult.class));
        verify(generalParser, never()).parse(anyString(), anyString(), anyString(), any(ParseResult.class));
    }

    @Test
    @DisplayName("processSingleFile - 正常处理返回docId")
    void processSingleFile_normal_returnsDocId() {
        when(docTypeDetector.detect(anyString(), anyString())).thenReturn(DocType.GENERAL);
        when(generalParser.supports(DocType.GENERAL)).thenReturn(true);
        when(generalParser.parse(anyString(), anyString(), anyString(), any(ParseResult.class)))
                .thenAnswer(invocation -> (ParseResult) invocation.getArgument(3));
        when(entityPersister.persist(any(ParseResult.class))).thenReturn(42L);

        Long docId = knowledgeEngine.processSingleFile("/docs/test.md", "# Test\n\nContent");

        assertEquals(42L, docId);
        verify(entityPersister, times(1)).persist(any(ParseResult.class));
    }

    @Test
    @DisplayName("processSingleFile - Windows路径正确提取文件名")
    void processSingleFile_windowsPath_correctFileName() {
        when(docTypeDetector.detect(eq("test.md"), anyString())).thenReturn(DocType.GENERAL);
        when(generalParser.supports(DocType.GENERAL)).thenReturn(true);
        when(generalParser.parse(anyString(), anyString(), anyString(), any(ParseResult.class)))
                .thenAnswer(invocation -> (ParseResult) invocation.getArgument(3));
        when(entityPersister.persist(any(ParseResult.class))).thenReturn(1L);

        knowledgeEngine.processSingleFile("D:\\docs\\test.md", "# Test");

        verify(docTypeDetector, times(1)).detect(eq("test.md"), anyString());
    }
}
