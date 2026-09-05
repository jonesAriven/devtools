package com.kb.knowledge.event;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.marschat.common.event.AbstractEventConsumer;
import com.marschat.common.event.AppEvent;
import com.kb.knowledge.entity.Share;
import com.kb.knowledge.mapper.ShareMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 跨服务事件消费者（M4-M5 重构：基于 Redis Streams）
 * <p>
 * 订阅 kb:streams:file-events，处理来自 kb-file 的事件通知。
 * 消费者组：kb-knowledge-group
 * <p>
 * 处理事件：
 * - file.parsed: 文件解析完成（记录日志）
 * - file.deleted: 文件逻辑删除（标记关联分享失效）
 * - file.permanent_deleted: 文件永久删除（标记关联分享失效）M4 新增
 * - file.trash_emptied: 回收站清空（批量标记关联分享失效）M4 新增
 * - file.reparse: 文件重新解析（记录日志）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexEventListener extends AbstractEventConsumer {

    private final ShareMapper shareMapper;

    @Override
    public String getStream() {
        return AppEvent.STREAM_FILE_EVENTS;
    }

    @Override
    public String getGroup() {
        return AppEvent.GROUP_KNOWLEDGE;
    }

    @Override
    public String getConsumer() {
        return "kb-knowledge-1";
    }

    @Override
    public void handleEvent(AppEvent event) {
        log.info("处理跨服务事件: {} entityId={}", event.getEvent(), event.getEntityId());

        switch (event.getEvent()) {
            case AppEvent.FILE_PARSED -> handleFileParsed(event);
            case AppEvent.FILE_DELETED -> handleFileDeleted(event);
            case AppEvent.FILE_PERMANENT_DELETED -> handleFilePermanentDeleted(event);
            case AppEvent.FILE_TRASH_EMPTIED -> handleFileTrashEmptied(event);
            case AppEvent.FILE_REPARSE -> handleFileReparse(event);
            default -> log.debug("忽略非相关事件: {}", event.getEvent());
        }
    }

    /**
     * 文件解析完成事件
     * kb-file 解析完成后通知，kb-knowledge 可用于关联笔记等一致性维护
     */
    private void handleFileParsed(AppEvent event) {
        log.info("处理文件解析完成事件 fileId={}", event.getEntityId());
        // 文件索引由 kb-file 自身维护（kb_files 索引），此处仅记录日志
    }

    /**
     * 文件删除事件（逻辑删除）
     * 文件被删除时，需标记关联的分享为失效
     */
    private void handleFileDeleted(AppEvent event) {
        markSharesInvalid(event.getEntityId());
    }

    /**
     * 文件永久删除事件（M4 新增）
     * 文件被永久删除时，标记关联的分享为失效
     */
    private void handleFilePermanentDeleted(AppEvent event) {
        markSharesInvalid(event.getEntityId());
    }

    /**
     * 回收站清空事件（M4 新增）
     * 批量标记该用户所有文件类型的分享为失效
     */
    private void handleFileTrashEmptied(AppEvent event) {
        if (event.getPayload() == null) {
            return;
        }
        Object userIdObj = event.getPayload().get("userId");
        if (userIdObj == null) {
            return;
        }
        Long userId = Long.parseLong(userIdObj.toString());
        int updated = shareMapper.update(null,
                new LambdaUpdateWrapper<Share>()
                        .eq(Share::getUserId, userId)
                        .eq(Share::getResourceType, "file")
                        .set(Share::getDeleted, 1));
        if (updated > 0) {
            log.info("[分享失效] 回收站清空导致 {} 条分享已标记失效 userId={}", updated, userId);
        }
    }

    /**
     * 文件重新解析事件
     */
    private void handleFileReparse(AppEvent event) {
        log.info("处理文件重新解析事件 fileId={}", event.getEntityId());
    }

    /**
     * 标记关联分享为失效
     */
    private void markSharesInvalid(Long fileId) {
        if (fileId == null) {
            return;
        }
        int updated = shareMapper.update(null,
                new LambdaUpdateWrapper<Share>()
                        .eq(Share::getResourceId, fileId)
                        .eq(Share::getResourceType, "file")
                        .set(Share::getDeleted, 1));
        if (updated > 0) {
            log.info("[分享失效] 文件删除导致 {} 条分享已标记失效 fileId={}", updated, fileId);
        }
    }
}
