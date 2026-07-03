package com.kb.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
import com.kb.ops.dto.CredentialRequest;
import com.kb.ops.entity.Credential;
import com.kb.ops.mapper.CredentialMapper;
import com.kb.ops.service.CredentialService;
import com.kb.ops.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CredentialServiceImpl implements CredentialService {

    private final CredentialMapper credentialMapper;
    private final CryptoUtil cryptoUtil;

    @Override
    public PageResult<Credential> list(String type, String keyword, int page, int size) {
        LambdaQueryWrapper<Credential> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(type)) {
            wrapper.eq(Credential::getType, type);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Credential::getName, keyword)
                    .or().like(Credential::getUsername, keyword)
                    .or().like(Credential::getRemark, keyword));
        }
        wrapper.orderByDesc(Credential::getCreatedAt);

        Page<Credential> p = credentialMapper.selectPage(new Page<>(page, size), wrapper);
        // 列表不返回密码与密钥
        p.getRecords().forEach(c -> {
            c.setPasswordEncrypted(null);
            c.setSecretKey(null);
        });
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Override
    public Credential getById(Long id, boolean revealPassword) {
        Credential credential = credentialMapper.selectById(id);
        if (credential == null) {
            throw new NotFoundException("凭据", id);
        }
        if (!revealPassword) {
            credential.setPasswordEncrypted(null);
            credential.setSecretKey(null);
        }
        return credential;
    }

    @Override
    public Credential create(CredentialRequest request) {
        Credential credential = new Credential();
        copyFromRequest(credential, request);
        if (StringUtils.hasText(request.getPassword())) {
            credential.setPasswordEncrypted(cryptoUtil.encrypt(request.getPassword()));
        }
        if (StringUtils.hasText(request.getSecretKey())) {
            credential.setSecretKey(cryptoUtil.encrypt(request.getSecretKey()));
        }
        credentialMapper.insert(credential);
        maskSensitive(credential);
        return credential;
    }

    @Override
    public Credential update(Long id, CredentialRequest request) {
        Credential credential = credentialMapper.selectById(id);
        if (credential == null) {
            throw new NotFoundException("凭据", id);
        }
        copyFromRequest(credential, request);
        // 密码/密钥为空表示不修改
        if (StringUtils.hasText(request.getPassword())) {
            credential.setPasswordEncrypted(cryptoUtil.encrypt(request.getPassword()));
        }
        if (StringUtils.hasText(request.getSecretKey())) {
            credential.setSecretKey(cryptoUtil.encrypt(request.getSecretKey()));
        }
        credentialMapper.updateById(credential);
        maskSensitive(credential);
        return credential;
    }

    @Override
    public void delete(Long id) {
        Credential credential = credentialMapper.selectById(id);
        if (credential == null) {
            throw new NotFoundException("凭据", id);
        }
        credentialMapper.deleteById(id);
    }

    private void copyFromRequest(Credential c, CredentialRequest r) {
        c.setName(r.getName());
        c.setType(r.getType());
        c.setUsername(r.getUsername());
        c.setHostId(r.getHostId());
        c.setServiceId(r.getServiceId());
        c.setRemark(r.getRemark());
    }

    private void maskSensitive(Credential c) {
        c.setPasswordEncrypted(null);
        c.setSecretKey(null);
    }
}
