package com.kb.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.exception.BusinessException;
import com.marschat.common.exception.NotFoundException;
import com.marschat.common.page.PageResult;
import com.kb.ops.dto.HostRequest;
import com.kb.ops.entity.Host;
import com.kb.ops.mapper.HostMapper;
import com.kb.ops.service.HostService;
import com.kb.ops.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HostServiceImpl implements HostService {

    private final HostMapper hostMapper;
    private final CryptoUtil cryptoUtil;

    @Override
    public PageResult<Host> list(String keyword, Integer status, int page, int size) {
        LambdaQueryWrapper<Host> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Host::getName, keyword)
                    .or().like(Host::getIp, keyword)
                    .or().like(Host::getTailscaleIp, keyword)
                    .or().like(Host::getTags, keyword));
        }
        if (status != null) {
            wrapper.eq(Host::getStatus, status);
        }
        wrapper.orderByDesc(Host::getCreatedAt);

        Page<Host> p = hostMapper.selectPage(new Page<>(page, size), wrapper);
        // 列表不返回密码
        p.getRecords().forEach(h -> h.setPasswordEncrypted(null));
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Override
    public Host getById(Long id, boolean revealPassword) {
        Host host = hostMapper.selectById(id);
        if (host == null) {
            throw new NotFoundException("主机", id);
        }
        if (!revealPassword) {
            host.setPasswordEncrypted(null);
        }
        return host;
    }

    @Override
    public Host create(HostRequest request) {
        checkIpDuplicate(request.getIp(), null);
        Host host = new Host();
        copyFromRequest(host, request);
        if (StringUtils.hasText(request.getPassword())) {
            host.setPasswordEncrypted(cryptoUtil.encrypt(request.getPassword()));
        }
        if (host.getSshPort() == null) {
            host.setSshPort(22);
        }
        if (host.getStatus() == null) {
            host.setStatus(1);
        }
        hostMapper.insert(host);
        host.setPasswordEncrypted(null);
        return host;
    }

    @Override
    public Host update(Long id, HostRequest request) {
        Host host = hostMapper.selectById(id);
        if (host == null) {
            throw new NotFoundException("主机", id);
        }
        checkIpDuplicate(request.getIp(), id);
        copyFromRequest(host, request);
        // 密码为空表示不修改
        if (StringUtils.hasText(request.getPassword())) {
            host.setPasswordEncrypted(cryptoUtil.encrypt(request.getPassword()));
        }
        hostMapper.updateById(host);
        host.setPasswordEncrypted(null);
        return host;
    }

    @Override
    public void delete(Long id) {
        Host host = hostMapper.selectById(id);
        if (host == null) {
            throw new NotFoundException("主机", id);
        }
        hostMapper.deleteById(id);
    }

    private void copyFromRequest(Host host, HostRequest r) {
        host.setName(r.getName());
        host.setIp(r.getIp());
        host.setTailscaleIp(r.getTailscaleIp());
        host.setSshPort(r.getSshPort());
        host.setUsername(r.getUsername());
        host.setRole(r.getRole());
        if (r.getStatus() != null) {
            host.setStatus(r.getStatus());
        }
        host.setTags(r.getTags());
        host.setRemark(r.getRemark());
    }

    private void checkIpDuplicate(String ip, Long excludeId) {
        if (!StringUtils.hasText(ip)) {
            return;
        }
        LambdaQueryWrapper<Host> wrapper = new LambdaQueryWrapper<Host>()
                .eq(Host::getIp, ip);
        if (excludeId != null) {
            wrapper.ne(Host::getId, excludeId);
        }
        List<Host> exist = hostMapper.selectList(wrapper);
        if (!exist.isEmpty()) {
            throw new BusinessException(409, "IP 已存在: " + ip);
        }
    }
}
