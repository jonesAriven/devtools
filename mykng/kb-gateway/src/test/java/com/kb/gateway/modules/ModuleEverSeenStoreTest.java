package com.kb.gateway.modules;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModuleEverSeenStore} 的纯逻辑测试（不需要 Redis）。
 *
 * <p>这里测的是「剪掉过期记录」——它是 everSeen 唯一有分支的逻辑，也是唯一可能让
 * 已改名模块（kb-auth）永久占位的地方。Redis 的读写本身是框架行为，不在此覆盖。
 */
class ModuleEverSeenStoreTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long TTL = 30L * 24 * 3600 * 1000;   // 30 天

    @Test
    void 未过期的记录应保留() {
        Map<String, Long> raw = Map.of("auth-center", NOW - 1000, "kb-file", NOW);
        Map<String, Long> kept = ModuleEverSeenStore.pruneExpired(raw, NOW, TTL);
        assertEquals(2, kept.size());
        assertTrue(kept.containsKey("auth-center"));
    }

    @Test
    void 超过TTL的记录应被剪掉() {
        Map<String, Long> raw = Map.of(
                "kb-file", NOW,                       // 刚见过 → 留
                "kb-auth", NOW - TTL - 1);            // 超过 TTL → 剪
        Map<String, Long> kept = ModuleEverSeenStore.pruneExpired(raw, NOW, TTL);
        assertEquals(1, kept.size());
        assertTrue(kept.containsKey("kb-file"));
        assertFalse(kept.containsKey("kb-auth"));
    }

    @Test
    void 恰好等于TTL边界应保留() {
        Map<String, Long> raw = Map.of("kb-file", NOW - TTL);
        Map<String, Long> kept = ModuleEverSeenStore.pruneExpired(raw, NOW, TTL);
        assertEquals(1, kept.size(), "now - last <= ttl 应算未过期");
    }

    @Test
    void 空输入与null都应安全返回空() {
        assertTrue(ModuleEverSeenStore.pruneExpired(Map.of(), NOW, TTL).isEmpty());
        assertTrue(ModuleEverSeenStore.pruneExpired(null, NOW, TTL).isEmpty());
    }

    @Test
    void 解析不出毫秒数的值应按0处理并被剪掉() {
        assertEquals(0L, ModuleEverSeenStore.parseMillis(null));
        assertEquals(0L, ModuleEverSeenStore.parseMillis("not-a-number"));
        assertEquals(123L, ModuleEverSeenStore.parseMillis("123"));
        assertEquals(123L, ModuleEverSeenStore.parseMillis(123L));
        // 0 会被当作"很久很久以前"，必然过期
        assertTrue(ModuleEverSeenStore.pruneExpired(Map.of("x", 0L), NOW, TTL).isEmpty());
    }
}
