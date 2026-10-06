package com.jones.activation.config;

import com.jones.activation.entity.AdminUser;
import com.jones.activation.mapper.AdminUserMapper;
import com.marschat.auth.bff.BffAccountSource;
import com.marschat.auth.bff.BffCredentialResolver;
import com.marschat.auth.bff.CenterSessionStore;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 统一认证 BFF 的应用侧扩展点（Phase 13 配置化接入）。
 *
 * <p>管理代理的实现已在 `auth-core` 2.2.0 的 {@code MarschatBffAutoConfig} 中公共化
 * （白名单配置化 + 凭据三模式 + 账号上报 SPI），本类只提供**本应用特有的两处扩展**，
 * 取代此前手写的 {@code AdminProxyController}（297 行）+ {@code CenterSessionStore}（71 行）
 * + {@code LocalAccountReporter}（104 行）。
 *
 * <h3>为什么本应用必须自定义 {@link BffCredentialResolver}（而不能用 credential-mode）</h3>
 * auth-core 内置的三种模式中，{@code session-store} / {@code auto} 都用
 * {@code request.getUserPrincipal()} 取当前用户名 —— 那是 **Spring Security 语义**。
 * 而本应用**没有引入 Spring Security**（鉴权走自有的 MVC 拦截器
 * {@code AuthInterceptor} + {@code PermissionInterceptor}），用户名存在 **HttpSession 属性**里
 * （{@code ssoUser} / {@code loginUser}），因此 {@code getUserPrincipal()} 恒为 {@code null}。
 *
 * <p>解法：提供本 Bean 覆盖默认实现（{@code @ConditionalOnMissingBean} 保证用户 Bean 优先）。
 * 这也正是把「凭据解析」设计成 SPI 的价值 —— **公共库不必假设所有应用都用 Spring Security**。
 *
 * <h3>安全不变式（与旧实现一致，务必保持）</h3>
 * <ul>
 *   <li>解析不到中心令牌 → 返回 {@code null} → 上层 401 fail-closed；**绝不回退服务账号**；</li>
 *   <li>端点本身仍受 {@code AuthInterceptor} 保护（其 {@code addPathPatterns} 含
 *       {@code /activecode/api/**}，覆盖 {@code /activecode/api/admin/**}）——
 *       未登录请求在进入代理方法之前就被拦下。</li>
 * </ul>
 */
@Configuration
public class MarschatBffConfig {

    /**
     * 中心凭据解析：用户名优先取 {@code ssoUser}（SSO / 邮箱码径登记的中心用户名），
     * 回退 {@code loginUser}（账密径的影子账号用户名 —— 二者同名，见 AuthController 的收敛逻辑），
     * 再用该用户名从 {@link CenterSessionStore} 取登录时暂存的中心 accessToken。
     */
    @Bean
    public BffCredentialResolver bffCredentialResolver(CenterSessionStore centerSessionStore) {
        return request -> {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return null;
            }
            String username = null;
            Object ssoUser = session.getAttribute("ssoUser");
            if (ssoUser instanceof String s && !s.isBlank()) {
                username = s;
            }
            if (username == null) {
                Object loginUser = session.getAttribute("loginUser");
                if (loginUser instanceof AdminUser u) {
                    username = u.getUsername();
                }
            }
            return username == null ? null : centerSessionStore.getAccessToken(username);
        };
    }

    /**
     * 本地账号来源 —— 本应用的本地账号是 {@code admin_user} 表（激活码系统应急管理员）。
     * 上报动作（PUT {@code /internal/clients/{id}/accounts} + X-Client-Secret、异常只 WARN 不阻断）
     * 由 auth-core 的 {@code MarschatBffAccountReporter} 承担。
     *
     * <p>语义与旧 {@code LocalAccountReporter} 一致：全量上报；表为空则跳过并记 INFO。
     */
    @Bean
    public BffAccountSource bffAccountSource(AdminUserMapper adminUserMapper) {
        return () -> {
            List<AdminUser> users = adminUserMapper.selectList(null);
            if (users == null || users.isEmpty()) {
                return List.of();
            }
            return users.stream()
                    .filter(u -> u != null && u.getUsername() != null && !u.getUsername().isBlank())
                    .map(u -> new BffAccountSource.BffLocalAccount(
                            u.getUsername(), "激活码系统应急管理员"))
                    .toList();
        };
    }
}
