package com.kb.intelligence.service;

import lombok.Data;

import java.util.List;

public interface FileScanner {

    List<FileToParse> scanDirectory(String rootPath, boolean incremental);

    @Data
    class FileToParse {
        private String filePath;
        private String fileName;
        private String content;
        private String contentHash;
    }
}
