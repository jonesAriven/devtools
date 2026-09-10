package com.kb.gateway.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModuleState#resolve} 真值表测试。
 * <p>
 * 为什么单独写这个测试：{@code resolve} 是 Phase 0.5「消灭静默失败」改造的核心语义——
 * 它决定了一个模块该被判成 OK / DOWN / MISSING / UNEXPECTED，直接决定运维收到的排障指引
 * 是"重启服务"还是"改配置"。这块逻辑原本没有任何测试覆盖，两处语义缺陷（跨重启误判、
 * 期望集外的野生名字被判成宕机）都是靠人工审查发现的。抽成纯函数后即可用真值表锁死，
 * 且不需要启动 Spring、不需要连 Nacos，能在 CI 的注册表门禁同一条命令里跑。
 */
class ModuleStateTest {

    // ---------------------------------------------------------- 有实例

    @Test
    @DisplayName("期望集内且有实例 → OK（与 inList / everSeen 无关）")
    void expectedAndAliveIsOk() {
        assertEquals(ModuleState.OK, ModuleState.resolve(true, 1, true, true));
        assertEquals(ModuleState.OK, ModuleState.resolve(true, 3, true, false));
        assertTrue(ModuleState.OK.isAvailable());
    }

    @Test
    @DisplayName("期望集外但有实例 → UNEXPECTED，仍对用户可用（野模块）")
    void unexpectedWhenAliveButNotDeclared() {
        assertEquals(ModuleState.UNEXPECTED, ModuleState.resolve(false, 1, true, true));
        assertTrue(ModuleState.UNEXPECTED.isAvailable());
    }

    // ---------------------------------------------------------- 期望集内、零实例

    @Test
    @DisplayName("期望集内、零实例、本进程见过 → DOWN（服务下线，运维事件）")
    void expectedAndWasAliveIsDown() {
        assertEquals(ModuleState.DOWN, ModuleState.resolve(true, 0, false, true));
        assertFalse(ModuleState.DOWN.isAvailable());
    }

    @Test
    @DisplayName("期望集内、零实例、Nacos 列表仍有名字 → DOWN（注册痕迹还在）")
    void expectedWithResidualEntryIsDown() {
        assertEquals(ModuleState.DOWN, ModuleState.resolve(true, 0, true, false));
        assertFalse(ModuleState.DOWN.isAvailable());
    }

    @Test
    @DisplayName("期望集内、零实例、无任何痕迹 → MISSING（名字对不上）")
    void expectedWithNoTraceIsMissing() {
        assertEquals(ModuleState.MISSING, ModuleState.resolve(true, 0, false, false));
        assertFalse(ModuleState.MISSING.isAvailable());
    }

    // ---------------------------------------------------------- 期望集外、零实例（本次修正点）

    @Test
    @DisplayName("期望集外、零实例、Nacos 列表仍有名字 → DOWN（残留条目，曾注册过）")
    void notExpectedWithResidualEntryIsDown() {
        assertEquals(ModuleState.DOWN, ModuleState.resolve(false, 0, true, false));
        assertFalse(ModuleState.DOWN.isAvailable());
    }

    @Test
    @DisplayName("期望集外、零实例、Nacos 里毫无痕迹 → MISSING（查无此注册），不再是误导性的 DOWN")
    void notExpectedWithNoTraceIsMissingNotDown() {
        // 本次修正点：任意野生名字（前端 kb-web、已删除的 kb-auth、随手拼错的名字）
        // 过去一律返回 DOWN，等于对运维说"这个服务宕机了"——但它可能压根不是被探活的服务。
        assertEquals(ModuleState.MISSING, ModuleState.resolve(false, 0, false, false));
        assertFalse(ModuleState.MISSING.isAvailable());
    }

    // ---------------------------------------------------------- 兼容映射

    @Test
    @DisplayName("UNKNOWN 视为可用：Nacos 整体抖动不能把全部菜单判成不可用")
    void unknownIsAvailableToAvoidHidingEverything() {
        assertTrue(ModuleState.UNKNOWN.isAvailable());
        assertEquals("UNKNOWN", ModuleState.UNKNOWN.toLegacyStatus());
    }

    @Test
    @DisplayName("旧 status 字段兼容映射：OK/UNEXPECTED → UP，DOWN → DOWN，MISSING/UNKNOWN → UNKNOWN")
    void legacyStatusMapping() {
        assertEquals("UP", ModuleState.OK.toLegacyStatus());
        assertEquals("UP", ModuleState.UNEXPECTED.toLegacyStatus());
        assertEquals("DOWN", ModuleState.DOWN.toLegacyStatus());
        assertEquals("UNKNOWN", ModuleState.MISSING.toLegacyStatus());
        assertEquals("UNKNOWN", ModuleState.UNKNOWN.toLegacyStatus());
    }
}
