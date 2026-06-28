package com.kb.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KbEvent 跨服务事件单元测试
 */
@DisplayName("KbEvent 跨服务事件单元测试")
class KbEventTest {

    @Test
    @DisplayName("全参构造_设置event/entityId/payload且timestamp非空")
    void allArgsConstructor_setsFieldsAndTimestamp() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");

        KbEvent event = new KbEvent(KbEvent.FILE_PARSED, 100L, payload);

        assertEquals(KbEvent.FILE_PARSED, event.getEvent());
        assertEquals(100L, event.getEntityId());
        assertSame(payload, event.getPayload());
        assertNotNull(event.getTimestamp());
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
    @DisplayName("全参构造_payload为null_字段保留null但timestamp已设置")
    void allArgsConstructor_nullPayload_keepsNullButSetsTimestamp() {
        KbEvent event = new KbEvent("file.deleted", 2L, null);

        assertEquals("file.deleted", event.getEvent());
        assertEquals(2L, event.getEntityId());
        assertNull(event.getPayload());
        assertNotNull(event.getTimestamp());
        assertNull(event.getSource());
    }

    @Test
    @DisplayName("全参构造_entityId为null_字段保留null")
    void allArgsConstructor_nullEntityId_keepsNull() {
        KbEvent event = new KbEvent("file.reparse", null, null);

        assertEquals("file.reparse", event.getEvent());
        assertNull(event.getEntityId());
    }

    @Test
    @DisplayName("无参构造_所有字段为默认值")
    void noArgsConstructor_allFieldsDefault() {
        KbEvent event = new KbEvent();

        assertNull(event.getEvent());
        assertNull(event.getEntityId());
        assertNull(event.getPayload());
        assertNull(event.getTimestamp());
        assertNull(event.getSource());
    }

    @Test
    @DisplayName("setter_设置所有字段_getter返回正确值")
    void setters_setAllFields() {
        KbEvent event = new KbEvent();
        Map<String, Object> payload = Map.of("k", "v");

        event.setEvent(KbEvent.OPS_IMPORTED);
        event.setEntityId(99L);
        event.setPayload(payload);
        event.setTimestamp(Instant.parse("2026-01-01T00:00:00Z"));
        event.setSource("kb-file");

        assertEquals(KbEvent.OPS_IMPORTED, event.getEvent());
        assertEquals(99L, event.getEntityId());
        assertSame(payload, event.getPayload());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), event.getTimestamp());
        assertEquals("kb-file", event.getSource());
    }

    @Test
    @DisplayName("常量_事件类型值正确")
    void constants_haveExpectedValues() {
        assertEquals("file.parsed", KbEvent.FILE_PARSED);
        assertEquals("file.deleted", KbEvent.FILE_DELETED);
        assertEquals("file.reparse", KbEvent.FILE_REPARSE);
        assertEquals("ops.imported", KbEvent.OPS_IMPORTED);
        assertEquals("ops.conflict", KbEvent.OPS_CONFLICT);
    }

    @Test
    @DisplayName("全参构造_使用所有事件类型常量_构造成功")
    void allArgsConstructor_withAllEventConstants_constructsSuccessfully() {
        assertDoesNotThrow(() -> new KbEvent(KbEvent.FILE_PARSED, 1L, null));
        assertDoesNotThrow(() -> new KbEvent(KbEvent.FILE_DELETED, 2L, null));
        assertDoesNotThrow(() -> new KbEvent(KbEvent.FILE_REPARSE, 3L, null));
        assertDoesNotThrow(() -> new KbEvent(KbEvent.OPS_IMPORTED, 4L, null));
        assertDoesNotThrow(() -> new KbEvent(KbEvent.OPS_CONFLICT, 5L, null));
    }
}
