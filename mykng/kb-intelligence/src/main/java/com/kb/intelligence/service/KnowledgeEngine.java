package com.kb.intelligence.service;

import lombok.Data;

public interface KnowledgeEngine {

    ImportStats importFromPath(String path, boolean incremental);

    Long processSingleFile(String filePath, String content);

    @Data
    class ImportStats {
        private int totalFiles;
        private int successFiles;
        private int failedFiles;
        private long durationMs;
    }
}
