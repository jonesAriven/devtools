package com.kb.knowledge.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.common.event.KbEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
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
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        redisMessageListenerContainer.addMessageListener(this, new PatternTopic("kb:events"));
        log.info("IndexEventListener 已订阅 kb:events 通道");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            KbEvent event = objectMapper.readValue(body, KbEvent.class);
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
        log.info("处理文件删除事件 fileId={}", event.getEntityId());
        // 分享失效标记可由定时补偿任务完成（ConsistencyCheckTask）
    }

    /**
     * 文件重新解析事件
     */
    private void handleFileReparse(KbEvent event) {
        log.info("处理文件重新解析事件 fileId={}", event.getEntityId());
    }
}
