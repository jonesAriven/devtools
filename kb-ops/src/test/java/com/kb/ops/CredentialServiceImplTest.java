package com.kb.ops;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.exception.NotFoundException;
import com.marschat.common.page.PageResult;
import com.kb.ops.dto.CredentialRequest;
import com.kb.ops.entity.Credential;
import com.kb.ops.mapper.CredentialMapper;
import com.kb.ops.service.impl.CredentialServiceImpl;
import com.kb.ops.util.CryptoUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("凭据管理服务单元测试")
class CredentialServiceImplTest {

    @Mock
    private CredentialMapper credentialMapper;

    @Mock
    private CryptoUtil cryptoUtil;

    @InjectMocks
    private CredentialServiceImpl credentialService;

    @Test
    @DisplayName("list_无过滤条件_列表不返回密码与密钥")
    void list_noFilter_masksSensitive() {
        Credential c = new Credential();
        c.setId(1L);
        c.setName("db-cred");
        c.setPasswordEncrypted("encrypted-pwd");
        c.setSecretKey("encrypted-key");
        Page<Credential> page = new Page<>(1, 20);
        page.setRecords(List.of(c));
        page.setTotal(1);
        when(credentialMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<Credential> result = credentialService.list(null, null, 1, 20);

        assertEquals(1, result.getTotal());
        assertNull(result.getList().get(0).getPasswordEncrypted());
        assertNull(result.getList().get(0).getSecretKey());
    }

    @Test
    @DisplayName("list_带类型与关键字_应用过滤条件")
    void list_withTypeAndKeyword_appliesFilters() {
        Credential c = new Credential();
        c.setId(1L);
        Page<Credential> page = new Page<>(1, 20);
        page.setRecords(List.of(c));
        page.setTotal(1);
        when(credentialMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<Credential> result = credentialService.list("SSH", "kb", 1, 20);

        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("getById_不展示密码_清空密码与密钥")
    void getById_hidePassword_clearsSensitive() {
        Credential c = new Credential();
        c.setId(1L);
        c.setName("db-cred");
        c.setPasswordEncrypted("encrypted-pwd");
        c.setSecretKey("encrypted-key");
        when(credentialMapper.selectById(1L)).thenReturn(c);

        Credential result = credentialService.getById(1L, false);

        assertEquals("db-cred", result.getName());
        assertNull(result.getPasswordEncrypted());
        assertNull(result.getSecretKey());
    }

    @Test
    @DisplayName("getById_展示密码_保留密码与密钥")
    void getById_revealPassword_keepsSensitive() {
        Credential c = new Credential();
        c.setId(1L);
        c.setPasswordEncrypted("encrypted-pwd");
        c.setSecretKey("encrypted-key");
        when(credentialMapper.selectById(1L)).thenReturn(c);

        Credential result = credentialService.getById(1L, true);

        assertEquals("encrypted-pwd", result.getPasswordEncrypted());
        assertEquals("encrypted-key", result.getSecretKey());
    }

    @Test
    @DisplayName("getById_不存在_抛出NotFoundException")
    void getById_notFound_throwsNotFoundException() {
        when(credentialMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> credentialService.getById(999L, false));
    }

    @Test
    @DisplayName("create_含密码与密钥_加密存储后置空")
    void create_withPasswordAndSecretKey_encryptsThenMasks() {
        CredentialRequest request = new CredentialRequest();
        request.setName("db-cred");
        request.setType("SSH");
        request.setUsername("root");
        request.setHostId(1L);
        request.setServiceId(2L);
        request.setPassword("secret123");
        request.setSecretKey("api-key-xyz");
        when(cryptoUtil.encrypt("secret123")).thenReturn("encrypted-pwd");
        when(cryptoUtil.encrypt("api-key-xyz")).thenReturn("encrypted-key");
        when(credentialMapper.insert(any(Credential.class))).thenAnswer(invocation -> {
            Credential c = invocation.getArgument(0);
            c.setId(1L);
            return 1;
        });

        Credential result = credentialService.create(request);

        assertEquals("db-cred", result.getName());
        assertNull(result.getPasswordEncrypted());
        assertNull(result.getSecretKey());
        verify(cryptoUtil).encrypt("secret123");
        verify(cryptoUtil).encrypt("api-key-xyz");
    }

    @Test
    @DisplayName("create_无密码与密钥_不调用加密")
    void create_withoutPasswordAndSecretKey_noEncrypt() {
        CredentialRequest request = new CredentialRequest();
        request.setName("empty-cred");
        when(credentialMapper.insert(any(Credential.class))).thenAnswer(invocation -> {
            Credential c = invocation.getArgument(0);
            c.setId(1L);
            return 1;
        });

        Credential result = credentialService.create(request);

        assertEquals("empty-cred", result.getName());
        verify(cryptoUtil, never()).encrypt(any());
    }

    @Test
    @DisplayName("update_含密码与密钥_加密更新后置空")
    void update_withPasswordAndSecretKey_encryptsThenMasks() {
        Credential existing = new Credential();
        existing.setId(1L);
        when(credentialMapper.selectById(1L)).thenReturn(existing);
        when(cryptoUtil.encrypt("newpwd")).thenReturn("encrypted-pwd");
        when(cryptoUtil.encrypt("newkey")).thenReturn("encrypted-key");
        when(credentialMapper.updateById(any(Credential.class))).thenReturn(1);

        CredentialRequest request = new CredentialRequest();
        request.setName("updated-cred");
        request.setPassword("newpwd");
        request.setSecretKey("newkey");

        Credential result = credentialService.update(1L, request);

        assertEquals("updated-cred", result.getName());
        assertNull(result.getPasswordEncrypted());
        verify(credentialMapper).updateById(any(Credential.class));
    }

    @Test
    @DisplayName("update_无密码与密钥_不修改原密码")
    void update_withoutPassword_noEncrypt() {
        Credential existing = new Credential();
        existing.setId(1L);
        existing.setPasswordEncrypted("old-pwd");
        when(credentialMapper.selectById(1L)).thenReturn(existing);
        when(credentialMapper.updateById(any(Credential.class))).thenReturn(1);

        CredentialRequest request = new CredentialRequest();
        request.setName("updated-cred");

        credentialService.update(1L, request);

        verify(cryptoUtil, never()).encrypt(any());
        verify(credentialMapper).updateById(any(Credential.class));
    }

    @Test
    @DisplayName("update_不存在_抛出NotFoundException")
    void update_notFound_throwsNotFoundException() {
        when(credentialMapper.selectById(999L)).thenReturn(null);

        CredentialRequest request = new CredentialRequest();
        request.setName("cred");

        assertThrows(NotFoundException.class, () -> credentialService.update(999L, request));
    }

    @Test
    @DisplayName("delete_存在_删除成功")
    void delete_exists_deletes() {
        Credential existing = new Credential();
        existing.setId(1L);
        when(credentialMapper.selectById(1L)).thenReturn(existing);
        when(credentialMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> credentialService.delete(1L));

        verify(credentialMapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete_不存在_抛出NotFoundException")
    void delete_notFound_throwsNotFoundException() {
        when(credentialMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> credentialService.delete(999L));
    }
}
