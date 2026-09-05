package com.kb.file.service.impl;

import com.marschat.common.exception.BusinessException;
import com.kb.file.entity.KbFile;
import com.kb.file.mapper.KbFileMapper;
import com.kb.file.mongo.doc.FileContent;
import com.kb.file.mongo.repository.FileContentRepository;
import com.kb.file.service.EventPublisher;
import com.kb.file.service.FileParseService;
import com.kb.file.service.MinioService;
import com.kb.file.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileParseServiceImpl implements FileParseService {

    private final KbFileMapper kbFileMapper;
    private final MinioService minioService;
    private final FileContentRepository fileContentRepository;
    private final SearchIndexService searchIndexService;
    private final EventPublisher eventPublisher;

    /**
     * 解析文件内容
     * <p>
     * 注意：本方法本身不再标注 @Async，异步调用由 FileParseTrigger 统一管理。
     * 这样避免了原代码中 @Async 自调用导致代理失效的问题。
     */
    @Override
    public void parseFile(Long fileId, String minioPath, String fileType) {
        KbFile file = kbFileMapper.selectById(fileId);
        if (file == null) {
            log.warn("文件不存在，跳过解析 fileId={}", fileId);
            return;
        }

        file.setParseStatus("PARSING");
        kbFileMapper.updateById(file);

        try {
            String content = extractContent(minioPath, fileType);

            // 归档旧版本
            fileContentRepository.findByFileIdAndIsCurrentTrue(fileId).ifPresent(current -> {
                current.setIsCurrent(false);
                fileContentRepository.save(current);
            });

            List<FileContent> versions = fileContentRepository.findByFileIdOrderByVersionDesc(fileId);
            int nextVersion = versions.isEmpty() ? 1 : versions.get(0).getVersion() + 1;

            // 保存解析内容到 MongoDB
            FileContent fileContent = new FileContent();
            fileContent.setFileId(fileId);
            fileContent.setUserId(file.getUserId());
            fileContent.setTitle(file.getName());
            fileContent.setContent(content);
            fileContent.setVersion(nextVersion);
            fileContent.setIsCurrent(true);
            fileContent.setCreatedAt(LocalDateTime.now());
            fileContentRepository.save(fileContent);

            // 更新文件状态
            file.setParseStatus("READY");
            file.setParseError(null);
            kbFileMapper.updateById(file);

            // 写入 MeiliSearch 索引
            searchIndexService.indexFile(file, content);

            // 发布文件解析完成事件（通知 kb-knowledge 等下游服务）
            eventPublisher.publishFileParsed(fileId, file.getUserId(), file.getName(), content);

            log.info("文件解析成功 fileId={} version={}", fileId, nextVersion);

        } catch (Exception e) {
            log.error("文件解析失败 fileId={}: {}", fileId, e.getMessage(), e);
            file.setParseStatus("PARSE_FAILED");
            file.setParseError(e.getMessage());
            kbFileMapper.updateById(file);
        }
    }

    private String extractContent(String minioPath, String fileType) throws Exception {
        try (InputStream is = minioService.download("kb-file", minioPath)) {
            String type = fileType != null ? fileType.toLowerCase() : "";

            // 纯文本类文件直接读取
            if (isTextFile(type)) {
                return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));
            }

            // Word/Excel/PPT/PDF/RTF 等二进制文档用 Tika 解析
            if (isTikaSupported(type)) {
                return extractWithTika(is);
            }

            log.info("暂不支持解析 {} 类型文件，仅保存元数据", fileType);
            return "[文件内容暂不支持解析，文件类型: " + fileType + "]";
        }
    }

    private boolean isTextFile(String type) {
        return switch (type) {
            case "txt", "md", "csv", "log", "json", "xml", "yaml", "yml",
                 "properties", "conf", "ini", "sh", "bat", "sql",
                 "java", "py", "js", "ts", "html", "css",
                 "go", "rs", "c", "cpp", "h" -> true;
            default -> false;
        };
    }

    private boolean isTikaSupported(String type) {
        return switch (type) {
            case "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                 "pdf", "rtf", "odt", "ods", "odp" -> true;
            default -> false;
        };
    }

    private String extractWithTika(InputStream is) throws Exception {
        org.apache.tika.parser.Parser parser = new org.apache.tika.parser.AutoDetectParser();
        org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
        org.apache.tika.sax.BodyContentHandler handler = new org.apache.tika.sax.BodyContentHandler(10 * 1024 * 1024); // 10MB limit
        parser.parse(is, handler, metadata, new org.apache.tika.parser.ParseContext());
        String content = handler.toString().trim();
        log.info("Tika 解析完成，内容长度: {}", content.length());
        return content;
    }
}
