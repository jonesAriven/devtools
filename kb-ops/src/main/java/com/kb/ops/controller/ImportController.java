package com.kb.ops.controller;

import cn.hutool.core.io.IoUtil;
import com.kb.common.result.Result;
import com.kb.ops.dto.ImportRequest;
import com.kb.ops.dto.ImportResult;
import com.kb.ops.service.ImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运维知识导入接口
 * <p>
 * 支持两种方式：
 * 1. POST /api/ops/import  上传结构化 JSON（ImportRequest，rows 为字段名->值映射）
 * 2. POST /api/ops/import/csv  上传 CSV 文件，按 type 指定的实体解析
 */
@Slf4j
@RestController
@RequestMapping("/ops/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    /**
     * 结构化数据导入
     */
    @PostMapping
    public Result<ImportResult> importData(@RequestBody ImportRequest request) {
        return Result.ok(importService.importData(request));
    }

    /**
     * CSV 文件导入
     *
     * @param file     CSV 文件（首行为表头）
     * @param type     导入类型: HOST / SERVICE / KNOWLEDGE
     * @param override 是否覆盖同名记录
     */
    @PostMapping("/csv")
    public Result<ImportResult> importCsv(@RequestParam("file") MultipartFile file,
                                          @RequestParam(defaultValue = "HOST") String type,
                                          @RequestParam(defaultValue = "false") boolean override) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "文件为空");
        }
        List<Map<String, String>> rows = parseCsv(file);
        ImportRequest request = new ImportRequest();
        request.setType(type);
        request.setOverride(override);
        request.setRows(rows);
        log.info("[CSV导入] type={} rows={}", type, rows.size());
        return Result.ok(importService.importData(request));
    }

    /**
     * 解析 CSV 文件为 字段名->值 的行列表。
     * 使用 Hutool IoUtil 读取行，自行处理带引号的字段切分。
     */
    private List<Map<String, String>> parseCsv(MultipartFile file) throws IOException {
        List<String> lines;
        try (InputStreamReader isr = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            lines = IoUtil.readLines(isr, new ArrayList<>());
        }
        List<Map<String, String>> rows = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return rows;
        }
        List<String> header = splitCsvLine(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            List<String> fields = splitCsvLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < header.size() && j < fields.size(); j++) {
                row.put(header.get(j).trim(), fields.get(j));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 切分单行 CSV（支持双引号包裹与转义）
     */
    private List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        if (line == null) {
            return result;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    result.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        result.add(cur.toString());
        return result;
    }
}
