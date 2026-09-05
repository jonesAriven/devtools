package com.kb.knowledge.service;

import com.marschat.common.event.EventBus;
import com.marschat.common.event.AppEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 跨服务事件发布器（M4 重构：薄包装层，实际发布委托给 EventBus）
 * <p>
 * 保留业务方法名（publishKnowledgeEvent），便于业务代码语义化调用。
 * 底层通过 Redis Streams 发布（持久化 + 消费者组 + ACK）。
 * <p>
 * 事件流：kb:streams:knowledge-events（doc/web/folder/space/tag 事件）
 *         kb:streams:share-events（share 事件）
 * 订阅方：kb-intelligence（GROUP_INTELLIGENCE）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final EventBus eventBus;

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
        AppEvent event = new AppEvent(eventType, resourceId, payload, "kb-knowledge");
        eventBus.publish(event);
    }
}
