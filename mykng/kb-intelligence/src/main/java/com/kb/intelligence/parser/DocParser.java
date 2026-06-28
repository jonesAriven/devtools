package com.kb.intelligence.parser;

public interface DocParser {
    boolean supports(DocType docType);
    ParseResult parse(String filePath, String fileName, String content, ParseResult context);
}
