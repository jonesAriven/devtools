package com.kb.portal.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marschat.common.exception.BusinessException;
import com.marschat.common.result.Result;
import com.kb.portal.dto.ChangePasswordRequest;
import com.kb.portal.dto.LoginRequest;
import com.kb.portal.dto.LoginResponse;
import com.kb.portal.entity.SysUser;
import com.kb.portal.mapper.SysUserMapper;
import com.kb.portal.util.JwtUtil;
import com.kb.portal.util.PasswordUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper sysUserMapper;
    private final PasswordUtil passwordUtil;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
                        .eq(SysUser::getStatus, 1)
                        .last("LIMIT 1")
        );

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!passwordUtil.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        LoginResponse response = new LoginResponse(
                token,
                user.getUsername(),
                user.getNickname() != null ? user.getNickname() : user.getUsername()
        );
        return Result.ok(response);
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
                            user.getNickname() != null ? user.getNickname() : user.getUsername()
                    ));
                }
            }
        }
        throw new BusinessException(401, "未登录或登录已过期");
    }
}
