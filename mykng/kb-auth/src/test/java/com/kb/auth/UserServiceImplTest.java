package com.kb.auth;

import com.kb.auth.entity.User;
import com.kb.auth.mapper.UserMapper;
import com.kb.auth.service.impl.UserServiceImpl;
import com.kb.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("用户服务单元测试")
class UserServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("admin");
        testUser.setPassword("$2a$10$hashedPassword");
        testUser.setNickname("管理员");
        testUser.setStatus(1);
    }

    @Test
    @DisplayName("获取用户信息 - 正常返回且密码置空")
    void getProfileSuccess() {
        when(userMapper.selectById(1L)).thenReturn(testUser);

        User result = userService.getProfile(1L);

        assertNotNull(result);
        assertEquals("admin", result.getUsername());
        assertNull(result.getPassword());
    }

    @Test
    @DisplayName("获取用户信息 - 用户不存在")
    void getProfileNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> userService.getProfile(999L));
    }

    @Test
    @DisplayName("修改密码 - 旧密码正确")
    void updatePasswordSuccess() {
        when(userMapper.selectById(1L)).thenReturn(testUser);
        when(passwordEncoder.matches("oldpass", "$2a$10$hashedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newpass")).thenReturn("$2a$10$newHashed");

        assertDoesNotThrow(() -> userService.updatePassword(1L, "oldpass", "newpass"));
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    @DisplayName("修改密码 - 旧密码错误")
    void updatePasswordWrongOld() {
        when(userMapper.selectById(1L)).thenReturn(testUser);
        when(passwordEncoder.matches("wrongold", "$2a$10$hashedPassword")).thenReturn(false);

        assertThrows(BusinessException.class, () -> userService.updatePassword(1L, "wrongold", "newpass"));
    }
}
