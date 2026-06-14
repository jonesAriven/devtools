package com.jones.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jones.kb.common.BusinessException;
import com.jones.kb.entity.KbFile;
import com.jones.kb.mapper.KbFileMapper;
import com.jones.kb.mongo.doc.FileContent;
import com.jones.kb.mongo.repository.FileContentRepository;
import com.jones.kb.service.FileParseService;
import com.jones.kb.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
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

    @Async("kbAsyncExecutor")
    @Override
    public void parseFile(Long fileId, String minioPath, String fileType) {
        KbFile file = kbFileMapper.selectById(fileId);
        if (file == null) return;

        file.setParseStatus("PARSING");
        kbFileMapper.updateById(file);

        try {
            String content = extractContent(minioPath, fileType);

            fileContentRepository.findByFileIdAndIsCurrentTrue(fileId).ifPresent(current -> {
                current.setIsCurrent(false);
                fileContentRepository.save(current);
            });

            List<FileContent> versions = fileContentRepository.findByFileIdOrderByVersionDesc(fileId);
            int nextVersion = versions.isEmpty() ? 1 : versions.get(0).getVersion() + 1;

            FileContent fileContent = new FileContent();
            fileContent.setFileId(fileId);
            fileContent.setUserId(file.getUserId());
            fileContent.setTitle(file.getName());
            fileContent.setContent(content);
            fileContent.setVersion(nextVersion);
            fileContent.setIsCurrent(true);
            fileContent.setCreatedAt(LocalDateTime.now());
            fileContentRepository.save(fileContent);

            file.setParseStatus("READY");
            file.setParseError(null);
            kbFileMapper.updateById(file);

        } catch (Exception e) {
            log.error("文件解析失败 fileId={}: {}", fileId, e.getMessage());
            file.setParseStatus("PARSE_FAILED");
            file.setParseError(e.getMessage());
            kbFileMapper.updateById(file);
        }
    }

    private String extractContent(String minioPath, String fileType) throws Exception {
        try (java.io.InputStream is = minioService.download("kb-file", minioPath)) {
            return switch (fileType.toLowerCase()) {
                case "txt", "md", "csv", "log", "json", "xml", "yaml", "yml", "properties", "conf", "ini", "sh", "bat", "sql", "java", "py", "js", "ts", "html", "css", "go", "rs", "c", "cpp", "h" ->
                        new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                                .lines().collect(Collectors.joining("\n"));
                default -> {
                    log.info("暂不支持解析 {} 类型文件，仅保存元数据", fileType);
                    yield "[文件内容暂不支持解析，文件类型: " + fileType + "]";
                }
            };
        }
    }
}
