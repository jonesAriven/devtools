package com.kb.infra.config;

import com.kb.infra.util.JwtUtil;
import com.marschat.auth.bff.BffAccountSource;
import com.marschat.auth.bff.BffLocalTokenClassifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 统一认证 BFF 的应用侧扩展点（Phase 13 配置化接入）。
 *
 * <p>管理代理的实现已在 `auth-core` 2.2.0 的 {@code MarschatBffAutoConfig} 中公共化
 * （白名单配置化 + 凭据三模式 + 账号上报 SPI），本类只提供**本应用特有的两处扩展**，
 * 共 30 行，取代此前手写的 {@code AdminProxyController}（295 行）+ {@code CenterSessionStore}
 * （71 行）+ {@code LocalAccountReporter}（95 行）。
 *
 * <p>本应用在 {@code application.yml} 显式 exclude 了 auth-core 的 {@code AuthJwtAutoConfig}
 * （自有 JWT 体系，不需要 TokenProvider）；BFF 自动装配不依赖 TokenProvider，不受影响。
 */
@Configuration
public class MarschatBffConfig {

    /**
     * 供 {@code credential-mode: auto} 判断「这个 token 是不是本应用自签的」。
     *
     * <p>语义与旧 {@code AdminProxyController.resolveCenterToken} 完全一致：
     * {@code parseLocalUsername(token) != null} 表示是本应用签发的本地会话 token
     * （中心不认），此时不能用它去访问中心，必须回落到 {@link com.marschat.auth.bff.CenterSessionStore}。
     *
     * <p>两种会话因此各自成立：
     * <ul>
     *   <li><b>SSO 会话</b>：{@code Authorization} 里就是中心 OIDC token → 验不出本地用户名 → 直接透传；</li>
     *   <li><b>账密 / 邮箱码会话</b>：{@code Authorization} 是本地 HS256 token → 回落中心会话表。</li>
     * </ul>
     */
    @Bean
    public BffLocalTokenClassifier bffLocalTokenClassifier(JwtUtil jwtUtil) {
        return token -> jwtUtil.parseLocalUsername(token) != null;
    }

    /**
     * 本地账号来源 —— infra-monitor 的「本地账号」是**配置式单管理员**
     * （{@code infra.admin.username}，无 user 表），它是双模策略里的**超管应急账号**：
     * 中心统一账号不可用时可由它在应用内直接登录。
     *
     * <p>上报动作（PUT {@code /internal/clients/{id}/accounts} + X-Client-Secret、异常只 WARN 不阻断）
     * 由 auth-core 的 {@code MarschatBffAccountReporter} 承担，本应用只声明「有哪些账号」。
     *
     * <p>返回空集合 = 不上报（reporter 会跳过并记 INFO）；凭据缺失时 reporter 也只 WARN。
     */
    @Bean
    public BffAccountSource bffAccountSource(
            @Value("${infra.admin.username:}") String adminUsername) {
        if (adminUsername == null || adminUsername.isBlank()) {
            return List::of;
        }
        return () -> List.of(new BffAccountSource.BffLocalAccount(
                adminUsername, "基础设施监控应急管理员"));
    }
}
