package com.kb.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * kb-ops 启动类冒烟测试（不加载完整 Spring 上下文，避免依赖外部 MySQL/Redis）。
 * 完整上下文测试需要真实基础设施，见各 Service 单元测试。
 */
class KbOpsApplicationTests {

    @Test
    void applicationClassExists() {
        assertNotNull(KbOpsApplication.class);
        assertNotNull(KbOpsApplication.class.getSimpleName());
    }
}
