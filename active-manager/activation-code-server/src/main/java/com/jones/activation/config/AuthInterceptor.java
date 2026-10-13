package com.jones.activation.config;

import com.jones.activation.entity.AdminUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    /**
     * 🔓 方法级匿名白名单（2026-10-12 恢复；P0-3 收口的误伤回退）。
     *
     * <p><b>背景</b>：2026-09-16 P0-3 修「匿名洞」时，把 {@code /api/activation/generate} 与
     * {@code /api/activation/config/default-expire} 从 {@code WebMvcConfig} 的
     * {@code excludePathPatterns} 移除，改由登录 + 权限点把关。
     * 但 {@code /activecode/index.html} 是<b>给外模块 / 客户端提供的公开自助页</b>
     * （该页面本身仍在白名单内，见 {@code WebMvcConfig}），它依赖的正是这两个接口。
     * ⇒ 页面打得开，但「生成激活码」必然 401、「有效期」永远卡在「加载中...」
     * （前端 {@code d.success=false} 时既不设置也不走 catch，故 UI 假死）。
     *
     * <p><b>为什么必须按方法拆，不能用路径级 exclude</b>：
     * {@code config/default-expire} 的 <b>GET</b>（读默认有效期）是公开页要用的；
     * 而 <b>PUT</b>（改默认有效期）是管理功能，仍受 {@code PermissionInterceptor}
     * 规则表（{@code perm=config:expire}）保护。若用路径级 exclude 会连 PUT 一起放开
     * ⇒ <b>匿名可改系统配置</b>（越权）。故这里以「路径 + 方法」为键。
     *
     * <p>🔄 2026-10-14：白名单从本类硬编码迁移到配置
     * {@code marschat.auth.anonymous.endpoints}（{@link AnonymousAuthProperties}），
     * 收放匿名权只改 yml 重启即可，不再需要改代码发版。
     *
     * <p>⚠️ <b>放开后的风险与缓解</b>：「匿名可生成激活码」。约束仍在：序列号必须由客户端
     * 软件产生、服务端校验有效性（无效即拒 {@code SERIAL_INVALID}）。如需进一步收紧
     * （频控 / 验证码），另立工单，**不要靠把接口重新关回去**来解决。
     */
    private final Map<String, Set<String>> anonymousEndpoints;

    public AuthInterceptor(AnonymousAuthProperties anonymousAuth) {
        // 启动时快照一次配置（收放匿名权后需重启生效，符合部署预期）
        this.anonymousEndpoints = anonymousAuth.endpointMap();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS请求直接放行（CORS预检）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            AdminUser user = (AdminUser) session.getAttribute("loginUser");
            if (user != null) {
                return true;
            }
        }

        // 🔓 方法级匿名白名单：对外自助页 index.html 的依赖接口（见字段注释）
        //    放在 session 判定之后 ⇒ 已登录用户本就可访问，此分支只影响匿名请求。
        if (isAnonymousEndpoint(request.getRequestURI(), request.getMethod())) {
            return true;
        }

        // 未登录：API请求返回401，页面请求重定向到登录页
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();

        if (uri.startsWith("/activecode/api/")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"未登录或会话已过期\"}");
            return false;
        }

        // 页面请求重定向到登录页
        response.sendRedirect("/activecode/login.html");
        return false;
    }

    /** 白名单判定：路径命中且 HTTP 方法（大写）命中才放行。 */
    private boolean isAnonymousEndpoint(String uri, String method) {
        Set<String> methods = anonymousEndpoints.get(uri);
        return methods != null && method != null && methods.contains(method.toUpperCase());
    }
}
