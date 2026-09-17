package com.jones.activation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jones.activation.entity.AdminUser;
import com.jones.activation.mapper.AdminUserMapper;
import com.jones.activation.service.CenterSessionStore;
import com.jones.activation.util.OidcTokenVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/activecode/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AdminUserMapper adminUserMapper;
    private final OidcTokenVerifier oidcVerifier;
    private final CenterSessionStore centerSessions;

    public AuthController(AdminUserMapper adminUserMapper, OidcTokenVerifier oidcVerifier,
                          CenterSessionStore centerSessions) {
        this.adminUserMapper = adminUserMapper;
        this.oidcVerifier = oidcVerifier;
        this.centerSessions = centerSessions;
    }

    /**
     * auth-center 服务端互调基址。
     * <p>activecode 部署在独立主机（内网 Debian .182）、只挂自己的 compose 网络，
     * **不能**用容器名 `auth-center`；必须走宿主 LAN 地址（与 LocalAccountReporter 同口径）。
     */
    @Value("${marschat.auth-center.base:}")
    private String authCenterBase;

    private static final String DEFAULT_AUTH_CENTER_BASE = "http://192.168.31.105:8085";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 独立账密登录 —— **身份与密码的唯一真源是 auth-center**（Phase 11，2026-09-15）。
     *
     * <p>改造前：查本地 {@code admin_user} → 用本地 salt 做 SHA-256 比对。这让账密与 SSO
     * 成为两套身份源，违背「统一登录、用户统一管理」。现改为：本服务把账密 **服务端代理转发**
     * 给 auth-center {@code POST /auth/login}，校验完全由中心完成；本地 {@code admin_user}
     * 降级为**影子表**（只保留账号存在性与最后登录时间，不再存密码、不再参与校验）。
     *
     * <p>🔴 三条硬约束：
     * <ol>
     *   <li>中心不可达时**绝不回退本地密码校验** —— 回退等于把「改中心密码/停用账号」失效，
     *       又造出一个影子真源。宁可登录不可用，也不制造越权。</li>
     *   <li>中心返回的 {@code data.user.password} 是哈希串，**禁止**读取、落库或透传。</li>
     *   <li>失败文案统一为「用户名或密码错误」，避免账号枚举。</li>
     * </ol>
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Map.of("success", false, "message", "用户名和密码不能为空");
        }

        Map<String, Object> res = proxyAjax("/auth/login",
                Map.of("username", username, "password", password), null);

        // 中心不可达 / 非 200：同一文案，避免账号枚举
        if (!isOk(res)) {
            if (isUnreachable(res)) {
                log.warn("账密登录失败：认证中心不可达 user={}", username);
                return Map.of("success", false, "message", "认证中心不可达，请稍后重试");
            }
            return Map.of("success", false, "message", "用户名或密码错误");
        }

        Object dataObj = res.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            return Map.of("success", false, "message", "用户名或密码错误");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> userNode = (data.get("user") instanceof Map)
                ? (Map<String, Object>) data.get("user") : null;
        if (userNode == null) {
            return Map.of("success", false, "message", "用户名或密码错误");
        }

        // 账号停用：中心 status=1 为启用
        Object statusObj = userNode.get("status");
        if (statusObj != null && !(statusObj instanceof Number n && n.intValue() == 1)) {
            log.info("账密登录被拒：账号已停用 user={}", username);
            return Map.of("success", false, "message", "账号已停用");
        }

        // 收敛本地影子：无则自动建档（不存密码），有则刷新最后登录时间
        AdminUser user = adminUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, username)
        );
        if (user == null) {
            Object nickname = userNode.get("nickname");
            String displayName = (nickname instanceof String s && !s.isBlank()) ? s : username;
            user = new AdminUser();
            user.setUsername(username);
            user.setCreateTime(LocalDateTime.now());
            // 密码已改由认证中心校验，本地不再保存任何口令材料：salt/password 仅填随机占位，
            // 保证即使有人绕过本方法直连本地表也无法用占位值登录（不存在与之匹配的明文）。
            user.setSalt(generateSalt());
            user.setPassword(hashPassword(UUID.randomUUID().toString(), user.getSalt()));
            adminUserMapper.insert(user);
            log.info("账密登录：中心身份首次进入，本地影子建档 user={}, displayName={}", username, displayName);
        }
        user.setLastLoginTime(LocalDateTime.now());
        adminUserMapper.updateById(user);

        // 暂存中心业务令牌（服务端内存，不下发浏览器）：供 AdminProxyController 以**用户本人
        // 身份**调中心 /admin/**（本系统用户管理页）。refreshToken 不落任何地方 —— 本应用不做续期，
        // 令牌过期即让前端重授权，避免在应用侧长期持有可换票的长效凭据。
        centerSessions.put(username, (String) data.get("accessToken"), asLong(data.get("expiresIn")));

        // 写入Session（下游 checkSession / changePassword / 各业务端点依赖此属性，保持不变）
        session.setAttribute("loginUser", user);
        log.info("用户登录成功(认证中心): {}, sessionId={}", username, session.getId());

        return Map.of("success", true, "username", username);
    }

    /** 中心 expiresIn 为毫秒；缺失或非数字时返回 0（由 CenterSessionStore 按 1 小时兜底）。 */
    private static long asLong(Object v) {
        return (v instanceof Number n) ? n.longValue() : 0L;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        // 登出即丢弃该用户的中心令牌：本应用不做中心续期，令牌仅服务于本次会话
        Object ssoUser = session.getAttribute("ssoUser");
        if (ssoUser instanceof String s) {
            centerSessions.remove(s);
        }
        Object loginUser = session.getAttribute("loginUser");
        if (loginUser instanceof AdminUser u) {
            centerSessions.remove(u.getUsername());
        }
        session.invalidate();
        return Map.of("success", true);
    }

    /**
     * 统一认证（auth-center）SSO 登录桥接。
     * 前端已完成 OIDC authorization_code + PKCE 流程，此处：服务端用 OidcTokenVerifier 对 RS256
     * access_token 验签（签名 + issuer + 过期），并校验 token 内用户名与请求用户名一致，通过后才建立
     * 与「账号密码登录」同口径的 HttpSession（激活码数据无用户隔离，统一以管理员身份进入）。
     * 不再接受无 token 的纯 username 声明，杜绝伪造管理员会话。
     */
    @PostMapping("/sso-login")
    public Map<String, Object> ssoLogin(@RequestBody Map<String, String> body, HttpSession session, HttpServletResponse response) {
        String ssoUsername = body.get("username");
        String accessToken = body.get("access_token");

        if (accessToken == null || accessToken.isBlank()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return Map.of("success", false, "message", "缺少 OIDC access_token");
        }

        JWTClaimsSet claims = oidcVerifier.verify(accessToken);
        if (claims == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return Map.of("success", false, "message", "access_token 验签失败");
        }

        // 🔴 遗留缺陷 F2（2026-09-15 修复）：原先取 sub 作为用户名、只在 sub 为空时才回退
        //    preferred_username。但中心 SAS 令牌的 **sub 是用户 ID**（数字），**username 才是
        //    登录用户名** —— 拿 sub 去比对会让「用户名一致」校验必然失败（或错把用户 ID 当账号），
        //    且下游的本地同名 / 中心账号映射都是**按 username 认领**的。
        //    现改为以 `username` 声明为主，回退 preferred_username，最后才是 sub。
        String tokenUsername = claimUsername(claims);
        if (tokenUsername == null || tokenUsername.isBlank()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return Map.of("success", false, "message", "token 中无用户名声明");
        }
        // 🔴 缺陷 D1（2026-09-17 修复）：原先要求「请求体 username == 令牌 username 声明」，
        //    不等即 403。但前端 activecode/sso.js 当时取的是 **id_token 的 sub**，而中心 SAS 的
        //    **sub 是用户 ID**（实测 "1"）、**username 才是登录名**（实测 "admin"）→ 两者必然不等
        //    → 整条 SSO 回调恒失败（页面：统一认证登录失败 / username 与 token 不一致）。
        //    现改为：**身份一律以验签通过的令牌声明为准**，请求体 username 仅作客户端自述，
        //    不一致时记 WARN 并以令牌为准，不再阻断。
        //    安全性不降级：会话主体来自 RS256 验签通过的令牌，请求体根本无法影响它；
        //    反之继续硬校验，只会把「客户端取错字段」放大成「整条 SSO 通道不可用」。
        if (ssoUsername != null && !ssoUsername.isBlank() && !ssoUsername.equals(tokenUsername)) {
            log.warn("[SSO] 客户端自述 username={} 与令牌声明不一致，以令牌为准: tokenUsername={} sub={} aud={}",
                    ssoUsername, tokenUsername, claims.getSubject(), claims.getAudience());
        }

        // 统一走「映射收敛」判定（同名 → 超管例外 → 中心账号映射 → 403）。
        // 🔴 改造前此处是「未匹配同名则回退到任意本地管理员(LIMIT 1)」—— 等于任何持有有效中心
        //    token 的用户都能以 admin 进入激活码系统（与 cosmic G2 同类的越权默认值），已收敛。
        // platformAdminHint=null → 由 establishSession 用该 RS256 令牌查中心平台角色。
        log.info("[SSO] 令牌身份已确认: username={} sub={} aud={}，进入映射收敛判定",
                tokenUsername, claims.getSubject(), claims.getAudience());
        return establishSession(tokenUsername, accessToken, null, session, response);
    }

    @GetMapping("/session")
    public Map<String, Object> checkSession(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("loginUser");
        if (user != null) {
            return Map.of("success", true, "username", user.getUsername());
        }
        return Map.of("success", false);
    }

    /**
     * 本地改密端点已下线（Phase 12 · P1-4 / F4）。
     *
     * <p>Phase 11 起口令唯一真源在统一认证中心，本库 {@code admin_user} 只是影子。
     * 历史实现会做「本地 salt+hash 比对 + 本地改密」：用户提交后中心口令纹丝不动，
     * 只有本机影子被改 —— 表面「修改成功」，实则制造身份分裂（同一用户名两套口令）。
     *
     * <p>处置：一律 410 Gone + 引导走中心「忘记密码」。<b>严禁</b>恢复本地比对。
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody(required = false) Map<String, String> body,
                                                              HttpSession session) {
        log.warn("[auth] 本地改密端点已下线，拒绝请求（session={}）——口令统一由认证中心管理",
                session != null && session.getAttribute("loginUser") != null);
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of("success", false, "code", 410,
                        "message", "口令由统一认证中心统一管理，请通过登录页「忘记密码」或联系平台管理员重置"));
    }

    /**
     * 确保默认管理员账号在**影子表**中存在（只建不覆盖）。
     *
     * <p>🔴 2026-09-15 修正：改造前这里会用硬编码默认口令建档，等于把刚刚拆掉的本地密码体系
     * 又装回影子表 —— 一个写死在源码里、全网同值的口令随时可能被回潮利用。独立账密登录统一到
     * 认证中心后，口令由中心持有，**本地不再保存任何口令材料**。
     *
     * <p>现语义：仅保证 username 在影子表里有一行（供会话/审计外键引用），password/salt 一律
     * 填随机占位符（与 {@link #login} 自动建档同口径，不存在与之匹配的明文，因此即使有人绕过
     * 本服务直连影子表也无法登录）。已有账号**绝不覆盖**其密码字段。
     *
     * <p>能否真正登录取决于该 username 在 auth-center 是否存在且启用 —— 本地建档只是影子。
     */
    public void initDefaultAdmin() {
        String defaultAdmin = "admin";
        AdminUser exist = adminUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, defaultAdmin)
        );
        if (exist != null) {
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setUsername(defaultAdmin);
        // 口令已改由认证中心校验，本地不存密码：salt/password 仅填随机占位（同 login() 建档口径）。
        admin.setSalt(generateSalt());
        admin.setPassword(hashPassword(UUID.randomUUID().toString(), admin.getSalt()));
        admin.setCreateTime(LocalDateTime.now());
        adminUserMapper.insert(admin);
        log.info("初始化默认管理员影子账号: {}（口令由认证中心持有，本地仅存随机占位）", defaultAdmin);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String combined = salt + password + salt;
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("密码哈希失败", e);
        }
    }

    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 统一登录三方式补齐（L1 / T6，2026-09-15）：邮箱验证码登录 + 忘记密码
    //
    // activecode 是 6 个自研应用里唯一掉队的：只支持「本地账密 + SSO」，没有邮箱码登录、
    // 没有自助改密，UMD 也停在 0.6.9。本段补齐最低线，并顺带收敛一个越权默认值（见 resolveLocalAdmin）。
    //
    // 实现方式 = **服务端代理**（与 portal BFF 同思路）：浏览器不接受中心 token，
    // 由本服务用服务器身份/用户 token 调 auth-center，再把结果换成自有 HttpSession。
    // 原因：activecode 的会话模型是 HttpSession（非 JWT），且它在独立网络（192.168.31.182），
    // 中心接口必须走内网地址 192.168.31.105:8085（不能走公网域名，见 .woodpecker 铁律）。
    // ══════════════════════════════════════════════════════════════════════════

    /** 邮箱验证码登录：请求发码（匿名端点，转发 auth-center） */
    @PostMapping("/mail-login/send-code")
    public Map<String, Object> sendMailLoginCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return Map.of("success", false, "message", "邮箱不能为空");
        }
        return proxyJson("/auth/mail-login/send-code", Map.of("email", email), null);
    }

    /** 忘记密码：请求发码（匿名端点，转发 auth-center；中心侧防枚举，永远返回成功） */
    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return Map.of("success", false, "message", "邮箱不能为空");
        }
        return proxyJson("/auth/forgot-password", Map.of("email", email), null);
    }

    /** 忘记密码：用邮箱码重置（匿名端点，转发 auth-center） */
    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        String newPassword = body.get("newPassword");
        if (email == null || email.isBlank() || code == null || code.isBlank()
                || newPassword == null || newPassword.length() < 6) {
            return Map.of("success", false, "message", "参数不完整（新密码至少 6 位）");
        }
        return proxyJson("/auth/reset-password",
                Map.of("email", email, "code", code, "newPassword", newPassword), null);
    }

    /**
     * 邮箱验证码登录：验码换票 → 验签 → 账号映射 → 建立本应用会话。
     *
     * <p>与 {@code /sso-login} 共用同一套「映射收敛」判定（见 {@link #resolveLocalAdmin}），
     * 两条登录径对「谁能进本应用」的答案是同一个。
     */
    @PostMapping("/mail-login")
    public Map<String, Object> mailLogin(@RequestBody Map<String, String> body,
                                         HttpSession session, HttpServletResponse response) {
        String email = body.get("email");
        String code = body.get("code");
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return Map.of("success", false, "message", "邮箱与验证码不能为空");
        }

        Map<String, Object> res = proxyAjax("/auth/mail-login", Map.of("email", email, "code", code), null);
        if (!isOk(res)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return Map.of("success", false, "message", String.valueOf(res.getOrDefault("message", "邮箱验证码登录失败")));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) res.get("data");
        String accessToken = data == null ? null : (String) data.get("accessToken");
        if (accessToken == null || accessToken.isBlank()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return Map.of("success", false, "message", "中心未返回 access_token");
        }

        // ⚠️ 血泪坑（2026-09-15 实测踩中）：**不能**用 OidcTokenVerifier 验这个 token。
        //   `/auth/mail-login` 是 auth-center 的**业务端点**，返回的是它自签的 **HS384** 令牌
        //   （header {"alg":"HS384"}，见 AuthServiceImpl 的 JwtUtil；用于访问中心业务 API），
        //   而 OidcTokenVerifier 只认 SAS 签发的 **RS256** OIDC 令牌 —— 两者算法与签发者都不同，
        //   拿后者验前者必然「验签失败」（表现为：验证码正确、密码也正确，却卡在最后一步）。
        //   SSO 径（/sso-login）拿到的才是 RS256，所以那里必须验签；
        //   本径是**服务端互调**（我们直接请求中心内网地址并拿到响应），响应体本身就是可信来源，
        //   故直接采信 data.user 的身份字段，不做二次验签。
        @SuppressWarnings("unchecked")
        Map<String, Object> userNode = (data != null && data.get("user") instanceof Map)
                ? (Map<String, Object>) data.get("user") : null;
        String centerUsername = userNode == null ? null : (String) userNode.get("username");
        if (centerUsername == null || centerUsername.isBlank()) {
            centerUsername = claimUsernameUnverified(accessToken);
        }
        if (centerUsername == null || centerUsername.isBlank()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return Map.of("success", false, "message", "中心响应中缺少用户名");
        }
        // 平台角色：中心已在 user.role 给出（superadmin / admin / user），无需再查一次
        String centerRole = userNode == null ? null : (String) userNode.get("role");
        Boolean platformAdmin = ("superadmin".equals(centerRole) || "admin".equals(centerRole))
                ? Boolean.TRUE : Boolean.FALSE;
        log.info("邮箱验证码登录：中心身份已确认 user={}, role={}", centerUsername, centerRole);
        return establishSession(centerUsername, accessToken, platformAdmin, session, response);
    }

    /**
     * 解析「中心身份 → 本应用本地管理员」，并建立 HttpSession。
     *
     * <h3>判定顺序（与 cosmic §33.12 同一套，2026-09-15 收敛）</h3>
     * <ol>
     *   <li><b>本地同名</b>：本地 admin_user 里有同名账号 → 用它（管理员同时也是中心账号的常见情形）；</li>
     *   <li><b>超管例外</b>：中心平台 admin/superadmin → 映射到本地管理员（超管本就是每个系统的管理员）；</li>
     *   <li><b>中心账号映射</b>：查 auth-center {@code /user/mapping?clientId=marschat-activecode}
     *       （以**用户本人 token** 查询，见 §33.12.1），命中则用映射到的本地账号；</li>
     *   <li>其余 → <b>403</b>。</li>
     * </ol>
     *
     * <p>🔴 修复的越权默认值：改造前 {@code /sso-login} 在「同名不匹配」时**回退到任意一个本地
     * 管理员**（{@code LIMIT 1}）—— 等于**任何**持有有效中心 token 的用户都能以 admin 身份进入
     * 激活码系统。这与 cosmic 已修的 G2（「非本池用户 → admin」）是同一类缺陷，本轮一并收敛。
     */
    private Map<String, Object> establishSession(String centerUsername, String accessToken,
                                                 Boolean platformAdminHint,
                                                 HttpSession session, HttpServletResponse response) {
        // ① 本地同名
        AdminUser user = adminUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, centerUsername));

        boolean platformAdmin = false;
        if (platformAdminHint != null) {
            // 邮箱码径：中心响应已带 user.role，直接采信（省一次互调）
            platformAdmin = platformAdminHint;
        } else if (user == null) {
            // SSO 径：RS256 OIDC 令牌可安全用于查中心权限点，取平台角色判定超管例外
            Set<String> roles = fetchPlatformRoles(accessToken);
            platformAdmin = roles.contains("admin") || roles.contains("superadmin");
        }

        // ② 超管例外
        if (user == null && platformAdmin) {
            user = adminUserMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUser>()
                            .last("LIMIT 1"));
        }

        // ③ 中心账号映射（用户本人 token 查自己在本应用的绑定）
        if (user == null) {
            String mapped = fetchMappedLocalAccount(accessToken);
            if (mapped != null && !mapped.isBlank()) {
                user = adminUserMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUser>()
                                .eq(AdminUser::getUsername, mapped));
            }
        }

        if (user == null) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return Map.of("success", false,
                    "message", "账号未绑定：请联系管理员在「统一认证中心 → 账号映射」中完成绑定");
        }

        user.setLastLoginTime(LocalDateTime.now());
        adminUserMapper.updateById(user);
        // 登记中心令牌（SSO 的 RS256 OIDC token / 邮箱码径的中心业务 token 均可调 /admin/**），
        // 供 AdminProxyController 以用户本人身份访问「本系统用户」。SSO 径拿不到 expiresIn，
        // 传 0 由 CenterSessionStore 按 1 小时兜底。
        centerSessions.put(centerUsername, accessToken, 0L);
        session.setAttribute("loginUser", user);
        session.setAttribute("ssoUser", centerUsername);
        log.info("统一登录成功(邮箱码/SSO): centerUser={}, mappedAdmin={}, sessionId={}",
                centerUsername, user.getUsername(), session.getId());
        return Map.of("success", true, "username", user.getUsername(), "ssoUser", centerUsername);
    }

    /** 取中心平台角色（供超管例外判定）；失败返回空集（退化为「按映射/同名走」）。 */
    private Set<String> fetchPlatformRoles(String accessToken) {
        Map<String, Object> res = proxyAjax("/auth/permissions?client=marschat-activecode", null, accessToken);
        if (!isOk(res)) {
            return Set.of();
        }
        Object dataObj = res.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            return Set.of();
        }
        Object roles = ((Map<?, ?>) data).get("platformRoles");
        if (!(roles instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (Object r : list) {
            out.add(String.valueOf(r));
        }
        return out;
    }

    /** 查「用户本人」在本应用的账号映射（auth-center GET /user/mapping?clientId=...）。 */
    private String fetchMappedLocalAccount(String accessToken) {
        Map<String, Object> res = proxyAjax("/user/mapping?clientId=marschat-activecode", null, accessToken);
        if (!isOk(res)) {
            return null;
        }
        Object dataObj = res.get("data");
        if (dataObj instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object local = first.get("local_account");
            if (local == null) {
                local = first.get("localAccount");
            }
            return local == null ? null : String.valueOf(local);
        }
        return null;
    }

    /**
     * 从 SAS 令牌取**登录用户名**：{@code username} 声明为主 → {@code preferred_username} → {@code sub}。
     *
     * <p>⚠️ 顺序不可颠倒：中心 SAS 令牌的 {@code sub} 是**用户 ID**，不是用户名。
     * 账号映射表（app_account_mapping）与本地 admin_user 都按 username 认领，用 sub 会全错。
     * （缺陷 F2，2026-09-15 修复；/sso-login 与 mail-login 兜底共用此口径。）
     */
    private String claimUsername(JWTClaimsSet claims) {
        String name = null;
        try {
            name = claims.getStringClaim("username");
        } catch (Exception e) {
            name = null;
        }
        if (name == null || name.isBlank()) {
            try {
                name = claims.getStringClaim("preferred_username");
            } catch (Exception e) {
                name = null;
            }
        }
        if (name == null || name.isBlank()) {
            name = claims.getSubject();
        }
        return name;
    }

    /**
     * **不验签**地从令牌 payload 取用户名 —— 只允许用在「我们自己的服务端互调响应」这类可信来源上，
     * 作为 {@code data.user} 缺失时的兜底（中心响应结构演进 / 字段改名）。
     *
     * <p>⚠️ 严禁用于任何来自浏览器/客户端的令牌 —— 那等于把身份交给了攻击者。
     * 之所以这里安全：令牌是我们刚刚向中心内网地址请求、并从**响应体**里拿到的，
     * 且该次调用已由中心完成了验证码校验。
     */
    private String claimUsernameUnverified(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] json = Base64.getUrlDecoder().decode(parts[1]);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = MAPPER.readValue(json, Map.class);
            Object u = payload.get("username");
            if (u == null) {
                u = payload.get("sub");
            }
            return u == null ? null : String.valueOf(u);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isOk(Map<String, Object> res) {
        Object code = res == null ? null : res.get("code");
        return code instanceof Number n && n.intValue() == 200;
    }

    /**
     * 是否「中心不可达」——{@link #proxyAjax} 在网络/解析失败时统一返回 code=502。
     * 与「账号密码错」区分开：前者要给用户可操作的提示，后者必须保持不可枚举的统一文案。
     */
    private boolean isUnreachable(Map<String, Object> res) {
        Object code = res == null ? null : res.get("code");
        return code instanceof Number n && n.intValue() == 502;
    }

    /** 转发到 auth-center，返回统一的 {@code {success, ...}} 形态（供发码/改密这类无 token 端点）。 */
    private Map<String, Object> proxyJson(String path, Map<String, Object> payload, String bearer) {
        Map<String, Object> res = proxyAjax(path, payload, bearer);
        if (isOk(res)) {
            return Map.of("success", true, "message", String.valueOf(res.getOrDefault("message", "success")));
        }
        return Map.of("success", false, "message", String.valueOf(res.getOrDefault("message", "请求失败")));
    }

    /** 原始转发：返回 auth-center 的完整响应体（Map）；网络/解析失败返回 code=502。 */
    private Map<String, Object> proxyAjax(String path, Map<String, Object> payload, String bearer) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create((authCenterBase == null || authCenterBase.isBlank()
                            ? DEFAULT_AUTH_CENTER_BASE : authCenterBase).replaceAll("/+$", "") + path))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json");
            if (bearer != null && !bearer.isBlank()) {
                builder.header("Authorization", "Bearer " + bearer);
            }
            if (payload == null) {
                builder.GET();
            } else {
                builder.POST(HttpRequest.BodyPublishers.ofString(
                        MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8));
            }
            HttpResponse<String> resp = HTTP.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            @SuppressWarnings("unchecked")
            Map<String, Object> body = MAPPER.readValue(resp.body(), Map.class);
            return body;
        } catch (Exception e) {
            log.warn("转发 auth-center 失败 path={}: {}", path, e.getMessage());
            return Map.of("code", 502, "message", "统一认证中心不可达：" + e.getMessage());
        }
    }
}
