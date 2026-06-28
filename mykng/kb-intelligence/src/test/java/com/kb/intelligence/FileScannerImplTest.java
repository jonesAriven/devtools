package com.kb.intelligence;

import cn.hutool.crypto.digest.DigestUtil;
import com.kb.intelligence.entity.KnDoc;
import com.kb.intelligence.mapper.KnDocMapper;
import com.kb.intelligence.service.FileScanner;
import com.kb.intelligence.service.impl.FileScannerImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FileScannerImpl 单元测试")
class FileScannerImplTest {

    @Mock
    private KnDocMapper docMapper;

    @InjectMocks
    private FileScannerImpl scanner;

    @Test
    @DisplayName("scanDirectory - 路径不存在返回空列表")
    void scanDirectory_nonExistentPath_returnsEmptyList(@TempDir Path tempDir) {
        String nonExistent = tempDir.resolve("non-existent-subdir").toString();

        List<FileScanner.FileToParse> result = scanner.scanDirectory(nonExistent, false);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(docMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("scanDirectory - markdown文件正确扫描并返回内容与hash")
    void scanDirectory_markdownFiles_returnsFiles(@TempDir Path tempDir) throws IOException {
        Path mdFile = tempDir.resolve("doc.md");
        String content = "# 测试标题\n\n正文内容";
        Files.writeString(mdFile, content);

        List<FileScanner.FileToParse> result = scanner.scanDirectory(tempDir.toString(), false);

        assertEquals(1, result.size());
        FileScanner.FileToParse f = result.get(0);
        assertEquals("doc.md", f.getFileName());
        assertTrue(f.getFilePath().endsWith("doc.md"));
        assertEquals(content, f.getContent());
        assertEquals(DigestUtil.sha256Hex(content), f.getContentHash());
        verify(docMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("scanDirectory - txt文件被正确扫描")
    void scanDirectory_txtFiles_returnsFiles(@TempDir Path tempDir) throws IOException {
        Path txtFile = tempDir.resolve("notes.txt");
        Files.writeString(txtFile, "纯文本内容");

        List<FileScanner.FileToParse> result = scanner.scanDirectory(tempDir.toString(), false);

        assertEquals(1, result.size());
        assertEquals("notes.txt", result.get(0).getFileName());
    }

    @Test
    @DisplayName("scanDirectory - markdown扩展名文件被扫描")
    void scanDirectory_markdownExtension_returnsFiles(@TempDir Path tempDir) throws IOException {
        Path mdFile = tempDir.resolve("doc.markdown");
        Files.writeString(mdFile, "# Markdown扩展");

        List<FileScanner.FileToParse> result = scanner.scanDirectory(tempDir.toString(), false);

        assertEquals(1, result.size());
        assertEquals("doc.markdown", result.get(0).getFileName());
    }

    @Test
    @DisplayName("scanDirectory - 不支持的扩展名被过滤")
    void scanDirectory_unsupportedExtension_filteredOut(@TempDir Path tempDir) throws IOException {
        Path jsonFile = tempDir.resolve("config.json");
        Files.writeString(jsonFile, "{}");
        Path mdFile = tempDir.resolve("doc.md");
        Files.writeString(mdFile, "# 文档");

        List<FileScanner.FileToParse> result = scanner.scanDirectory(tempDir.toString(), false);

        assertEquals(1, result.size());
        assertEquals("doc.md", result.get(0).getFileName());
    }

    @Test
    @DisplayName("scanDirectory - 增量模式文件未变更跳过")
    void scanDirectory_incrementalUnchangedFile_skipsFile(@TempDir Path tempDir) throws IOException {
        Path mdFile = tempDir.resolve("doc.md");
        String content = "# 不变内容";
        Files.writeString(mdFile, content);
        String hash = DigestUtil.sha256Hex(content);

        KnDoc existing = new KnDoc();
        existing.setFilePath(mdFile.toAbsolutePath().toString());
        existing.setContentHash(hash);
        when(docMapper.selectOne(any())).thenReturn(existing);

        List<FileScanner.FileToParse> result = scanner.scanDirectory(tempDir.toString(), true);

        assertTrue(result.isEmpty());
        verify(docMapper, atLeastOnce()).selectOne(any());
    }

    @Test
    @DisplayName("scanDirectory - 增量模式文件已变更纳入解析")
    void scanDirectory_incrementalChangedFile_includesFile(@TempDir Path tempDir) throws IOException {
        Path mdFile = tempDir.resolve("doc.md");
        Files.writeString(mdFile, "# 新内容");

        KnDoc existing = new KnDoc();
        existing.setFilePath(mdFile.toAbsolutePath().toString());
        existing.setContentHash("old-hash-different");
        when(docMapper.selectOne(any())).thenReturn(existing);

        List<FileScanner.FileToParse> result = scanner.scanDirectory(tempDir.toString(), true);

        assertEquals(1, result.size());
        assertEquals("doc.md", result.get(0).getFileName());
    }

    @Test
    @DisplayName("scanDirectory - 增量模式无历史记录纳入解析")
    void scanDirectory_incrementalNoExisting_includesFile(@TempDir Path tempDir) throws IOException {
        Path mdFile = tempDir.resolve("doc.md");
        Files.writeString(mdFile, "# 内容");

        when(docMapper.selectOne(any())).thenReturn(null);

        List<FileScanner.FileToParse> result = scanner.scanDirectory(tempDir.toString(), true);

        assertEquals(1, result.size());
        assertEquals("doc.md", result.get(0).getFileName());
    }

    @Test
    @DisplayName("scanDirectory - 目录遍历异常返回空列表")
    void scanDirectory_walkThrowsIOException_returnsEmptyList() throws IOException {
        Path root = Paths.get("/fake-nonexistent-dir-xyz");
        try (MockedStatic<Files> mocked = mockStatic(Files.class)) {
            mocked.when(() -> Files.exists(root)).thenReturn(true);
            mocked.when(() -> Files.walk(root)).thenThrow(new IOException("walk失败"));

            List<FileScanner.FileToParse> result = scanner.scanDirectory("/fake-nonexistent-dir-xyz", false);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("scanDirectory - 单文件读取失败跳过该文件")
    void scanDirectory_readThrowsIOException_skipsFile() throws IOException {
        Path root = Paths.get("/fake-dir-xyz");
        Path file = mock(Path.class);
        Path fileName = mock(Path.class);
        when(fileName.toString()).thenReturn("bad.md");
        when(file.getFileName()).thenReturn(fileName);
        when(file.toFile()).thenReturn(new java.io.File("/fake-dir-xyz/bad.md"));

        try (MockedStatic<Files> mocked = mockStatic(Files.class)) {
            mocked.when(() -> Files.exists(root)).thenReturn(true);
            mocked.when(() -> Files.walk(root)).thenReturn(Stream.of(file));
            mocked.when(() -> Files.isRegularFile(file)).thenReturn(true);
            mocked.when(() -> Files.readString(eq(file), any())).thenThrow(new IOException("读取失败"));

            List<FileScanner.FileToParse> result = scanner.scanDirectory("/fake-dir-xyz", false);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }
}
