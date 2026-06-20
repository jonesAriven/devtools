package com.kb.file.service;

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
 * 通过 Redis Pub/Sub 发布事件，kb-knowledge 等下游服务订阅后更新索引等。
 * 事件通道: kb:events
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${kb.event.channel:kb:events}")
    private String channel;

    /**
     * 发布文件解析完成事件
     */
    public void publishFileParsed(Long fileId, Long userId, String name, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fileId", fileId);
        payload.put("userId", userId);
        payload.put("name", name);
        payload.put("content", content);
        KbEvent event = new KbEvent(KbEvent.FILE_PARSED, fileId, payload);
        event.setSource("kb-file");
        publish(event);
    }

    /**
     * 发布文件删除事件
     */
    public void publishFileDeleted(Long fileId, Long userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fileId", fileId);
        payload.put("userId", userId);
        KbEvent event = new KbEvent(KbEvent.FILE_DELETED, fileId, payload);
        event.setSource("kb-file");
        publish(event);
    }

    /**
     * 发布文件重新解析事件
     */
    public void publishFileReparse(Long fileId, Long userId, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fileId", fileId);
        payload.put("userId", userId);
        payload.put("content", content);
        KbEvent event = new KbEvent(KbEvent.FILE_REPARSE, fileId, payload);
        event.setSource("kb-file");
        publish(event);
    }

    private void publish(KbEvent event) {
        try {
            redisTemplate.convertAndSend(channel, event);
            log.info("发布事件: {} entityId={}", event.getEvent(), event.getEntityId());
        } catch (Exception e) {
            log.error("发布事件失败: {} entityId={}", event.getEvent(), event.getEntityId(), e);
        }
    }
}
