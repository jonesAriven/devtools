package com.kb.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KbEvent v2 跨服务事件单元测试
 */
@DisplayName("KbEvent v2 跨服务事件单元测试")
class KbEventTest {

    @Test
    @DisplayName("全参构造_设置event/entityId/payload且timestamp/eventId非空")
    void allArgsConstructor_setsFieldsAndTimestamp() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");

        KbEvent event = new KbEvent(KbEvent.FILE_PARSED, 100L, payload);

        assertEquals(KbEvent.FILE_PARSED, event.getEvent());
        assertEquals(100L, event.getEntityId());
        assertSame(payload, event.getPayload());
        assertNotNull(event.getTimestamp());
        assertNotNull(event.getEventId(), "v2 新增：eventId 应自动生成");
        assertEquals("v2", event.getVersion(), "v2 新增：version 默认 v2");
    }

    @Test
    @DisplayName("全参构造_指定source_设置source字段")
    void allArgsConstructor_withSource_setsSource() {
        KbEvent event = new KbEvent(KbEvent.FILE_DELETED, 1L, null, "kb-file");

        assertEquals("kb-file", event.getSource());
        assertNotNull(event.getEventId());
    }

    @Test
    @DisplayName("全参构造_timestamp接近当前时间")
    void allArgsConstructor_timestampCloseToNow() {
        Instant before = Instant.now();

        KbEvent event = new KbEvent("file.parsed", 1L, null);

        Instant after = Instant.now();
        assertNotNull(event.getTimestamp());
        assertTrue(!event.getTimestamp().isBefore(before));
        assertTrue(!event.getTimestamp().isAfter(after));
    }

    @Test
    @DisplayName("全参构造_payload为null_字段保留null但timestamp和eventId已设置")
    void allArgsConstructor_nullPayload_keepsNullButSetsTimestamp() {
        KbEvent event = new KbEvent("file.deleted", 2L, null);

        assertEquals("file.deleted", event.getEvent());
        assertEquals(2L, event.getEntityId());
        assertNull(event.getPayload());
        assertNotNull(event.getTimestamp());
        assertNull(event.getSource());
        assertNotNull(event.getEventId(), "v2 新增：payload 为 null 时 eventId 仍应生成");
    }

    @Test
    @DisplayName("全参构造_entityId为null_字段保留null")
    void allArgsConstructor_nullEntityId_keepsNull() {
        KbEvent event = new KbEvent("file.reparse", null, null);

        assertEquals("file.reparse", event.getEvent());
        assertNull(event.getEntityId());
    }

    @Test
    @DisplayName("无参构造_event/entityId/payload/timestamp/source为null但version默认v2")
    void noArgsConstructor_allFieldsDefault() {
        KbEvent event = new KbEvent();

        assertNull(event.getEvent());
        assertNull(event.getEntityId());
        assertNull(event.getPayload());
        assertNull(event.getTimestamp());
        assertNull(event.getSource());
        assertNull(event.getEventId());
        assertEquals("v2", event.getVersion(), "v2 新增：无参构造 version 默认 v2");
    }

    @Test
    @DisplayName("setter_设置所有字段_getter返回正确值")
    void setters_setAllFields() {
        KbEvent event = new KbEvent();
        Map<String, Object> payload = Map.of("k", "v");

        event.setEvent(KbEvent.FILE_REPARSE);
        event.setEntityId(99L);
        event.setPayload(payload);
        event.setTimestamp(Instant.parse("2026-01-01T00:00:00Z"));
        event.setSource("kb-file");
        event.setEventId("evt-123");
        event.setVersion("v3");
        event.setTraceId("trace-abc");

        assertEquals(KbEvent.FILE_REPARSE, event.getEvent());
        assertEquals(99L, event.getEntityId());
        assertSame(payload, event.getPayload());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), event.getTimestamp());
        assertEquals("kb-file", event.getSource());
        assertEquals("evt-123", event.getEventId());
        assertEquals("v3", event.getVersion());
        assertEquals("trace-abc", event.getTraceId());
    }

    @Test
    @DisplayName("常量_文件事件类型值正确")
    void constants_fileEventTypesCorrect() {
        assertEquals("file.parsed", KbEvent.FILE_PARSED);
        assertEquals("file.deleted", KbEvent.FILE_DELETED);
        assertEquals("file.reparse", KbEvent.FILE_REPARSE);
        assertEquals("file.permanent_deleted", KbEvent.FILE_PERMANENT_DELETED);
        assertEquals("file.trash_emptied", KbEvent.FILE_TRASH_EMPTIED);
    }

    @Test
    @DisplayName("常量_知识库事件类型值正确")
    void constants_knowledgeEventTypesCorrect() {
        assertEquals("doc.created", KbEvent.DOC_CREATED);
        assertEquals("doc.updated", KbEvent.DOC_UPDATED);
        assertEquals("doc.deleted", KbEvent.DOC_DELETED);
        assertEquals("web.collected", KbEvent.WEB_COLLECTED);
        assertEquals("web.deleted", KbEvent.WEB_DELETED);
        assertEquals("share.created", KbEvent.SHARE_CREATED);
        assertEquals("share.deleted", KbEvent.SHARE_DELETED);
        assertEquals("folder.created", KbEvent.FOLDER_CREATED);
        assertEquals("folder.deleted", KbEvent.FOLDER_DELETED);
        assertEquals("space.created", KbEvent.SPACE_CREATED);
        assertEquals("space.deleted", KbEvent.SPACE_DELETED);
        assertEquals("tag.created", KbEvent.TAG_CREATED);
        assertEquals("tag.deleted", KbEvent.TAG_DELETED);
    }

    @Test
    @DisplayName("常量_事件流通道和消费者组正确")
    void constants_streamsAndGroupsCorrect() {
        assertEquals("kb:streams:file-events", KbEvent.STREAM_FILE_EVENTS);
        assertEquals("kb:streams:knowledge-events", KbEvent.STREAM_KNOWLEDGE_EVENTS);
        assertEquals("kb:streams:share-events", KbEvent.STREAM_SHARE_EVENTS);
        assertEquals("kb:streams:dead-letter", KbEvent.STREAM_DEAD_LETTER);
        assertEquals("kb-knowledge-group", KbEvent.GROUP_KNOWLEDGE);
        assertEquals("kb-intelligence-group", KbEvent.GROUP_INTELLIGENCE);
    }

    @Test
    @DisplayName("全参构造_使用所有事件类型常量_构造成功")
    void allArgsConstructor_withAllEventConstants_constructsSuccessfully() {
        assertDoesNotThrow(() -> new KbEvent(KbEvent.FILE_PARSED, 1L, null));
        assertDoesNotThrow(() -> new KbEvent(KbEvent.FILE_DELETED, 2L, null));
        assertDoesNotThrow(() -> new KbEvent(KbEvent.FILE_REPARSE, 3L, null));
        assertDoesNotThrow(() -> new KbEvent(KbEvent.FILE_PERMANENT_DELETED, 4L, null));
        assertDoesNotThrow(() -> new KbEvent(KbEvent.FILE_TRASH_EMPTIED, null, null));
        assertDoesNotThrow(() -> new KbEvent(KbEvent.DOC_CREATED, 5L, null));
        assertDoesNotThrow(() -> new KbEvent(KbEvent.SHARE_CREATED, 6L, null));
    }

    @Test
    @DisplayName("每次构造_eventId唯一")
    void allArgsConstructor_eventIdUnique() {
        KbEvent e1 = new KbEvent("test", 1L, null);
        KbEvent e2 = new KbEvent("test", 1L, null);

        assertNotEquals(e1.getEventId(), e2.getEventId(), "eventId 应唯一");
    }
}
