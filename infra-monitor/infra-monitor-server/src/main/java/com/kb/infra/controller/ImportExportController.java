package com.kb.infra.controller;

import com.kb.common.result.Result;
import com.kb.infra.service.ImportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/io")
@RequiredArgsConstructor
public class ImportExportController {

    private final ImportExportService importExportService;
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectWriter JSON_WRITER = JSON_MAPPER.writerWithDefaultPrettyPrinter();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectWriter YAML_WRITER = YAML_MAPPER.writerWithDefaultPrettyPrinter();

    @PostMapping("/import")
    public Result<Map<String, Object>> importData(@RequestParam("file") MultipartFile file) {
        return Result.ok(importExportService.importData(file));
    }

    @GetMapping("/export/json")
    public ResponseEntity<byte[]> exportJson() {
        try {
            Map<String, Object> data = importExportService.exportData();
            String json = JSON_WRITER.writeValueAsString(data);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            String filename = "infra-export-" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/export/yaml")
    public ResponseEntity<byte[]> exportYaml() {
        try {
            Map<String, Object> data = importExportService.exportData();
            String yaml = YAML_WRITER.writeValueAsString(data);
            byte[] bytes = yaml.getBytes(StandardCharsets.UTF_8);
            String filename = "infra-export-" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".yaml";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/x-yaml"))
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
