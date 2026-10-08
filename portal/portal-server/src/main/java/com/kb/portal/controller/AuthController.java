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

        // 🔴 2026-10-06 Phase 13：保存中心在账密登录时签发的 access_token。
        //    此前只保留了 portal 自签的 HS256 会话 token，导致账密/邮箱码登录的**管理员
        //    调管理面必 401**（无 SSO refresh_token 可换）。而中心的 loginAsUser 与 SSO 换票
        //    走同一个 jwtTokenProvider、同样返回可直调 /admin/** 的 access_token
        //    （JwtAuthenticationFilter 明确支持 legacy 分支 + type==access 校验）。
        //    ⇒ 这里存下来，凭据链与 SSO 路径完全统一，且中心审计到的是**本人**而非服务账号
        //    （旧 callAdmin 的服务身份兜底会审计失真，已废弃）。
        authCenterService.storeLoginAccessToken(user.getId(), resp);

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

    /**
     * 登出 —— 服务端侧凭据清理（T-ENG-8，2026-10-07）。
     *
     * <p>🔴 <b>修复前的缺陷</b>：本端点此前是<b>空实现</b>（直接 {@code Result.ok()}），
     * 而前端 {@code userStore.logout()} 也<b>根本没调它</b>（只清 localStorage + 跳 SLO）。
     * 后果是 2026-10-06 新增 {@link AuthCenterService#loginAccessTokens} 后，
     * <b>用户点了「退出登录」，服务端仍握着一枚可直调 {@code /admin/**} 的中心 access_token</b>
     * —— 客户端以为已登出，服务端侧凭据却原封不动（凭据残留）。
     *
     * <p><b>现在的链路</b>（服务端这一段在 SLO 跳转<b>之前</b>由前端主动调用）：
     * <ol>
     *   <li>取本进程持有的中心 access_token（账密池优先，其次 refresh 池换票）；</li>
     *   <li>调中心 {@code POST /auth/logout} 把它写进 {@code jwt_blacklist}（吊销）；</li>
     *   <li>清本进程两个凭据池 —— <b>无论第 2 步成功与否都要清</b>；</li>
     *   <li>客户端清 localStorage → SLO 销毁 IdP 会话。</li>
     * </ol>
     *
     * <p>⚠️ <b>本端点全程 best-effort</b>：任何一步失败都<b>不影响</b>返回 200。
     * 用户"退得掉"是硬需求，不能因为中心抖动就卡住登出。
     *
     * <p>⚠️ 依赖 {@code JwtInterceptor} 注入的 {@code userId}（见 WebMvcConfig：本路径已被拦截）。
     * 拿不到 userId（如 token 已失效被拦截器挡下）时静默返回 200 —— 此时本就没有凭据可清。
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        Object rawUserId = request == null ? null : request.getAttribute("userId");
        if (!(rawUserId instanceof Long userId)) {
            // 无 userId ⇒ 拦截器已判定会话不可用，本进程不会有该用户的残留凭据
            return Result.ok();
        }

        String accessToken = null;
        try {
            accessToken = authCenterService.resolveAccessToken(userId);
        } catch (Exception e) {
            log.warn("登出：取中心凭据失败（跳过吊销，仅清本地池）: {}", e.getMessage());
        }

        boolean revoked = authCenterService.revokeAccessToken(accessToken);
        // 无论吊销是否成功都要清池：池是本进程的可利用面，优先级高于中心的拉黑结果
        authCenterService.clearUserCredentials(userId);
        log.info("登出：服务端凭据已清理 userId={} hadToken={} revoked={}",
                userId, accessToken != null && !accessToken.isBlank(), revoked);
        return Result.ok();
    }

    /**
     * 本地改密端点已下线（Phase 12 · P1-4 / F4）。
     *
     * <p>Phase 11 起口令唯一真源在统一认证中心，本库 {@code sys_user} 只是影子（不决定身份）。
     * 但历史实现仍在做「本地口令比对 + 本地改密」：用户提交后<b>中心口令纹丝不动</b>，
     * 只有 portal 影子被改 —— 用户以为改了平台口令，实际制造了身份分裂（同一用户名两套口令）。
     * 2026-09-17 浏览器实测复现：改密接口返回 200 成功，中心口令不变。
     *
     * <p>处置：一律 410 Gone，引导用户走中心「忘记密码」/ 中心管理台重置。
     * <b>严禁</b>恢复本地比对（等于给身份分裂留后门）。
     * 正确改密范式见 kb-web：{@code PUT /kb/api/user/password} 经 kb-gateway 代理到中心。
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        throw new BusinessException(410,
                "口令由统一认证中心统一管理，请通过登录页「忘记密码」或联系平台管理员重置");
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
