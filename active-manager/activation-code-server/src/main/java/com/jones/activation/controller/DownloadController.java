package com.jones.activation.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 二维码工具下载控制器
 * 
 * 扫描共享目录下的 exe 文件，提供版本列表和文件下载。
 * 目录路径通过 download.base-dir 配置，docker-compose 中映射宿主机共享目录。
 */
@RestController
@RequestMapping("/activecode/api/download")
public class DownloadController {

    private static final Logger log = LoggerFactory.getLogger(DownloadController.class);

    @Value("${download.base-dir:/app/downloads}")
    private String baseDir;

    /**
     * 获取可下载的版本列表
     * 扫描 baseDir 下所有 .exe 文件，按修改时间倒序
     */
    @GetMapping("/list")
    public Map<String, Object> listVersions() {
        Map<String, Object> result = new HashMap<>();
        try {
            File dir = Paths.get(baseDir).normalize().toAbsolutePath().toFile();
            if (!dir.exists() || !dir.isDirectory()) {
                result.put("success", false);
                result.put("message", "下载目录不存在: " + baseDir);
                result.put("data", Collections.emptyList());
                return result;
            }

            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".exe"));
            List<Map<String, Object>> versions = new ArrayList<>();

            if (files != null) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File f : files) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("filename", f.getName());
                    item.put("size", f.length());
                    item.put("sizeFormatted", formatFileSize(f.length()));
                    item.put("lastModified", f.lastModified());
                    item.put("lastModifiedStr", new Date(f.lastModified()).toString());
                    versions.add(item);
                }
            }

            result.put("success", true);
            result.put("data", versions);
        } catch (Exception e) {
            log.error("获取下载列表失败", e);
            result.put("success", false);
            result.put("message", "服务器内部错误: " + e.getMessage());
            result.put("data", Collections.emptyList());
        }
        return result;
    }

    /**
     * 下载指定文件
     * 路径遍历防护：只允许 .exe 结尾，且必须在 baseDir 内
     */
    @GetMapping("/{filename:.+\\.exe")
    public ResponseEntity<Resource> download(@PathVariable String filename,
                                           HttpServletResponse response) throws IOException {
        // 安全校验：防止路径遍历
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        if (!filename.toLowerCase().endsWith(".exe")) {
            return ResponseEntity.badRequest().build();
        }

        Path filePath = Paths.get(baseDir, filename).normalize();
        Path basePath = Paths.get(baseDir).normalize().toAbsolutePath();

        // 确保最终路径在允许的目录内
        if (!filePath.startsWith(basePath)) {
            return ResponseEntity.status(403).build();
        }

        File file = filePath.toFile();
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(file.length())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
