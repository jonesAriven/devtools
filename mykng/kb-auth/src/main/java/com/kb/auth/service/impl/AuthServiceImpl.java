package com.kb.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.auth.dto.LoginRequest;
import com.kb.auth.dto.LoginResponse;
import com.kb.auth.dto.RefreshRequest;
import com.kb.auth.entity.JwtBlacklist;
import com.kb.auth.entity.RefreshToken;
import com.kb.auth.entity.User;
import com.kb.auth.mapper.JwtBlacklistMapper;
import com.kb.auth.mapper.RefreshTokenMapper;
import com.kb.auth.mapper.UserMapper;
import com.kb.auth.security.JwtTokenProvider;
import com.kb.auth.service.AuthService;
import com.kb.common.event.KbEvent;
import com.kb.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtBlacklistMapper jwtBlacklistMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String EVENT_CHANNEL = "kb:events";
    private static final ObjectMapper objectMapper = new ObjectMapper();

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

        // 通过 Redis Pub/Sub 发布登录事件（替代跨服务的 OperationLogService）
        publishEvent("user.login", user.getId(), Map.of("username", user.getUsername()));

        log.info("用户登录成功 userId={}, username={}", user.getId(), user.getUsername());
        return new LoginResponse(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration());
    }

    @Override
    @Transactional
    public void logout(String accessToken) {
        if (accessToken == null) {
            return;
        }

        if (jwtTokenProvider.validateToken(accessToken)) {
            JwtBlacklist blacklist = new JwtBlacklist();
            blacklist.setToken(accessToken);
            blacklist.setExpireAt(jwtTokenProvider.getExpirationFromToken(accessToken)
                    .toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            jwtBlacklistMapper.insert(blacklist);

            Long userId = jwtTokenProvider.getUserIdFromToken(accessToken);
            publishEvent("user.logout", userId, Map.of());
            log.info("用户登出成功 userId={}", userId);
        }
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

    /**
     * 通过 Redis Pub/Sub 发布事件，替代原单体中的 OperationLogService.log()
     */
    private void publishEvent(String event, Long userId, Map<String, Object> payload) {
        try {
            KbEvent kbEvent = new KbEvent(event, userId, payload);
            kbEvent.setSource("kb-auth");
            redisTemplate.convertAndSend(EVENT_CHANNEL, objectMapper.writeValueAsString(kbEvent));
        } catch (Exception e) {
            log.warn("发布事件失败 event={}, userId={}: {}", event, userId, e.getMessage());
        }
    }
}
