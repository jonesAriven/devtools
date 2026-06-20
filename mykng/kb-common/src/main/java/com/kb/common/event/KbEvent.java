package com.kb.common.event;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 跨服务事件通知（P1 新增）
 * <p>
 * 通过 Redis Pub/Sub 传递，用于服务间异步通知。
 * 如：kb-file 解析完成 → 通知 kb-knowledge 建索引
 */
@Data
@NoArgsConstructor
public class KbEvent {

    private String event;       // 事件类型: file.parsed, file.deleted, ops.imported
    private Long entityId;      // 关联实体 ID
    private Map<String, Object> payload;  // 附加数据
    private Instant timestamp;  // 事件时间
    private String source;      // 事件来源服务名

    public KbEvent(String event, Long entityId, Map<String, Object> payload) {
        this.event = event;
        this.entityId = entityId;
        this.payload = payload;
        this.timestamp = Instant.now();
    }

    // 常用事件类型
    public static final String FILE_PARSED = "file.parsed";
    public static final String FILE_DELETED = "file.deleted";
    public static final String FILE_REPARSE = "file.reparse";
    public static final String OPS_IMPORTED = "ops.imported";
    public static final String OPS_CONFLICT = "ops.conflict";
}
