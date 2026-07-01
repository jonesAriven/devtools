package com.kb.file.service;

import cn.hutool.json.JSONUtil;
import com.kb.file.entity.KbFile;
import com.kb.file.mapper.KbFileMapper;
import com.kb.file.mongo.doc.FileContent;
import com.kb.file.mongo.repository.FileContentRepository;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.model.Settings;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MeiliSearch 索引服务
 * <p>
 * 管理 kb_files 索引的写入和删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    private static final String INDEX_UID = "kb_files";

    private final Client meiliSearchClient;
    private final KbFileMapper kbFileMapper;
    private final FileContentRepository fileContentRepository;

    /**
     * 启动时初始化索引和 filterable attributes
     * <p>
     * 避免每次重启后都需要手动通过 API 设置 filterableAttributes，
     * 否则带 filter 的搜索会报 invalid_search_filter 错误。
     */
    @PostConstruct
    public void initIndex() {
        configureIndex(new String[]{"userId", "fileId", "folderId", "starred"});
    }

    private void configureIndex(String[] filterableAttributes) {
        try {
            // 显式创建索引并指定主键为 "id"，避免主键推断失败
            try {
                meiliSearchClient.createIndex(INDEX_UID, "id");
                log.info("MeiliSearch 索引 {} 创建任务已提交（primaryKey=id）", INDEX_UID);
            } catch (Exception ce) {
                log.debug("MeiliSearch 索引 {} 创建任务结果: {}", INDEX_UID, ce.getMessage());
            }
            Index index = meiliSearchClient.index(INDEX_UID);
            Settings settings = new Settings();
            settings.setFilterableAttributes(filterableAttributes);
            index.updateSettings(settings);
            log.info("MeiliSearch 索引 {} filterableAttributes 已设置: {}", INDEX_UID, String.join(", ", filterableAttributes));
        } catch (Exception e) {
            log.warn("MeiliSearch 索引 {} 配置失败（可能索引尚不存在，将在首次写入时自动创建）: {}", INDEX_UID, e.getMessage());
        }
    }

    /**
     * 写入/更新文件索引
     */
    public void indexFile(KbFile file, String content) {
        try {
            Index index = meiliSearchClient.index(INDEX_UID);
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", String.valueOf(file.getId()));
            doc.put("fileId", file.getId());
            doc.put("userId", file.getUserId());
            doc.put("folderId", file.getFolderId());
            doc.put("name", file.getName());
            doc.put("type", file.getType());
            doc.put("content", content);
            doc.put("starred", file.getStarred());
            doc.put("createdAt", file.getCreatedAt() != null ? file.getCreatedAt().toString() : null);
            index.addDocuments(JSONUtil.toJsonStr(doc));
            log.info("写入 MeiliSearch 索引成功 fileId={}", file.getId());
        } catch (MeilisearchException e) {
            log.error("写入 MeiliSearch 索引失败 fileId={}: {}", file.getId(), e.getMessage());
        }
    }

    /**
     * 删除文件索引
     */
    public void removeIndex(Long fileId) {
        try {
            Index index = meiliSearchClient.index(INDEX_UID);
            index.deleteDocument(String.valueOf(fileId));
            log.info("删除 MeiliSearch 索引成功 fileId={}", fileId);
        } catch (MeilisearchException e) {
            log.error("删除 MeiliSearch 索引失败 fileId={}: {}", fileId, e.getMessage());
        }
    }

    /**
     * 全量重建文件索引
     * <p>
     * 删除并重新创建 kb_files 索引，然后从数据库读取所有文件记录，
     * 从 MongoDB 读取文件内容，写入 MeiliSearch。
     *
     * @return 重建的文档数量
     */
    public int rebuildAllIndexes() {
        try {
            // 1. 删除旧索引（如果存在）
            try {
                meiliSearchClient.deleteIndex(INDEX_UID);
                log.info("删除旧 kb_files 索引成功");
            } catch (Exception e) {
                log.warn("删除旧 kb_files 索引失败（可能不存在）: {}", e.getMessage());
            }

            // 2. 创建新索引并配置 filterableAttributes
            configureIndex(new String[]{"userId", "fileId", "folderId", "starred"});

            // 3. 获取索引对象
            Index index = meiliSearchClient.index(INDEX_UID);

            // 4. 从数据库读取所有文件
            List<KbFile> files = kbFileMapper.selectList(null);
            log.info("从数据库读取到 {} 条文件记录", files.size());

            int successCount = 0;
            for (KbFile file : files) {
                try {
                    // 5. 从 MongoDB 读取文件内容
                    String content = "";
                    Optional<FileContent> fileContent = fileContentRepository.findByFileIdAndIsCurrentTrue(file.getId());
                    if (fileContent.isPresent()) {
                        content = fileContent.get().getContent();
                        if (content != null && content.length() > 50000) {
                            content = content.substring(0, 50000);
                        }
                    }

                    // 6. 写入 MeiliSearch
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("id", String.valueOf(file.getId()));
                    doc.put("fileId", file.getId());
                    doc.put("userId", file.getUserId());
                    doc.put("folderId", file.getFolderId());
                    doc.put("name", file.getName());
                    doc.put("type", file.getType());
                    doc.put("content", content);
                    doc.put("starred", file.getStarred() != null ? file.getStarred() : 0);
                    doc.put("createdAt", file.getCreatedAt() != null ? file.getCreatedAt().toString() : null);
                    index.addDocuments(JSONUtil.toJsonStr(doc));
                    successCount++;
                } catch (Exception e) {
                    log.error("重建索引失败 fileId={}: {}", file.getId(), e.getMessage());
                }
            }

            log.info("全量重建文件索引完成，成功 {} 条，共 {} 条", successCount, files.size());
            return successCount;
        } catch (Exception e) {
            log.error("全量重建文件索引失败: {}", e.getMessage());
            throw new RuntimeException("重建索引失败: " + e.getMessage(), e);
        }
    }
}
