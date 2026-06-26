package com.kb.knowledge.service;

import cn.hutool.json.JSONUtil;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.entity.Folder;
import com.kb.knowledge.entity.WebPage;
import com.kb.knowledge.mongo.doc.DocContent;
import com.kb.knowledge.mongo.doc.WebContent;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.Settings;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * MeiliSearch 索引服务
 * <p>
 * 管理 kb-knowledge 拥有的三个索引：
 * - kb_docs: 笔记索引
 * - kb_webpages: 网页索引
 * - kb_folders: 目录索引
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    public static final String INDEX_DOCS = "kb_docs";
    public static final String INDEX_WEBPAGES = "kb_webpages";
    public static final String INDEX_FOLDERS = "kb_folders";

    private final Client meiliSearchClient;

    /**
     * 启动时初始化索引和 filterable attributes
     */
    @PostConstruct
    public void initIndexes() {
        configureIndex(INDEX_DOCS, new String[]{"userId", "folderId", "starred"});
        configureIndex(INDEX_WEBPAGES, new String[]{"userId", "folderId", "starred"});
        configureIndex(INDEX_FOLDERS, new String[]{"spaceId", "parentId"});
        log.info("MeiliSearch 索引初始化完成");
    }

    private void configureIndex(String uid, String[] filterableAttributes) {
        try {
            Index index = meiliSearchClient.index(uid);
            // 显式创建索引并指定主键为 "id"，
            // 避免文档含多个以 id 结尾的字段时主键推断失败
            // （参考错误：index_primary_key_multiple_candidates_found）
            try {
                meiliSearchClient.createIndex(uid, "id");
                log.info("MeiliSearch 索引 {} 创建任务已提交（primaryKey=id）", uid);
            } catch (Exception ce) {
                // 索引已存在或主键已固定，忽略创建异常
                log.debug("MeiliSearch 索引 {} 创建任务结果: {}", uid, ce.getMessage());
            }
            Settings settings = new Settings();
            settings.setFilterableAttributes(filterableAttributes);
            index.updateSettings(settings);
            log.info("MeiliSearch 索引 {} filterableAttributes 已设置: {}", uid, String.join(", ", filterableAttributes));
        } catch (Exception e) {
            log.warn("MeiliSearch 索引 {} 配置失败（可能索引尚不存在，将在首次写入时自动创建）: {}", uid, e.getMessage());
        }
    }

    /**
     * 写入/更新笔记索引
     */
    public void indexDoc(Doc doc, String content) {
        try {
            Index index = meiliSearchClient.index(INDEX_DOCS);
            Map<String, Object> document = new HashMap<>();
            document.put("id", String.valueOf(doc.getId()));
            document.put("docId", doc.getId());
            document.put("userId", doc.getUserId());
            document.put("folderId", doc.getFolderId());
            document.put("title", doc.getTitle());
            document.put("content", content != null ? content : "");
            document.put("starred", doc.getStarred());
            document.put("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null);
            document.put("updatedAt", doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null);
            index.addDocuments(JSONUtil.toJsonStr(document));
            log.info("已提交 MeiliSearch 笔记索引任务 docId={}", doc.getId());
        } catch (Exception e) {
            log.error("已提交 MeiliSearch 笔记索引失败 docId={}: {}", doc.getId(), e.getMessage());
        }
    }

    /**
     * 删除笔记索引
     */
    public void removeDocIndex(Long docId) {
        try {
            Index index = meiliSearchClient.index(INDEX_DOCS);
            index.deleteDocument(String.valueOf(docId));
            log.info("删除 MeiliSearch 笔记索引任务 docId={}", docId);
        } catch (Exception e) {
            log.error("删除 MeiliSearch 笔记索引失败 docId={}: {}", docId, e.getMessage());
        }
    }

    /**
     * 写入/更新网页索引
     */
    public void indexWebPage(WebPage webPage, String content) {
        try {
            Index index = meiliSearchClient.index(INDEX_WEBPAGES);
            Map<String, Object> document = new HashMap<>();
            document.put("id", String.valueOf(webPage.getId()));
            document.put("webId", webPage.getId());
            document.put("userId", webPage.getUserId());
            document.put("folderId", webPage.getFolderId());
            document.put("url", webPage.getUrl());
            document.put("title", webPage.getTitle());
            document.put("content", content != null ? content : "");
            document.put("starred", webPage.getStarred());
            document.put("createdAt", webPage.getCreatedAt() != null ? webPage.getCreatedAt().toString() : null);
            index.addDocuments(JSONUtil.toJsonStr(document));
            log.info("已提交 MeiliSearch 网页索引任务 webId={}", webPage.getId());
        } catch (Exception e) {
            log.error("已提交 MeiliSearch 网页索引失败 webId={}: {}", webPage.getId(), e.getMessage());
        }
    }

    /**
     * 删除网页索引
     */
    public void removeWebPageIndex(Long webId) {
        try {
            Index index = meiliSearchClient.index(INDEX_WEBPAGES);
            index.deleteDocument(String.valueOf(webId));
            log.info("删除 MeiliSearch 网页索引任务 webId={}", webId);
        } catch (Exception e) {
            log.error("删除 MeiliSearch 网页索引失败 webId={}: {}", webId, e.getMessage());
        }
    }

    /**
     * 写入/更新目录索引
     */
    public void indexFolder(Folder folder) {
        try {
            Index index = meiliSearchClient.index(INDEX_FOLDERS);
            Map<String, Object> document = new HashMap<>();
            document.put("id", String.valueOf(folder.getId()));
            document.put("folderId", folder.getId());
            document.put("spaceId", folder.getSpaceId());
            document.put("parentId", folder.getParentId());
            document.put("name", folder.getName());
            document.put("sortOrder", folder.getSortOrder());
            document.put("createdAt", folder.getCreatedAt() != null ? folder.getCreatedAt().toString() : null);
            index.addDocuments(JSONUtil.toJsonStr(document));
            log.info("已提交 MeiliSearch 目录索引任务 folderId={}", folder.getId());
        } catch (Exception e) {
            log.error("已提交 MeiliSearch 目录索引失败 folderId={}: {}", folder.getId(), e.getMessage());
        }
    }

    /**
     * 删除目录索引
     */
    public void removeFolderIndex(Long folderId) {
        try {
            Index index = meiliSearchClient.index(INDEX_FOLDERS);
            index.deleteDocument(String.valueOf(folderId));
            log.info("删除 MeiliSearch 目录索引任务 folderId={}", folderId);
        } catch (Exception e) {
            log.error("删除 MeiliSearch 目录索引失败 folderId={}: {}", folderId, e.getMessage());
        }
    }
}
