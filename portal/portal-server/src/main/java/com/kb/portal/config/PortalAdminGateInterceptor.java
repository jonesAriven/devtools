package com.kb.portal.config;

import com.kb.portal.service.AuthCenterService;
import com.kb.portal.util.JwtUtil;
import com.marschat.auth.authz.PermissionChecker;
import com.marschat.auth.authz.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 门户管理面闸门（Phase 13 迁移补偿）—— **恢复迁移前的三道闸**。
 *
 * <h3>迁移前后对照</h3>
 * <pre>
 * 迁移前（SsoController#proxyAdminCenter）：
 *   ① 本地 role ∈ {admin, superadmin}        （requireAdmin，方法内第二道）
 *   ② 中心权限点 marschat-portal:api:admin     （@RequirePermission + RequirePermissionInterceptor）
 *   ③ 中心侧 @PreAuthorize("hasRole('ADMIN')") （AdminUserController:22，服务端强制）
 *
 * 迁移后（本类 + 库内 BFF 控制器）：
 *   ① 本地 role        → 本类（order(1)）
 *   ② 中心权限点        → 本类直接调用 PermissionChecker#hasPermission（等价于原②）
 *   ③ 中心 hasRole     → 中心侧，仍在
 * </pre>
 *
 * <h3>🔴 为什么第②道要「手动调用」而不是靠 {@code @RequirePermission} 注解</h3>
 * 代理实现已改为库内 {@code MarschatBffAdminProxyController}，该类<b>没有</b>
 * {@code @RequirePermission} 注解（它在公共库里，不带应用语义），而 auth-core 的
 * {@code RequirePermissionInterceptor:36-38} 对 {@code anno == null} <b>直接放行</b>
 * ⇒ 注解驱动机制在此失效。
 * <b>但第二道闸本身并未失效</b>：{@link PermissionChecker#hasPermission} 是
 * {@code public} 方法，且 portal 已通过 {@link AuthzConfig} 注册了实现
 * {@link PortalPermissionChecker}（以该用户本人的 RS256 身份查中心）。
 * 故本类<b>直接调用</b>它即可完全恢复第②道 —— <b>零 auth-core 改动、零新依赖</b>。
 *
 * <h3>🔴 superadmin 豁免的实现位置（不是本类，而是 PortalPermissionChecker）</h3>
 * 第②道对超管的豁免<b>不在本类</b>，而在
 * {@link PortalPermissionChecker#hasPermission} 的第 2 步
 * （{@code if ("superadmin".equals(role)) return true;}）。
 * 本类因此<b>不重复实现</b>豁免逻辑，避免两处真源漂移。
 * 该实现正是 2026-09-13「超管被误挡」事故后的产物，必须保留。
 *
 * <h3>🔴 为什么权限点失败返回 <b>401</b> 而非 403（可自助恢复）</h3>
 * 第②道失败有两种成因，<b>都返 401</b>（前端 {@code request.ts:68} 对 401 会
 * 自动跳 SSO 重授权，管理员<b>可自助恢复</b>；若返 403 则前端无路可走、
 * 管理员被永久卡死）：
 * <ul>
 *   <li><b>场景③</b>：无 SSO 会话（账密 / 邮箱码登录，或 portal 重启内存清空）
 *       ⇒ 由 {@link #hasSsoSession} <b>前置</b>判定，避免与场景②混淆；</li>
 *   <li><b>场景②</b>：中心不可达 / 权限点已被回收。</li>
 * </ul>
 * 两者状态码相同（语义统一：会话无法验证），但<b>日志分别打印不同文案</b>，
 * 便于线上区分「中心故障」与「该用户本就该重新授权」。
 *
 * <h3>⚠️ 执行顺序（必须晚于 JwtInterceptor）</h3>
 * 本类依赖 {@code request.getAttribute("userId"/"role")}，由 {@link JwtInterceptor} 写入。
 * 若本拦截器先于它执行，attribute 恒为 {@code null} ⇒ 管理员被误判。
 * 故在 {@link WebMvcConfig} 中显式 {@code .order(1)}。
 *
 * <h3>安全语义</h3>
 * 未登录请求<b>不会</b>走到这里 —— {@link JwtInterceptor} 已先返回 401。
 * 本类 fail-closed：任何判定不确定的情形一律拒绝。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortalAdminGateInterceptor implements HandlerInterceptor {

    /** 允许访问门户管理面的角色。 */
    private static final String ROLE_ADMIN = "admin";

    /**
     * 平台最高权限角色。必须与前端 {@code isAdmin}（{@code src/stores/user.ts}）
     * 和中心侧 {@code hasRole('ADMIN')} 保持一致，否则会出现
     * 「前端放行、后端 403」或反之的漂移。
     */
    private static final String ROLE_SUPERADMIN = "superadmin";

    /**
     * 本应用在中心的接口级权限点（全码为 {@code marschat-portal:api:admin}）。
     *
     * <p><b>⚠️ 已知残余盲区（刻意不加静态断言，由真机 P0 兜底）</b>：若有人把此常量改成
     * 另一个<b>同样存在但更宽</b>的权限点（如 {@code api:read}），第②道会形同虚设，
     * 而 {@code VerifyBff} 的 8 条静态契约<b>全部照样通过</b>。
     * <b>刻意不为此加断言</b>：那会变成「断言拼字符串」的无限加固（下一个更宽的点又能绕开），
     * 收益低于维护成本。此类改动必然修改字符串常量 ⇒ <b>git 可见、code review 可抓</b>。
     * ⇒ <b>最终防线是真机 P0</b>：{@code role=admin(SSO)→200} 与
     * {@code superadmin→200}（QA #11覆盖）。
     */
    private static final String PERMISSION_ADMIN = "api:admin";

    private final JwtUtil jwtUtil;

    /** 中心权限点校验（实现由 {@link AuthzConfig} 提供，内含 superadmin 豁免）。 */
    private final PermissionChecker permissionChecker;

    /** 用于前置判定「是否持有 SSO 会话」，以区分场景②与场景③。 */
    private final AuthCenterService authCenterService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        String bearer = bearerToken(request);

        // ── 第①道：本地 role ────────────────────────────────────────────
        String role = resolveRole(request);
        if (!ROLE_ADMIN.equals(role) && !ROLE_SUPERADMIN.equals(role)) {
            log.warn("[管理面闸门] 拒绝非管理员：uri={} role={}", uri, role);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "需要管理员权限");
            return false;
        }

        // ── 前置：无 SSO 会话 ⇒ 场景③（账密 / 邮箱码登录）──────────────
        //    必须早于第②道：否则「中心故障」与「本就该重授权」会落到同一个
        //    状态码且日志无法区分（见类注释「为什么返 401」）。
        Long userId = resolveUserId(request);
        if (!authCenterService.hasSsoSession(userId)) {
            log.warn("[管理面闸门] 拒绝（场景③·无 SSO 会话，请用统一认证登录）："
                    + "uri={} userId={} role={}", uri, userId, role);
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "无统一认证会话，请使用统一认证登录");
            return false;
        }

        // ── 第②道：中心权限点 api:admin（superadmin 豁免在 checker 内）──
        boolean permitted = permissionChecker.hasPermission(
                bearer, new String[]{PERMISSION_ADMIN}, RequirePermission.Mode.ANY);
        if (!permitted) {
            log.warn("[管理面闸门] 拒绝（场景②·中心不可达或权限点 api:admin 已被回收）："
                    + "uri={} userId={} role={}", uri, userId, role);
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "无法验证统一认证权限，请稍后重试");
            return false;
        }

        return true;
    }

    /** 取 {@code Authorization: Bearer xxx} 的原始头值（{@code hasPermission} 自行剥前缀）。 */
    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header == null || header.isBlank()) ? null : header;
    }

    /**
     * 解析 role：首选 {@link JwtInterceptor} 写入的 attribute，
     * 缺失时回退到解析 {@code Authorization} 头。
     *
     * <p>回退分支是防御「将来有人调整拦截器顺序」导致管理员被误拒 ——
     * 正常路径不会走到这里（已保证 {@code .order(1)}）。
     */
    private String resolveRole(HttpServletRequest request) {
        Object attr = request.getAttribute("role");
        if (attr instanceof String r && !r.isBlank()) {
            return r;
        }
        String token = tokenFromHeader(request);
        if (token == null) {
            return null;
        }
        try {
            return jwtUtil.getRole(token);
        } catch (Exception e) {
            // token 非法/过期：交回 JwtInterceptor 统一出 401，本类不重复判定
            log.warn("[管理面闸门] role 解析失败，交回 JwtInterceptor：{}", e.getMessage());
            return null;
        }
    }

    /** 解析 portal 用户 id（同样 attribute 优先、token 兜底）。 */
    private Long resolveUserId(HttpServletRequest request) {
        Object attr = request.getAttribute("userId");
        if (attr instanceof Long id) {
            return id;
        }
        String token = tokenFromHeader(request);
        if (token == null) {
            return null;
        }
        try {
            return jwtUtil.getUserId(token);
        } catch (Exception e) {
            log.warn("[管理面闸门] userId 解析失败，按无会话处理：{}", e.getMessage());
            return null;
        }
    }

    /** 从 Authorization 头取出裸 token。 */
    private String tokenFromHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}