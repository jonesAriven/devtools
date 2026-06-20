package com.kb.file.service;

import cn.hutool.json.JSONUtil;
import com.kb.file.entity.KbFile;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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
}
