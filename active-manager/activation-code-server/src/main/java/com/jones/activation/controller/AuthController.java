package com.jones.activation.controller;

import com.jones.activation.entity.AdminUser;
import com.jones.activation.mapper.AdminUserMapper;
import com.jones.activation.util.OidcTokenVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/activecode/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AdminUserMapper adminUserMapper;
    private final OidcTokenVerifier oidcVerifier;

    public AuthController(AdminUserMapper adminUserMapper, OidcTokenVerifier oidcVerifier) {
        this.adminUserMapper = adminUserMapper;
        this.oidcVerifier = oidcVerifier;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Map.of("success", false, "message", "用户名和密码不能为空");
        }

        AdminUser user = adminUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, username)
        );

        if (user == null) {
            return Map.of("success", false, "message", "用户名或密码错误");
        }

        String hashed = hashPassword(password, user.getSalt());
        if (!hashed.equals(user.getPassword())) {
            return Map.of("success", false, "message", "用户名或密码错误");
        }

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        adminUserMapper.updateById(user);

        // 写入Session
        session.setAttribute("loginUser", user);
        log.info("用户登录成功: {}, IP: {}", username, session.getId());

        return Map.of("success", true, "username", username);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Map.of("success", true);
    }

    /**
     * 统一认证（auth-center / kb-auth）SSO 登录桥接。
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

        // SAS access_token 的 sub 即登录用户名；回退 preferred_username
        String tokenUsername = claims.getSubject();
        if (tokenUsername == null || tokenUsername.isBlank()) {
            try {
                tokenUsername = claims.getStringClaim("preferred_username");
            } catch (Exception e) {
                tokenUsername = null;
            }
        }
        if (tokenUsername == null || tokenUsername.isBlank()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return Map.of("success", false, "message", "token 中无用户名声明");
        }
        if (ssoUsername == null || ssoUsername.isBlank() || !ssoUsername.equals(tokenUsername)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return Map.of("success", false, "message", "username 与 token 不一致");
        }

        // 优先映射到同名管理员账号；激活码数据无用户隔离，未匹配时回退到既有管理员账号
        AdminUser user = adminUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, ssoUsername)
        );
        if (user == null) {
            user = adminUserMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUser>()
                            .last("LIMIT 1")
            );
        }
        if (user == null) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return Map.of("success", false, "message", "未找到可映射的管理员账号");
        }

        user.setLastLoginTime(LocalDateTime.now());
        adminUserMapper.updateById(user);

        session.setAttribute("loginUser", user);
        session.setAttribute("ssoUser", ssoUsername);
        log.info("SSO 登录成功: ssoUser={}, mappedAdmin={}, sessionId={}", ssoUsername, user.getUsername(), session.getId());

        return Map.of("success", true, "username", user.getUsername(), "ssoUser", ssoUsername);
    }

    @GetMapping("/session")
    public Map<String, Object> checkSession(HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("loginUser");
        if (user != null) {
            return Map.of("success", true, "username", user.getUsername());
        }
        return Map.of("success", false);
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@RequestBody Map<String, String> body, HttpSession session) {
        AdminUser user = (AdminUser) session.getAttribute("loginUser");
        if (user == null) {
            return Map.of("success", false, "message", "未登录");
        }

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (oldPassword == null || newPassword == null || newPassword.length() < 6) {
            return Map.of("success", false, "message", "密码不能为空且新密码至少6位");
        }

        String hashedOld = hashPassword(oldPassword, user.getSalt());
        if (!hashedOld.equals(user.getPassword())) {
            return Map.of("success", false, "message", "原密码错误");
        }

        String newSalt = generateSalt();
        String newHashed = hashPassword(newPassword, newSalt);
        user.setSalt(newSalt);
        user.setPassword(newHashed);
        adminUserMapper.updateById(user);

        return Map.of("success", true, "message", "密码修改成功");
    }

    /**
     * 初始化默认管理员账号
     */
    public void initDefaultAdmin() {
        Long count = adminUserMapper.selectCount(null);
        if (count == 0) {
            AdminUser admin = new AdminUser();
            admin.setUsername("admin");
            String salt = generateSalt();
            admin.setSalt(salt);
            admin.setPassword(hashPassword("admin123", salt));
            admin.setCreateTime(LocalDateTime.now());
            adminUserMapper.insert(admin);
            log.info("初始化默认管理员账号: admin / admin123");
        }
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
}
