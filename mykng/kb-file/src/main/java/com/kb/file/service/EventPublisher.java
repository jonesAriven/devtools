package com.kb.file.service;

import com.kb.common.event.EventBus;
import com.kb.common.event.KbEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 跨服务事件发布器（M4 重构：薄包装层，实际发布委托给 EventBus）
 * <p>
 * 保留业务方法名（publishFileParsed 等），便于业务代码语义化调用。
 * 底层通过 Redis Streams 发布（持久化 + 消费者组 + ACK）。
 * <p>
 * 事件流：kb:streams:file-events
 * 订阅方：kb-knowledge（GROUP_KNOWLEDGE）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final EventBus eventBus;

    /**
     * 发布文件解析完成事件
     */
    public void publishFileParsed(Long fileId, Long userId, String name, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fileId", fileId);
        payload.put("userId", userId);
        payload.put("name", name);
        payload.put("content", content);
        publish(KbEvent.FILE_PARSED, fileId, payload);
    }

    /**
     * 发布文件删除事件（逻辑删除）
     */
    public void publishFileDeleted(Long fileId, Long userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fileId", fileId);
        payload.put("userId", userId);
        publish(KbEvent.FILE_DELETED, fileId, payload);
    }

    /**
     * 发布文件重新解析事件
     */
    public void publishFileReparse(Long fileId, Long userId, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fileId", fileId);
        payload.put("userId", userId);
        payload.put("content", content);
        publish(KbEvent.FILE_REPARSE, fileId, payload);
    }

    /**
     * 发布文件永久删除事件（M4 新增）
     */
    public void publishFilePermanentDeleted(Long fileId, Long userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fileId", fileId);
        payload.put("userId", userId);
        publish(KbEvent.FILE_PERMANENT_DELETED, fileId, payload);
    }

    /**
     * 发布回收站清空事件（M4 新增）
     */
    public void publishFileTrashEmptied(Long userId, int count) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("count", count);
        publish(KbEvent.FILE_TRASH_EMPTIED, null, payload);
    }

    /**
     * 统一发布方法
     */
    private void publish(String eventType, Long entityId, Map<String, Object> payload) {
        KbEvent event = new KbEvent(eventType, entityId, payload, "kb-file");
        eventBus.publish(event);
    }
}
