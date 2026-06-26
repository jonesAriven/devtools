package com.kb.knowledge.event;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.common.event.KbEvent;
import com.kb.knowledge.entity.Share;
import com.kb.knowledge.mapper.ShareMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 跨服务事件监听器
 * <p>
 * 订阅 Redis Pub/Sub 通道 kb:events，处理来自其他服务的事件通知。
 * 主要处理 kb-file 发布的文件解析完成/删除事件（用于一致性维护等）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexEventListener implements MessageListener {

    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ShareMapper shareMapper;

    @PostConstruct
    public void init() {
        redisMessageListenerContainer.addMessageListener(this, new PatternTopic("kb:events"));
        log.info("IndexEventListener 已订阅 kb:events 通道");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 使用 RedisTemplate 的 value serializer 反序列化（与发布端 EventPublisher 一致）
            // RedisTemplate 配置了 activateDefaultTyping，序列化/反序列化格式完全匹配
            Object obj = redisTemplate.getValueSerializer().deserialize(message.getBody());
            if (obj == null || !(obj instanceof KbEvent event)) {
                log.warn("收到非 KbEvent 类型的消息: {}", obj == null ? "null" : obj.getClass().getSimpleName());
                return;
            }
            log.info("收到跨服务事件: {} entityId={}", event.getEvent(), event.getEntityId());

            switch (event.getEvent()) {
                case KbEvent.FILE_PARSED -> handleFileParsed(event);
                case KbEvent.FILE_DELETED -> handleFileDeleted(event);
                case KbEvent.FILE_REPARSE -> handleFileReparse(event);
                default -> log.debug("忽略非相关事件: {}", event.getEvent());
            }
        } catch (Exception e) {
            log.error("处理跨服务事件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 文件解析完成事件
     * kb-file 解析完成后通知，kb-knowledge 可用于关联笔记等一致性维护
     */
    private void handleFileParsed(KbEvent event) {
        log.info("处理文件解析完成事件 fileId={}", event.getEntityId());
        // 文件索引由 kb-file 自身维护（kb_files 索引），此处仅记录日志
    }

    /**
     * 文件删除事件
     * 文件被删除时，需标记关联的分享为失效
     */
    private void handleFileDeleted(KbEvent event) {
        Long fileId = event.getEntityId();
        log.info("处理文件删除事件 fileId={}", fileId);
        int updated = shareMapper.update(null,
                new LambdaUpdateWrapper<Share>()
                        .eq(Share::getResourceId, fileId)
                        .eq(Share::getResourceType, "file")
                        .set(Share::getDeleted, 1));
        if (updated > 0) {
            log.info("[分享失效] 文件删除导致 {} 条分享已标记失效 fileId={}", updated, fileId);
        }
    }

    /**
     * 文件重新解析事件
     */
    private void handleFileReparse(KbEvent event) {
        log.info("处理文件重新解析事件 fileId={}", event.getEntityId());
    }
}
