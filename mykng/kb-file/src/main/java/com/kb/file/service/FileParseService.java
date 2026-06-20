package com.kb.file.service;

public interface FileParseService {

    void parseFile(Long fileId, String minioPath, String fileType);
}
