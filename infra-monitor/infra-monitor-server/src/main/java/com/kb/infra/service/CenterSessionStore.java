package com.kb.infra.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账密 / 邮箱码登录换来的**中心业务令牌**（auth-center {@code /auth/login} 的
 * {@code data.accessToken}）的内存会话表：{@code username → accessToken}。
 *
 * <h3>为什么放在服务端内存而不是浏览器</h3>
 * 「用户管理」页需要以**中心身份**调 {@code /admin/**}，而本应用自签会话 token 调不动中心。
 * 两种做法：
 * <ol>
 *   <li>把中心 accessToken 回传前端存 localStorage，每次请求带上 —— 等于给页面加了一个
 *       可被 XSS 直接窃取的**中心管理凭据**，且它不受本应用登出控制；</li>
 *   <li>（本实现）服务端按用户名暂存，{@link com.kb.infra.controller.AdminProxyController}
 *       转发时取用 —— 与 portal 的 {@code AuthCenterService.refreshTokens} 同一模式，
 *       浏览器侧只留本应用自己的会话 token。</li>
 * </ol>
 *
 * <p><b>已知边界</b>（与 portal 相同）：内存态，服务重启即失效 → 账密会话访问用户管理会
 * 返回 401 触发重授权；亦要求单实例部署（本应用为 host 网络单容器）。
 * SSO 会话不受影响 —— 它持有的是中心 OIDC access token，由调用方直接带来。
 */
@Component
public class CenterSessionStore {

    /** 提前 60s 视为过期，避免边界时刻把刚过期的 token 发出去 */
    private static final long SKEW_MS = 60_000L;

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    private record Entry(String accessToken, long expiresAt) {}

    /**
     * 记录某用户的中心会话。
     *
     * @param expiresInMs 中心返回的 expiresIn（毫秒）；<=0 或缺省时按 1 小时兜底
     */
    public void put(String username, String accessToken, long expiresInMs) {
        if (username == null || username.isBlank() || accessToken == null || accessToken.isBlank()) {
            return;
        }
        long ttl = expiresInMs > 0 ? expiresInMs : 3_600_000L;
        sessions.put(username, new Entry(accessToken, System.currentTimeMillis() + ttl - SKEW_MS));
    }

    /** 取该用户当前有效的中心 accessToken；无会话或已过期返回 {@code null}（顺带清理） */
    public String getAccessToken(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        Entry entry = sessions.get(username);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt() <= System.currentTimeMillis()) {
            sessions.remove(username);
            return null;
        }
        return entry.accessToken();
    }

    public void remove(String username) {
        if (username != null) {
            sessions.remove(username);
        }
    }
}
