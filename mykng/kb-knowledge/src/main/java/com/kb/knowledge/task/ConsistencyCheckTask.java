package com.kb.knowledge.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kb.knowledge.entity.Doc;
import com.kb.knowledge.entity.Share;
import com.kb.knowledge.entity.WebPage;
import com.kb.knowledge.mapper.DocMapper;
import com.kb.knowledge.mapper.ShareMapper;
import com.kb.knowledge.mapper.WebPageMapper;
import com.kb.knowledge.mongo.doc.DocContent;
import com.kb.knowledge.mongo.doc.WebContent;
import com.kb.knowledge.mongo.repository.DocContentRepository;
import com.kb.knowledge.mongo.repository.WebContentRepository;
import com.kb.knowledge.service.SearchIndexService;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据一致性补偿任务（P1 新增）
 * <p>
 * 每天凌晨 03:00 执行，自动修复以下不一致：
 * <ol>
 *   <li>MySQL ↔ MeiliSearch 索引一致性：MySQL 有但索引缺少的 → 补建；索引有但 MySQL 已删的 → 清理</li>
 *   <li>分享链接有效性：资源已删但分享仍有效的 → 标记失效（deleted=1）</li>
 *   <li>孤儿文档检查：folderId 指向已删目录的 → 移至根目录（folderId=0）</li>
 * </ol>
 * <p>
 * 与 Redis Pub/Sub 事件通知形成双重保障：
 * 事件通知负责实时同步，本任务负责兜底补偿（防止事件丢失）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsistencyCheckTask {

    private final DocMapper docMapper;
    private final WebPageMapper webPageMapper;
    private final ShareMapper shareMapper;
    private final SearchIndexService searchIndexService;
    private final Client meiliSearchClient;
    private final DocContentRepository docContentRepository;
    private final WebContentRepository webContentRepository;

    /**
     * 每天 03:00 执行一致性校验
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void consistencyCheck() {
        log.info("[一致性检查] 开始执行");
        int idxFixed = 0, idxCleaned = 0, shareInvalidated = 0, orphanFixed = 0;

        try {
            // === 1. 索引一致性：MySQL 有但 MeiliSearch 缺少的 → 补建 ===
            idxFixed += rebuildMissingDocIndex();
            idxFixed += rebuildMissingWebPageIndex();

            // === 2. 索引清理：MySQL 已删但 MeiliSearch 还有的 → 删索引 ===
            idxCleaned += cleanOrphanDocIndex();
            idxCleaned += cleanOrphanWebPageIndex();

            // === 3. 分享有效性：资源已删但分享仍有效 → 标记失效 ===
            shareInvalidated += invalidateOrphanShares();

            // === 4. 孤儿文档：folderId 指向已删目录 → 移至根目录 ===
            orphanFixed += fixOrphanDocs();
            orphanFixed += fixOrphanWebPages();

        } catch (Exception e) {
            log.error("[一致性检查] 执行失败", e);
        }

        log.info("[一致性检查] 完成 — 补建索引 {}，清理索引 {}，标记失效分享 {}，修复孤儿文档 {}",
                idxFixed, idxCleaned, shareInvalidated, orphanFixed);
    }

    // ================================================================
    // 1. 索引补建：MySQL 有但 MeiliSearch 没有
    // ================================================================

    /**
     * 检查未删除的笔记是否在 MeiliSearch 中有索引，缺少的补建
     */
    private int rebuildMissingDocIndex() {
        int count = 0;
        List<Doc> docs = docMapper.selectList(
                new LambdaQueryWrapper<Doc>().eq(Doc::getDeleted, 0));

        for (Doc doc : docs) {
            if (!docExistsInIndex(SearchIndexService.INDEX_DOCS, doc.getId())) {
                log.warn("[补偿] 笔记索引缺失，补建 docId={}", doc.getId());
                String content = docContentRepository.findByDocIdAndIsCurrentTrue(doc.getId())
                        .map(DocContent::getContent).orElse("");
                searchIndexService.indexDoc(doc, content);
                count++;
            }
        }
        return count;
    }

    /**
     * 检查未删除的网页是否在 MeiliSearch 中有索引，缺少的补建
     */
    private int rebuildMissingWebPageIndex() {
        int count = 0;
        List<WebPage> pages = webPageMapper.selectList(
                new LambdaQueryWrapper<WebPage>().eq(WebPage::getDeleted, 0));

        for (WebPage page : pages) {
            if (!docExistsInIndex(SearchIndexService.INDEX_WEBPAGES, page.getId())) {
                log.warn("[补偿] 网页索引缺失，补建 webId={}", page.getId());
                String content = webContentRepository.findByWebIdAndIsCurrentTrue(page.getId())
                        .map(WebContent::getContent).orElse("");
                searchIndexService.indexWebPage(page, content);
                count++;
            }
        }
        return count;
    }

    // ================================================================
    // 2. 索引清理：MySQL 已删但 MeiliSearch 还有
    // ================================================================

    /**
     * 检查 MeiliSearch 中有但 MySQL 已标记删除的笔记，清理孤儿索引
     */
    private int cleanOrphanDocIndex() {
        int count = 0;
        List<Doc> deletedDocs = docMapper.selectList(
                new LambdaQueryWrapper<Doc>().eq(Doc::getDeleted, 1));

        for (Doc doc : deletedDocs) {
            if (docExistsInIndex(SearchIndexService.INDEX_DOCS, doc.getId())) {
                log.warn("[补偿] 清理孤儿笔记索引 docId={}", doc.getId());
                searchIndexService.removeDocIndex(doc.getId());
                count++;
            }
        }
        return count;
    }

    /**
     * 检查 MeiliSearch 中有但 MySQL 已标记删除的网页，清理孤儿索引
     */
    private int cleanOrphanWebPageIndex() {
        int count = 0;
        List<WebPage> deletedPages = webPageMapper.selectList(
                new LambdaQueryWrapper<WebPage>().eq(WebPage::getDeleted, 1));

        for (WebPage page : deletedPages) {
            if (docExistsInIndex(SearchIndexService.INDEX_WEBPAGES, page.getId())) {
                log.warn("[补偿] 清理孤儿网页索引 webId={}", page.getId());
                searchIndexService.removeWebPageIndex(page.getId());
                count++;
            }
        }
        return count;
    }

    // ================================================================
    // 3. 分享有效性：资源已删但分享仍有效
    // ================================================================

    /**
     * 标记资源已删除的分享为失效（deleted=1）
     */
    private int invalidateOrphanShares() {
        // 查找所有有效分享（deleted=0）
        List<Share> activeShares = shareMapper.selectList(
                new LambdaQueryWrapper<Share>().eq(Share::getDeleted, 0));

        int count = 0;
        for (Share share : activeShares) {
            boolean resourceGone = false;

            if ("doc".equals(share.getResourceType())) {
                Doc doc = docMapper.selectById(share.getResourceId());
                resourceGone = (doc == null || doc.getDeleted() == 1);
            } else if ("web".equals(share.getResourceType())) {
                WebPage page = webPageMapper.selectById(share.getResourceId());
                resourceGone = (page == null || page.getDeleted() == 1);
            }
            // file 类型分享的失效由 IndexEventListener.handleFileDeleted 处理，此处跳过

            if (resourceGone) {
                log.warn("[补偿] 分享资源已删，标记失效 shareId={} resourceType={} resourceId={}",
                        share.getId(), share.getResourceType(), share.getResourceId());
                shareMapper.update(null,
                        new LambdaUpdateWrapper<Share>()
                                .eq(Share::getId, share.getId())
                                .set(Share::getDeleted, 1));
                count++;
            }
        }
        return count;
    }

    // ================================================================
    // 4. 孤儿文档：folderId 指向已删目录 → 移至根目录
    // ================================================================

    /**
     * 笔记的 folderId 指向已删目录的，移至根目录（folderId=0）
     */
    private int fixOrphanDocs() {
        int count = docMapper.update(null,
                new LambdaUpdateWrapper<Doc>()
                        .notInSql(Doc::getFolderId, "SELECT id FROM folder WHERE deleted=0")
                        .set(Doc::getFolderId, 0));
        if (count > 0) {
            log.info("[孤儿文档] 已将 {} 篇笔记移至根目录", count);
        }
        return count;
    }

    /**
     * 网页的 folderId 指向已删目录的，移至根目录
     */
    private int fixOrphanWebPages() {
        int count = webPageMapper.update(null,
                new LambdaUpdateWrapper<WebPage>()
                        .notInSql(WebPage::getFolderId, "SELECT id FROM folder WHERE deleted=0")
                        .set(WebPage::getFolderId, 0));
        if (count > 0) {
            log.info("[孤儿文档] 已将 {} 个网页移至根目录", count);
        }
        return count;
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /**
     * 检查指定文档 ID 是否存在于 MeiliSearch 索引中
     */
    private boolean docExistsInIndex(String indexName, Long id) {
        try {
            Index index = meiliSearchClient.index(indexName);
            String doc = index.getDocument(String.valueOf(id), String.class);
            return doc != null && !doc.isEmpty() && !doc.equals("null");
        } catch (MeilisearchException e) {
            // 文档不存在会抛异常
            return false;
        }
    }
}
