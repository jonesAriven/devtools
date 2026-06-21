package com.kb.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kb.auth.dto.LoginRequest;
import com.kb.auth.dto.LoginResponse;
import com.kb.auth.entity.User;
import com.kb.auth.mapper.JwtBlacklistMapper;
import com.kb.auth.mapper.RefreshTokenMapper;
import com.kb.auth.mapper.UserMapper;
import com.kb.auth.security.JwtTokenProvider;
import com.kb.auth.service.impl.AuthServiceImpl;
import com.kb.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("认证服务单元测试")
class AuthServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private RefreshTokenMapper refreshTokenMapper;
    @Mock private JwtBlacklistMapper jwtBlacklistMapper;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("admin");
        testUser.setPassword("$2a$10$hashedPassword");
        testUser.setStatus(1);
    }

    @Test
    @DisplayName("登录成功 - 返回 accessToken 和 refreshToken")
    void loginSuccess() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        when(userMapper.selectOne(any())).thenReturn(testUser);
        when(passwordEncoder.matches("admin123", "$2a$10$hashedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(1L, "admin")).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(900000L);

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        verify(refreshTokenMapper).insert(any());
    }

    @Test
    @DisplayName("登录失败 - 用户不存在")
    void loginUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("password");

        when(userMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("登录失败 - 密码错误")
    void loginWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrongpass");

        when(userMapper.selectOne(any())).thenReturn(testUser);
        when(passwordEncoder.matches("wrongpass", "$2a$10$hashedPassword")).thenReturn(false);

        assertThrows(BusinessException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("登录失败 - 用户已被禁用")
    void loginUserDisabled() {
        testUser.setStatus(0);
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        when(userMapper.selectOne(any())).thenReturn(testUser);

        assertThrows(BusinessException.class, () -> authService.login(request));
    }
}
