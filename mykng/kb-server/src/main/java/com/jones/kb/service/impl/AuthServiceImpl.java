package com.jones.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jones.kb.common.BusinessException;
import com.jones.kb.dto.auth.LoginRequest;
import com.jones.kb.dto.auth.LoginResponse;
import com.jones.kb.dto.auth.RefreshRequest;
import com.jones.kb.entity.JwtBlacklist;
import com.jones.kb.entity.RefreshToken;
import com.jones.kb.entity.User;
import com.jones.kb.mapper.JwtBlacklistMapper;
import com.jones.kb.mapper.RefreshTokenMapper;
import com.jones.kb.mapper.UserMapper;
import com.jones.kb.security.JwtTokenProvider;
import com.jones.kb.service.AuthService;
import com.jones.kb.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtBlacklistMapper jwtBlacklistMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("用户已被禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setToken(refreshToken);
        rt.setExpireAt(LocalDateTime.now().plusDays(7));
        refreshTokenMapper.insert(rt);

        operationLogService.log(user.getId(), "LOGIN", null, null, "用户登录", null);

        return new LoginResponse(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }

    @Override
    @Transactional
    public void logout(String accessToken) {
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            JwtBlacklist blacklist = new JwtBlacklist();
            blacklist.setToken(accessToken);
            blacklist.setExpireAt(jwtTokenProvider.getExpirationFromToken(accessToken)
                    .toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            jwtBlacklistMapper.insert(blacklist);
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(accessToken);
        operationLogService.log(userId, "LOGOUT", null, null, "用户登出", null);
    }

    @Override
    @Transactional
    public LoginResponse refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(401, "RefreshToken无效或已过期");
        }

        String type = jwtTokenProvider.getTokenType(refreshToken);
        if (!"refresh".equals(type)) {
            throw new BusinessException(401, "无效的Token类型");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        RefreshToken storedToken = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshToken>()
                        .eq(RefreshToken::getUserId, userId)
                        .eq(RefreshToken::getToken, refreshToken));
        if (storedToken == null) {
            throw new BusinessException(401, "RefreshToken不存在");
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == 0) {
            throw new BusinessException(401, "用户不存在或已被禁用");
        }

        refreshTokenMapper.deleteById(storedToken.getId());

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setToken(newRefreshToken);
        rt.setExpireAt(LocalDateTime.now().plusDays(7));
        refreshTokenMapper.insert(rt);

        return new LoginResponse(newAccessToken, newRefreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }
}
