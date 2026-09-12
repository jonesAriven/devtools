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

            // 账号映射：按 username 找 sys_user，不存在则自动开通；角色跟随 auth-center
            SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username).last("LIMIT 1"));
            if (user == null) {
                user = new SysUser();
                user.setUsername(username);
                user.setPassword(passwordUtil.encode(java.util.UUID.randomUUID().toString()));
                user.setNickname(nickname);
                user.setStatus(1);
                user.setRole(role);
                sysUserMapper.insert(user);
                log.info("SSO 自动开通 portal 账号: {} (role={})", username, role);
            } else {
                if (!role.equals(user.getRole())) {
                    user.setRole(role);
                }
                if (user.getStatus() == null || user.getStatus() == 0) {
                    throw new BusinessException("账号已被禁用");
                }
                sysUserMapper.updateById(user);
            }

            authCenterService.storeRefreshToken(user.getId(), tokenJson);
            String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
            // auth uid：前端会话监视器的「身份一致性守卫」比对用（auth-components 0.5.4+）
            String authUid = claims.path("uid").asText(null);
            if (authUid == null || authUid.isBlank()) {
                authUid = claims.path("sub").asText(null);
            }
            return Result.ok(new LoginResponse(token, user.getUsername(),
                    user.getNickname() != null ? user.getNickname() : user.getUsername(), user.getRole(), authUid));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("SSO exchange 失败: {}", e.getMessage());
            throw new BusinessException("统一登录失败，请重试");
        }
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

    private void requireAdmin(HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("role"))) {
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
