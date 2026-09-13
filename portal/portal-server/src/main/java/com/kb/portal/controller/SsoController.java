package com.kb.portal.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.kb.portal.dto.LoginResponse;
import com.kb.portal.entity.SysUser;
import com.kb.portal.mapper.SysUserMapper;
import com.kb.portal.service.AuthCenterService;
import com.kb.portal.util.JwtUtil;
import com.kb.portal.util.PasswordUtil;
import com.marschat.common.exception.BusinessException;
import com.marschat.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 统一认证（auth-center）SSO 接入 + 用户管理代理。
 * 登录：浏览器跳 auth-center 授权 → 前端回调页拿 code → 此处换 token/映射账号/发 portal JWT。
 * 用户管理：前端同源调 /api/admin/users/**，此处以该用户身份代理到 auth-center（RS256 双验签）。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SsoController {

    private final AuthCenterService authCenterService;
    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;
    private final PasswordUtil passwordUtil;

    /** 发起 SSO：302 到 auth-center 授权页。redirect 必须是白名单 origin（防开放重定向） */
    @GetMapping("/auth/sso/authorize")
    public void authorize(@RequestParam String redirect, jakarta.servlet.http.HttpServletResponse response) throws Exception {
        String origin = normalizeOrigin(redirect);
        response.sendRedirect(authCenterService.buildAuthorizeUrl(origin));
    }

    /** 授权码换 portal 会话：映射/创建 sys_user，角色与 auth-center 同步 */
    @PostMapping("/auth/sso/exchange")
    public Result<LoginResponse> exchange(@RequestBody ExchangeRequest request) {
        String origin = authCenterService.consumeState(request.getState());
        if (origin == null) {
            throw new BusinessException("SSO 状态无效或已过期，请重新登录");
        }
        try {
            JsonNode tokenJson = authCenterService.exchangeCode(request.getCode(), origin);
            String accessToken = tokenJson.get("access_token").asText();
            JsonNode claims = authCenterService.parseAccessTokenClaims(accessToken);
            String username = claims.path("username").asText(null);
            String role = claims.path("role").asText("user");
            String nickname = claims.path("username").asText(username);
            if (username == null || username.isBlank()) {
                throw new BusinessException("统一认证返回的用户信息不完整");
            }
            // auth uid：token 的 uid/sub claim（Phase 5 JIT 关联键改 sub——username 可改名，
            // auth_uid 是 auth-center 侧稳定主键）
            String authUid = claims.path("uid").asText(null);
            if (authUid == null || authUid.isBlank()) {
                authUid = claims.path("sub").asText(null);
            }

            SysUser user = upsertPortalUser(authUid, username, role, nickname);
            // 仅 SSO（SAS 换票）有真正的 SAS refresh_token；邮箱码登录是 legacy token，不入 SSO 会话池
            authCenterService.storeRefreshToken(user.getId(), tokenJson);
            return Result.ok(toLoginResponse(user, authUid));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("SSO exchange 失败: {}", e.getMessage());
            throw new BusinessException("统一登录失败，请重试");
        }
    }

    /**
     * 邮箱验证码登录（统一登录三方式之一，Phase 7）。
     *
     * <p><b>为什么必须走 BFF：</b>portal 是 OIDC 机密客户端。若浏览器直连 auth-center
     * `/auth/mail-login`，拿到的是 auth-center 签发、portal-server 无法识别的 token；
     * 且会绕过 portal 的账号映射（sys_user / auth_uid），造成「登录了但 portal 不认」。
     * 因此由 portal-server 服务端换票，再复用**与 SSO exchange 完全相同**的
     * 账号映射链路（auth_uid → username 回填 → JIT 开通）+ portal JWT 签发。
     *
     * <p>浏览器路径：`POST /portal/api/auth/mail-login`（同源，经 nginx → portal-server）。
     */
    @PostMapping("/auth/mail-login")
    public Result<LoginResponse> mailLogin(@RequestBody MailLoginRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()
                || request.getCode() == null || request.getCode().isBlank()) {
            throw new BusinessException("缺少邮箱或验证码");
        }
        try {
            JsonNode data = authCenterService.mailLogin(request.getEmail().trim(), request.getCode().trim());
            JsonNode user = data.path("user");
            String username = user.path("username").asText(null);
            String role = user.path("role").asText("user");
            String nickname = user.path("nickname").isMissingNode() || user.path("nickname").isNull()
                    ? username : user.path("nickname").asText(username);
            String authUid = user.path("id").asText(null);
            if (username == null || username.isBlank()) {
                throw new BusinessException("统一认证返回的用户信息不完整");
            }
            SysUser portalUser = upsertPortalUser(authUid, username, role, nickname);
            log.info("portal 邮箱验证码登录成功: {} (authUid={})", username, authUid);
            return Result.ok(toLoginResponse(portalUser, authUid));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("portal 邮箱验证码登录失败: {}", e.getMessage());
            throw new BusinessException("邮箱验证码登录失败，请重试");
        }
    }

    /**
     * 账号映射（Phase 5/7 共用）：auth_uid 优先 → 存量按 username 命中则回填 auth_uid
     * （Account Linking 迁移，一次性）→ 都没有则 JIT 自动开通；角色跟随 auth-center。
     *
     * <p>SSO 授权码登录与邮箱验证码登录**共用本方法**，保证两条登录路径的账号视图一致。
     */
    private SysUser upsertPortalUser(String authUid, String username, String role, String nickname) {
        SysUser user = null;
        if (authUid != null && !authUid.isBlank()) {
            user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getAuthUid, authUid).last("LIMIT 1"));
        }
        if (user == null) {
            user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username).last("LIMIT 1"));
            if (user != null && authUid != null && !authUid.isBlank()) {
                // 存量账号迁移：绑定 auth_uid（若已被他人占用说明数据异常，拒绝登录而非串号）
                Long owner = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getAuthUid, authUid).ne(SysUser::getId, user.getId()));
                if (owner != null && owner > 0) {
                    throw new BusinessException("账号标识冲突（auth_uid 已绑定其他账号），请联系管理员");
                }
                user.setAuthUid(authUid);
                log.info("存量账号迁移绑定 auth_uid: {} -> {}", username, authUid);
            }
        }
        if (user == null) {
            user = new SysUser();
            user.setUsername(username);
            user.setPassword(passwordUtil.encode(java.util.UUID.randomUUID().toString()));
            user.setNickname(nickname);
            user.setStatus(1);
            user.setRole(role);
            user.setAuthUid(authUid);
            sysUserMapper.insert(user);
            log.info("自动开通 portal 账号: {} (role={}, authUid={})", username, role, authUid);
        } else {
            if (role != null && !role.equals(user.getRole())) {
                user.setRole(role);
            }
            if (user.getStatus() == null || user.getStatus() == 0) {
                throw new BusinessException("账号已被禁用");
            }
            sysUserMapper.updateById(user);
        }
        return user;
    }

    /** 由 portal 用户构建登录响应（两条登录路径共用，保证返回结构一致） */
    private LoginResponse toLoginResponse(SysUser user, String authUid) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(),
                user.getNickname() != null ? user.getNickname() : user.getUsername(),
                user.getRole(), authUid);
    }

    @lombok.Data
    public static class MailLoginRequest {
        private String email;
        private String code;
    }

    // ---------- RBAC 权限下发代理（Phase 2 下游接入）----------

    /**
     * 权限点下发代理：`GET /portal/api/auth/permissions?client=marschat-portal`。
     *
     * 🔴 为什么必须代理：portal 是 OIDC **机密客户端**，浏览器里只有 portal-server 自签的
     * `portal_token`（hutool HS256），**不是 auth-center 签发的 token** —— 前端直连
     * `https://auth.marschat.online/auth/permissions` 必然 401。
     * 这里以**该用户自己的 auth-center 身份**转发（{@link AuthCenterService#callAsUser}，
     * 无服务身份兜底，防提权）。
     *
     * 前端 `usePermissions({ issuer: '<origin>/portal/api' })` 即打到本端点；
     * 失败/无 SSO 会话 → 返回 401，前端按 `configured=false` 全放行（R10 默认策略）。
     */
    @GetMapping("/auth/permissions")
    public Result<JsonNode> permissions(@RequestParam(required = false) String client,
                                        HttpServletRequest request) {
        String target = (client == null || client.isBlank()) ? "marschat-portal" : client;
        AuthCenterService.ProxyResult r = authCenterService.callAsUser(
                (Long) request.getAttribute("userId"), "GET",
                "/auth/permissions?client=" + URLEncoder.encode(target, StandardCharsets.UTF_8), null);
        return toResult(r);
    }

    // ---------- 用户管理代理（仅 admin）----------

    @GetMapping("/admin/users")
    public Result<JsonNode> listUsers(@RequestParam(required = false) String realmId, HttpServletRequest request) {
        requireAdmin(request);
        String qs = realmId == null ? "" : "?realmId=" + URLEncoder.encode(realmId, StandardCharsets.UTF_8);
        AuthCenterService.ProxyResult r = authCenterService.callAdmin(
                (Long) request.getAttribute("userId"), "GET", "/admin/users" + qs, null);
        return toResult(r);
    }

    @PostMapping("/admin/users")
    public Result<JsonNode> createUser(@RequestBody String body, HttpServletRequest request) {
        requireAdmin(request);
        AuthCenterService.ProxyResult r = authCenterService.callAdmin(
                (Long) request.getAttribute("userId"), "POST", "/admin/users", body);
        return toResult(r);
    }

    @PutMapping("/admin/users/{userId}")
    public Result<JsonNode> updateUser(@PathVariable Long userId, @RequestBody String body, HttpServletRequest request) {
        requireAdmin(request);
        AuthCenterService.ProxyResult r = authCenterService.callAdmin(
                (Long) request.getAttribute("userId"), "PUT", "/admin/users/" + userId, body);
        return toResult(r);
    }

    @DeleteMapping("/admin/users/{userId}")
    public Result<JsonNode> deleteUser(@PathVariable Long userId, HttpServletRequest request) {
        requireAdmin(request);
        AuthCenterService.ProxyResult r = authCenterService.callAdmin(
                (Long) request.getAttribute("userId"), "DELETE", "/admin/users/" + userId, null);
        return toResult(r);
    }

    @PutMapping("/admin/users/{userId}/password")
    public Result<JsonNode> resetPassword(@PathVariable Long userId, @RequestBody String body, HttpServletRequest request) {
        requireAdmin(request);
        AuthCenterService.ProxyResult r = authCenterService.callAdmin(
                (Long) request.getAttribute("userId"), "PUT", "/admin/users/" + userId + "/password", body);
        return toResult(r);
    }

    // ---------- 授权管理代理（Phase 4 · 应用角色绑定）----------

    /** 角色列表（platform + client 级）→ auth-center GET /admin/roles */
    @GetMapping("/admin/roles")
    public Result<JsonNode> listRoles(HttpServletRequest request) {
        requireAdmin(request);
        AuthCenterService.ProxyResult r = authCenterService.callAdmin(
                (Long) request.getAttribute("userId"), "GET", "/admin/roles", null);
        return toResult(r);
    }

    /** 用户在某应用的角色绑定 id 集合 → auth-center GET /admin/users/{id}/client-roles */
    @GetMapping("/admin/users/{userId}/client-roles")
    public Result<JsonNode> userClientRoles(@PathVariable Long userId,
                                            @RequestParam(required = false) String client,
                                            HttpServletRequest request) {
        requireAdmin(request);
        String qs = client == null ? "" : "?client=" + URLEncoder.encode(client, StandardCharsets.UTF_8);
        AuthCenterService.ProxyResult r = authCenterService.callAdmin(
                (Long) request.getAttribute("userId"), "GET",
                "/admin/users/" + userId + "/client-roles" + qs, null);
        return toResult(r);
    }

    /** 用户在某应用的角色绑定全量覆盖 → auth-center PUT /admin/users/{id}/client-roles */
    @PutMapping("/admin/users/{userId}/client-roles")
    public Result<JsonNode> assignUserClientRoles(@PathVariable Long userId,
                                                  @RequestParam(required = false) String client,
                                                  @RequestBody String body,
                                                  HttpServletRequest request) {
        requireAdmin(request);
        String qs = client == null ? "" : "?client=" + URLEncoder.encode(client, StandardCharsets.UTF_8);
        AuthCenterService.ProxyResult r = authCenterService.callAdmin(
                (Long) request.getAttribute("userId"), "PUT",
                "/admin/users/" + userId + "/client-roles" + qs, body);
        return toResult(r);
    }

    /**
     * 管理员校验。
     *
     * <p>⚠️ 必须同时认 {@code superadmin}：auth-center 的超管账号（{@code user.role='superadmin'}，§22.1）
     * 是平台唯一的最高权限账号。只比 {@code "admin"} 会把超管挡在 portal 用户管理之外
     * （2026-09-13 浏览器实测：超管登录 portal 后 {@code /portal/users} 被重定向，
     * BFF {@code /portal/api/admin/users} 返回 403「需要管理员权限」）。
     */
    private void requireAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        if (!"admin".equals(role) && !"superadmin".equals(role)) {
            throw new BusinessException(403, "需要管理员权限");
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    /** 解包 auth-center 的 Result：200 取内层 data 节点，否则抛业务异常 */
    @SuppressWarnings("unchecked")
    private Result<JsonNode> toResult(AuthCenterService.ProxyResult r) {
        try {
            JsonNode node = MAPPER.readTree(r.body() == null ? "{}" : r.body());
            if (r.status() == 200 && node.path("code").asInt() == 200) {
                return Result.ok(node.get("data"));
            }
            throw new com.marschat.common.exception.BusinessException(
                    node.path("code").asInt(500),
                    node.path("message").asText("统一认证中心返回错误"));
        } catch (com.marschat.common.exception.BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new com.marschat.common.exception.BusinessException(502, "统一认证中心响应解析失败");
        }
    }

    /**
     * redirect 校验：白名单 **origin**（严格匹配，防开放重定向）+ 其任意站内路径；
     * **返回值必须是裸 origin** —— buildAuthorizeUrl 拿它拼 redirect_uri（origin+/portal/auth/callback），
     * 带路径会拼出 /portal/portal/auth/callback 被 SAS 拒（2026-09-12 实测）。
     * 路径部分丢弃，落地统一回 origin（与回调页固定回首页的既有行为一致）。
     */
    private String normalizeOrigin(String redirect) {
        if (redirect == null) {
            throw new BusinessException("缺少 redirect 参数");
        }
        String trimmed = redirect.trim();
        java.net.URI uri;
        try {
            uri = java.net.URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("不允许的回调地址");
        }
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        boolean allowed = origin.equals("https://main.marschat.online")
                || origin.equals("http://192.168.31.105:8095")
                || origin.equals("http://localhost:5173");
        if (!allowed) {
            throw new BusinessException("不允许的回调地址");
        }
        return origin;
    }

    @lombok.Data
    public static class ExchangeRequest {
        private String code;
        private String state;
    }
}
