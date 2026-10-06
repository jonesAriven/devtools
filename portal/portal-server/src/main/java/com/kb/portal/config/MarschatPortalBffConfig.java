package com.kb.portal.config;

import com.kb.portal.service.AuthCenterService;
import com.marschat.auth.bff.BffAccountSource;
import com.marschat.auth.bff.BffCredentialResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * portal 的 BFF 扩展点（Phase 13 配置化接入）。
 *
 * <p>管理代理的公共实现已在 auth-core 2.2.0 的 {@code MarschatBffAutoConfig} 中
 * （白名单配置化 + 凭据三模式 + 账号上报 SPI），本类只提供**本应用特有的两处扩展**，
 * 取代此前手写的 {@code SsoController#proxyAdminCenter}（约 50 行）
 * + {@code config/LocalAccountReporter}（111 行）。
 *
 * <h3>① 为什么必须自定义 {@link BffCredentialResolver}（三种 credential-mode 全不适用）</h3>
 * portal <b>没有 Spring Security</b>（{@code pom.xml} 无 {@code spring-boot-starter-security}），
 * 鉴权走自研 {@link JwtInterceptor}（HandlerInterceptor），它把身份写在
 * <b>request attribute</b> 上：
 * <pre>
 *   request.setAttribute("userId",   userId);     ← 本类取的就是它
 *   request.setAttribute("username", username);
 *   request.setAttribute("role",     role);
 * </pre>
 * 而 {@code DefaultBffCredentialResolver} 的三种模式<b>都不读 request attribute</b>：
 * <ul>
 *   <li>{@code PASSTHROUGH} —— 取 {@code Authorization: Bearer}。浏览器里只有 portal-server
 *       自签的 HS256 {@code portal_token}，auth-center<b>不认</b>（RS256），转发必 401；</li>
 *   <li>{@code SESSION_STORE} —— 取 {@code request.getUserPrincipal().getName()}，那是
 *       <b>Spring Security 语义</b>，portal 无 Security ⇒ 恒为 {@code null}；</li>
 *   <li>{@code AUTO} —— 先试 Bearer（非自签就直接用），再回落 {@code getUserPrincipal()}。
 *       本应用<b>不提供</b> {@code BffLocalTokenClassifier}，故无 classifier 时
 *       {@code locallyIssued=false} ⇒ 与 PASSTHROUGH 同病；而 portal_token 正是自签的，
 *       真配了 classifier 也会被判为「自签」而回落到同样失效的会话表。</li>
 * </ul>
 * 三种模式<b>索引键也全不同</b>：{@code CenterSessionStore} 按 <b>username</b> 索引，
 * 而 portal 的凭据真实来源（{@link AuthCenterService#refreshTokens}）是按
 * <b>portal 用户 id（Long）</b> 索引的进程内 Map —— 即便灌满 store 也取不到。
 *
 * <p>⇒ 解法：提供本 Bean 覆盖默认实现。<b>auth-core 零改动、无需发版。</b>
 *
 * <h3>② 为什么 Bean 名带 {@code marschat} 前缀</h3>
 * auth-core 的 {@code MarschatBffAutoConfig#marschatBffCredentialResolver} 带
 * {@code @ConditionalOnMissingBean}，而它<b>只按类型匹配、不看名字</b>。
 * 若本 Bean 用了通用名（如 {@code credentialResolver}），在某些装配顺序下可能与库内默认
 * Bean 撞名，抛 {@code BeanDefinitionOverrideException} 导致<b>启动 crash-loop</b>。
 * 用 {@code marschat} 前缀可确保不会与库内默认 Bean 撞名。
 *
 * <h3>③ 本类为何<b>不</b>加 {@code @ConditionalOnMissingBean}</h3>
 * 该注解只在<b>自动装配类</b>上语义可靠（Spring Boot 文档明确说明它「只应出现在自动装配类中」，
 * 因为它依赖「用户 Bean 先于自动装配注册」这一顺序）。
 * 本类是<b>用户 {@code @Configuration}</b>：加它反而可能因装配顺序不确定而让本 Bean 被跳过，
 * 继而回落到不适用的默认解析器 ⇒ 管理面全 401。
 * 正确做法是<b>只在本 Bean 上不加该注解</b>，让库内自动装配的
 * {@code @ConditionalOnMissingBean} 检测到本 Bean 后自行退让。
 *
 * <h3>安全不变式（务必保持）</h3>
 * <ul>
 *   <li>解析不到中心令牌 → 返回 {@code null} → 上层 401 fail-closed，
 *       <b>绝不回退服务账号</b>（服务账号是 admin，回退即提权）；
 *       历史 P0 事故即源于「无会话回退服务账号」。</li>
 *   <li>端点本身仍受 {@link JwtInterceptor}（{@code /api/admin/**} 已登记其路径）
 *       + {@link PortalAdminGateInterceptor}（role + 中心权限点 api:admin）保护。</li>
 * </ul>
 */
@Slf4j
@Configuration
public class MarschatPortalBffConfig {

    private final AuthCenterService authCenterService;

    public MarschatPortalBffConfig(AuthCenterService authCenterService) {
        this.authCenterService = authCenterService;
    }

    /**
     * 解析 BFF 调用者的<b>统一认证中心 access token</b>。
     *
     * <p>数据流：{@code request.getAttribute("userId")}（{@link JwtInterceptor} 注入的
     * portal 用户 id）→ {@link AuthCenterService#refreshAccessToken(Long)}
     * → 用内存里的 refresh_token 换一张新的中心 access token（RS256）。
     *
     * <p>🔴 必须走 refresh 而不能直接取 {@code Authorization} 头：浏览器持有的
     * {@code portal_token} 是 portal-server 自签的 HS256，auth-center 不认。
     *
     * <p>🔴 与被删除的 {@code proxyAdminCenter} 的<b>关键行为差异（有意为之）</b>：
     * 旧实现走 {@code callAdmin}，它在无 SSO 会话时<b>回退服务账号</b>
     * （{@code getServiceToken()}，即 {@code auth-center.admin-username/password}）。
     * 本实现<b>不做该回退</b> —— 无 SSO 会话即 401 fail-closed，交前端走正常重授权。
     * 这与 auth-core 的安全不变式一致，也堵住了「服务账号兜底 = 凭服务账号放行」的提权面。
     */
    @Bean("marschatPortalBffCredentialResolver")
    public BffCredentialResolver marschatPortalBffCredentialResolver() {
        return this::resolveCenterToken;
    }

    /**
     * 从 request attribute 取 portal 用户 id，换取中心 access token。
     *
     * <p>🔴 2026-10-06 Phase 13：改用 {@code resolveAccessToken}（按登录渠道自动选池），
     * 不再只走「SSO refresh_token 换票」这一条 —— 否则账密/邮箱码登录的管理员
     * 恒 401，而他们**本该能用**用户管理页（中心在那两种登录时就已签发可用access_token）。
     *
     * @return 中心 access token；<b>无可用凭据 / 换取失败时返回 {@code null}</b>
     *（上层据此 401，绝不兜底服务账号）
     */
    private String resolveCenterToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object rawUserId = request.getAttribute("userId");
        if (!(rawUserId instanceof Long portalUserId)) {
            // 未登录 / 非 portal 签发的 token —— JwtInterceptor 尚未放行到这里，
            // 或 token 内无 userId claim。一律视为「无凭据」。
            return null;
        }
        try {
            String accessToken = authCenterService.resolveAccessToken(portalUserId);
            return (accessToken == null || accessToken.isBlank()) ? null : accessToken;
        } catch (Exception e) {
            // 凭据过期 / 被撤销 / 认证中心侧会话丢失 → fail-closed
            log.warn("BFF 换取中心凭据失败（返回 401，不回退服务账号）：userId={} cause={}",
                    portalUserId, e.getMessage());
            return null;
        }
    }

    /**
     * 本地账号来源 —— portal 的本地账号是 {@code sys_user} 表（含超管应急账号）。
     * 上报动作（PUT {@code /internal/clients/{id}/accounts} + {@code X-Client-Secret}、
     * 异常只 WARN 不阻断）由 auth-core 的 {@code MarschatBffAccountReporter} 承担。
     *
     * <p>语义与被删除的 {@code LocalAccountReporter} 一致：全量上报活跃账号
     * （{@code deleted=0 AND status=1}）；表为空则跳过并记 INFO。
     */
    @Bean
    public BffAccountSource marschatPortalBffAccountSource(JdbcTemplate jdbcTemplate) {
        return () -> {
            List<Map<String, Object>> rows = jdbcTemplate.query(
                    "SELECT username, nickname FROM sys_user WHERE deleted = 0 AND status = 1",
                    (rs, i) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("account", rs.getString("username"));
                        m.put("name", rs.getString("nickname"));
                        return m;
                    });
            return rows.stream()
                    .filter(m -> m.get("account") != null && !String.valueOf(m.get("account")).isBlank())
                    .map(m -> new BffAccountSource.BffLocalAccount(
                            String.valueOf(m.get("account")),
                            m.get("name") == null ? null : String.valueOf(m.get("name"))))
                    .toList();
        };
    }
}