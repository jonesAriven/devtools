package com.kb.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KbGatewayProperties 单元测试。
 * <p>
 * 覆盖：
 * <ol>
 *   <li>白名单路径匹配逻辑（使用与 JwtAuthFilter 相同的 {@link AntPathMatcher}）</li>
 *   <li>配置加载（默认值 + setter/getter）</li>
 * </ol>
 */
@DisplayName("网关配置属性测试")
class KbGatewayPropertiesTest {

    /** 与 JwtAuthFilter 中相同的路径匹配器 */
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** 构造一份与 application.yml 中一致的标准配置 */
    private KbGatewayProperties buildDefault() {
        KbGatewayProperties props = new KbGatewayProperties();
        props.setContextPath("/kb");
        props.getJwt().setSecret("YourSuperSecretKeyForJwtTokenGenerationMustBe256BitsLong!!");
        props.getJwt().setHeader("Authorization");
        props.setWhitelist(Arrays.asList(
                "/kb/api/auth/login",
                "/kb/api/auth/refresh",
                "/kb/api/share/verify/**",
                "/kb/api/share/detail/**"
        ));
        return props;
    }

    /** 复用 JwtAuthFilter 的白名单匹配算法 */
    private boolean isWhitelisted(KbGatewayProperties props, String path) {
        List<String> whitelist = props.getWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        for (String pattern : whitelist) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // 白名单路径匹配逻辑
    // ============================================================

    @Test
    @DisplayName("白名单 - 精确路径匹配成功")
    void whitelist_exactPath_matches() {
        KbGatewayProperties props = buildDefault();
        assertTrue(isWhitelisted(props, "/kb/api/auth/login"));
        assertTrue(isWhitelisted(props, "/kb/api/auth/refresh"));
    }

    @Test
    @DisplayName("白名单 - Ant 通配符路径匹配成功")
    void whitelist_wildcardPath_matches() {
        KbGatewayProperties props = buildDefault();
        assertTrue(isWhitelisted(props, "/kb/api/share/verify/abc123"));
        assertTrue(isWhitelisted(props, "/kb/api/share/verify/long/path/segment"));
        assertTrue(isWhitelisted(props, "/kb/api/share/detail/xyz-456"));
    }

    @Test
    @DisplayName("白名单 - 非白名单路径不匹配")
    void whitelist_nonWhitelistPath_notMatches() {
        KbGatewayProperties props = buildDefault();
        assertFalse(isWhitelisted(props, "/kb/api/doc/list"));
        assertFalse(isWhitelisted(props, "/kb/api/user/info"));
        assertFalse(isWhitelisted(props, "/kb/api/auth/logout"));
        assertFalse(isWhitelisted(props, "/kb/api/share/list"));
    }

    @Test
    @DisplayName("白名单 - 路径前缀相似但非精确匹配")
    void whitelist_similarPrefix_notMatches() {
        KbGatewayProperties props = buildDefault();
        // loginX 不是 login
        assertFalse(isWhitelisted(props, "/kb/api/auth/loginX"));
        // refresh 子路径不匹配（精确匹配只匹配自身）
        assertFalse(isWhitelisted(props, "/kb/api/auth/refresh/sub"));
    }

    @Test
    @DisplayName("白名单为空 - 任何路径都不匹配")
    void whitelist_empty_matchesNothing() {
        KbGatewayProperties props = new KbGatewayProperties();
        assertFalse(isWhitelisted(props, "/kb/api/auth/login"));
        assertFalse(isWhitelisted(props, "/kb/api/any/path"));
    }

    @Test
    @DisplayName("白名单为 null - 安全降级为不匹配")
    void whitelist_null_matchesNothing() {
        KbGatewayProperties props = new KbGatewayProperties();
        props.setWhitelist(null);
        assertFalse(isWhitelisted(props, "/kb/api/auth/login"));
    }

    // ============================================================
    // 配置加载
    // ============================================================

    @Test
    @DisplayName("默认配置 - contextPath 与 header 拥有合理默认值")
    void defaultConfig_hasReasonableDefaults() {
        KbGatewayProperties props = new KbGatewayProperties();
        assertEquals("/kb", props.getContextPath(), "默认 contextPath 应为 /kb");
        assertEquals("Authorization", props.getJwt().getHeader(), "默认 JWT header 应为 Authorization");
        assertNotNull(props.getJwt(), "Jwt 子配置不应为 null");
        assertNotNull(props.getWhitelist(), "whitelist 不应为 null");
        assertTrue(props.getWhitelist().isEmpty(), "默认 whitelist 应为空");
        assertNull(props.getJwt().getSecret(), "默认 secret 应为 null（需配置注入）");
    }

    @Test
    @DisplayName("配置 setter/getter - 完整配置正确写入读取")
    void configSetterGetter_worksCorrectly() {
        KbGatewayProperties props = buildDefault();
        assertEquals("/kb", props.getContextPath());
        assertEquals("Authorization", props.getJwt().getHeader());
        assertEquals("YourSuperSecretKeyForJwtTokenGenerationMustBe256BitsLong!!",
                props.getJwt().getSecret());
        assertEquals(4, props.getWhitelist().size());
        assertTrue(props.getWhitelist().contains("/kb/api/auth/login"));
    }

    @Test
    @DisplayName("自定义 contextPath - 支持非默认上下文路径")
    void customContextPath_worksCorrectly() {
        KbGatewayProperties props = new KbGatewayProperties();
        props.setContextPath("/custom");
        props.setWhitelist(Arrays.asList(
                "/custom/api/auth/login",
                "/custom/api/share/verify/**"
        ));
        assertEquals("/custom", props.getContextPath());
        assertTrue(isWhitelisted(props, "/custom/api/auth/login"));
        assertTrue(isWhitelisted(props, "/custom/api/share/verify/abc"));
        assertFalse(isWhitelisted(props, "/kb/api/auth/login"), "切换 contextPath 后旧前缀不应再匹配");
    }
}
