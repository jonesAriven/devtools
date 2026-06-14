package com.jones.kb.service;

public interface FileParseService {

    void parseFile(Long fileId, String minioPath, String fileType);
}
