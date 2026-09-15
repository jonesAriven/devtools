package com.kb.portal.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.marschat.common.exception.BusinessException;
import com.marschat.common.result.Result;
import com.kb.portal.dto.ChangePasswordRequest;
import com.kb.portal.dto.LoginRequest;
import com.kb.portal.dto.LoginResponse;
import com.kb.portal.entity.SysUser;
import com.kb.portal.mapper.SysUserMapper;
import com.kb.portal.service.AuthCenterService;
import com.kb.portal.util.JwtUtil;
import com.kb.portal.util.PasswordUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper sysUserMapper;
    private final PasswordUtil passwordUtil;
    private final JwtUtil jwtUtil;
    private final AuthCenterService authCenterService;

    /**
     * 独立账密登录（BFF 转发）。
     *
     * <p>🔴 密码校验已统一到认证中心：本方法**不再比对本地 sys_user.password**。
     * 此前本地表是第二套密码真源（中心 42 个身份 vs 本地 13 个账号），
     * 中心新建的用户根本登不进 portal。现在本地表只作**影子**（承载 portal 自有的
     * id/角色/外键），中心校验通过后按 username 同步，缺失则自动建档。
     *
     * <p>中心不可达时 fail-closed 报 503，**不回退本地密码**（回退等于重新分裂两套密码）。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        JsonNode resp;
        try {
            resp = authCenterService.loginAsUser(request.getUsername(), request.getPassword());
        } catch (Exception e) {
            log.warn("认证中心账密登录不可达: {}", e.getMessage());
            throw new BusinessException(503, "认证中心不可达，请稍后重试");
        }

        // 统一文案，避免暴露账号是否存在
        if (resp.path("code").asInt() != 200) {
            throw new BusinessException("用户名或密码错误");
        }

        JsonNode u = resp.path("data").path("user");
        if (u.path("status").asInt(1) != 1) {
            throw new BusinessException("账号已停用");
        }

        String username = u.path("username").asText(request.getUsername());
        String nickname = u.path("nickname").isMissingNode() || u.path("nickname").isNull()
                ? username : u.path("nickname").asText(username);
        String role = u.path("role").isMissingNode() || u.path("role").isNull()
                ? "user" : u.path("role").asText("user");
        // auth_uid：中心 user.id（与 SsoController.mailLogin 同源同语义；SSO 走的是 token 的 uid/sub claim）
        String authUid = u.path("id").isMissingNode() || u.path("id").isNull()
                ? null : u.path("id").asText(null);
        if (authUid != null && authUid.isBlank()) {
            authUid = null;
        }

        SysUser user = shadowUser(username, nickname, role, authUid);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(),
                user.getRole() == null ? "user" : user.getRole());
        LoginResponse response = new LoginResponse(
                token,
                user.getUsername(),
                user.getNickname() != null ? user.getNickname() : user.getUsername(),
                user.getRole() == null ? "user" : user.getRole()
        );
        return Result.ok(response);
    }

    /**
     * 收敛本地影子账号：以中心身份为准同步 nickname/role/auth_uid，缺失则自动建档。
     *
     * <p>🔴 为什么必须回填 auth_uid：账密登录与 SSO/邮箱码登录是**同一个中心身份**的两条入口。
     * 若账密路径不写 auth_uid，SSO 路径就会走「按 username 回填」的存量迁移分支，
     * 两条路径对同一用户的本地记录视图不一致，身份一致性守卫会误判（甚至重复建档）。
     *
     * <p>按 username 查询（不加 status=1 过滤 —— 否则被停用的存量账号会查不到而
     * 触发重复建档，撞 username 唯一索引）。
     */
    private SysUser shadowUser(String username, String nickname, String role, String authUid) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .last("LIMIT 1")
        );
        if (user == null) {
            user = new SysUser();
            user.setUsername(username);
            // 该字段已不再用于登录校验（密码真源在认证中心），仅为满足非空约束填随机占位值
            user.setPassword(passwordUtil.encode(UUID.randomUUID().toString()));
            user.setNickname(nickname);
            user.setStatus(1);
            user.setRole(role);
            user.setAuthUid(authUid);
            sysUserMapper.insert(user);
            log.info("按认证中心身份自动建档 portal 影子账号: {} (authUid={})", username, authUid);
            return user;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已停用");
        }
        boolean dirty = false;
        if (nickname != null && !nickname.equals(user.getNickname())) {
            user.setNickname(nickname);
            dirty = true;
        }
        if (role != null && !role.equals(user.getRole())) {
            user.setRole(role);
            dirty = true;
        }
        // 存量影子补写（幂等）：已有值不动，避免与 SSO 侧已绑定的标识冲突
        if (authUid != null && (user.getAuthUid() == null || user.getAuthUid().isBlank())) {
            user.setAuthUid(authUid);
            dirty = true;
        }
        if (dirty) {
            sysUserMapper.updateById(user);
        }
        return user;
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (!passwordUtil.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }

        user.setPassword(passwordUtil.encode(request.getNewPassword()));
        sysUserMapper.updateById(user);

        return Result.ok();
    }

    @GetMapping("/userinfo")
    public Result<LoginResponse> userinfo(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Long userId = jwtUtil.getUserId(token);
            String username = jwtUtil.getUsername(token);
            if (userId != null && username != null) {
                SysUser user = sysUserMapper.selectById(userId);
                if (user != null && user.getStatus() == 1) {
                    return Result.ok(new LoginResponse(
                            null,
                            user.getUsername(),
                            user.getNickname() != null ? user.getNickname() : user.getUsername(),
                            user.getRole() == null ? "user" : user.getRole()
                    ));
                }
            }
        }
        throw new BusinessException(401, "未登录或登录已过期");
    }
}
