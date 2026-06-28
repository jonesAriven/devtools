package com.kb.intelligence.service.impl;

import com.kb.intelligence.parser.*;
import com.kb.intelligence.service.EntityPersister;
import com.kb.intelligence.service.FileScanner;
import com.kb.intelligence.service.KnowledgeEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeEngineImpl implements KnowledgeEngine {

    private final DocTypeDetector docTypeDetector;
    private final TableParser tableParser;
    private final PlanDocParser planDocParser;
    private final TimelineParser timelineParser;
    private final GeneralParser generalParser;
    private final EntityPersister entityPersister;
    private final FileScanner fileScanner;

    @Override
    public ImportStats importFromPath(String path, boolean incremental) {
        log.info("开始导入知识: path={}, incremental={}", path, incremental);
        long startTime = System.currentTimeMillis();

        List<FileScanner.FileToParse> files = fileScanner.scanDirectory(path, incremental);

        ImportStats stats = new ImportStats();
        stats.setTotalFiles(files.size());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (FileScanner.FileToParse f : files) {
            try {
                processFile(f);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
                log.error("解析文件失败: {} - {}", f.getFilePath(), e.getMessage(), e);
            }
        }

        stats.setSuccessFiles(successCount.get());
        stats.setFailedFiles(failCount.get());
        stats.setDurationMs(System.currentTimeMillis() - startTime);
        log.info("导入完成: {}", stats);
        return stats;
    }

    @Override
    public Long processSingleFile(String filePath, String content) {
        DocType docType = docTypeDetector.detect(extractFileName(filePath), content);
        ParseResult result = buildParseResult(filePath, content, docType);

        List<DocParser> parsers = List.of(tableParser, planDocParser, timelineParser, generalParser);
        for (DocParser parser : parsers) {
            if (parser.supports(docType)) {
                result = parser.parse(filePath, extractFileName(filePath), content, result);
            }
        }

        return entityPersister.persist(result);
    }

    private void processFile(FileScanner.FileToParse f) {
        String fileName = f.getFileName();
        String content = f.getContent();
        String filePath = f.getFilePath();

        DocType docType = docTypeDetector.detect(fileName, content);
        log.debug("处理文件: {} -> 类型: {}", fileName, docType);

        ParseResult result = buildParseResult(filePath, content, docType);

        List<DocParser> parsers = List.of(tableParser, planDocParser, timelineParser, generalParser);
        for (DocParser parser : parsers) {
            if (parser.supports(docType)) {
                result = parser.parse(filePath, fileName, content, result);
            }
        }

        result.getDocMeta().setDocType(docType.name());
        entityPersister.persist(result);
    }

    private ParseResult buildParseResult(String filePath, String content, DocType docType) {
        ParseResult result = new ParseResult();
        com.kb.intelligence.entity.KnDoc docMeta = new com.kb.intelligence.entity.KnDoc();
        docMeta.setFilePath(filePath);
        docMeta.setDocType(docType.name());
        result.setDocMeta(docMeta);
        return result;
    }

    private String extractFileName(String filePath) {
        int idx = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return idx >= 0 ? filePath.substring(idx + 1) : filePath;
    }
}
