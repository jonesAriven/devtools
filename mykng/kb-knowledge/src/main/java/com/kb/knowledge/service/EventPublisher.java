package com.kb.knowledge.service;

import com.kb.common.event.KbEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 跨服务事件发布器（替代原单体中的 OperationLogService）
 * <p>
 * 通过 Redis Pub/Sub 发布知识库操作事件，替代直接写 operation_log 表。
 * 事件通道: kb:events
 * 事件类型: doc.created, doc.updated, doc.deleted, web.collected, web.deleted,
 *          share.created, share.deleted, folder.created, folder.deleted, etc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${kb.event.channel:kb:events}")
    private String channel;

    // 知识库事件类型常量
    public static final String DOC_CREATED = "doc.created";
    public static final String DOC_UPDATED = "doc.updated";
    public static final String DOC_DELETED = "doc.deleted";
    public static final String WEB_COLLECTED = "web.collected";
    public static final String WEB_DELETED = "web.deleted";
    public static final String SHARE_CREATED = "share.created";
    public static final String SHARE_DELETED = "share.deleted";
    public static final String FOLDER_CREATED = "folder.created";
    public static final String FOLDER_DELETED = "folder.deleted";
    public static final String SPACE_CREATED = "space.created";
    public static final String SPACE_DELETED = "space.deleted";
    public static final String TAG_CREATED = "tag.created";
    public static final String TAG_DELETED = "tag.deleted";

    /**
     * 发布知识库操作事件（替代 operationLogService.log）
     *
     * @param userId       操作用户 ID
     * @param action       操作类型（CREATE/MODIFY/DELETE/SHARE/COLLECT 等）
     * @param resourceType 资源类型（doc/web/folder/share/space/tag）
     * @param resourceId   资源 ID
     * @param detail       操作详情
     */
    public void publishKnowledgeEvent(Long userId, String action, String resourceType,
                                      Long resourceId, String detail) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("action", action);
        payload.put("resourceType", resourceType);
        payload.put("resourceId", resourceId);
        payload.put("detail", detail);

        String eventType = resourceType + "." + action.toLowerCase();
        KbEvent event = new KbEvent(eventType, resourceId, payload);
        event.setSource("kb-knowledge");
        publish(event);
    }

    private void publish(KbEvent event) {
        try {
            redisTemplate.convertAndSend(channel, event);
            log.info("发布知识库事件: {} entityId={}", event.getEvent(), event.getEntityId());
        } catch (Exception e) {
            log.error("发布知识库事件失败: {} entityId={}", event.getEvent(), event.getEntityId(), e);
        }
    }
}
