package com.kb.intelligence.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.kb.intelligence.entity.KnDoc;
import com.kb.intelligence.mapper.KnDocMapper;
import com.kb.intelligence.service.FileScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileScannerImpl implements FileScanner {

    private final KnDocMapper docMapper;

    @Override
    public List<FileToParse> scanDirectory(String rootPath, boolean incremental) {
        List<FileToParse> files = new ArrayList<>();
        Path root = Paths.get(rootPath);

        if (!Files.exists(root)) {
            log.warn("扫描路径不存在: {}", rootPath);
            return files;
        }

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".txt");
                })
                .forEach(p -> {
                    try {
                        File file = p.toFile();
                        String content = Files.readString(p, StandardCharsets.UTF_8);
                        String hash = DigestUtil.sha256Hex(content);

                        if (incremental) {
                            KnDoc existing = docMapper.selectOne(
                                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnDoc>()
                                            .eq(KnDoc::getFilePath, file.getAbsolutePath())
                            );
                            if (existing != null && hash.equals(existing.getContentHash())) {
                                log.debug("文件未变更，跳过: {}", file.getAbsolutePath());
                                return;
                            }
                        }

                        FileToParse f = new FileToParse();
                        f.setFilePath(file.getAbsolutePath());
                        f.setFileName(file.getName());
                        f.setContent(content);
                        f.setContentHash(hash);
                        files.add(f);
                    } catch (IOException e) {
                        log.warn("读取文件失败: {}, 原因: {}", p, e.getMessage());
                    }
                });
        } catch (IOException e) {
            log.error("扫描目录失败: {}", rootPath, e);
        }

        log.info("扫描完成: {} 个文件待解析 (路径={}, 增量={})", files.size(), rootPath, incremental);
        return files;
    }
}
