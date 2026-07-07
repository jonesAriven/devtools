package com.kb.portal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
import com.kb.portal.dto.PortalSystemRequest;
import com.kb.portal.dto.SystemCredentials;
import com.kb.portal.entity.PortalSystem;
import com.kb.portal.mapper.PortalSystemMapper;
import com.kb.portal.service.PortalSystemService;
import com.kb.portal.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PortalSystemServiceImpl extends ServiceImpl<PortalSystemMapper, PortalSystem> implements PortalSystemService {

    private final CryptoUtil cryptoUtil;

    @Override
    public PageResult<PortalSystem> list(String keyword, String category, Integer status, Boolean hasCredentials, Boolean hasUrl, int page, int size) {
        LambdaQueryWrapper<PortalSystem> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PortalSystem::getName, keyword)
                    .or().like(PortalSystem::getDescription, keyword)
                    .or().like(PortalSystem::getUrl, keyword));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(PortalSystem::getCategory, category);
        }
        if (status != null) {
            wrapper.eq(PortalSystem::getStatus, status);
        }
        if (hasCredentials != null) {
            if (hasCredentials) {
                wrapper.isNotNull(PortalSystem::getLoginUsername)
                        .ne(PortalSystem::getLoginUsername, "");
            } else {
                wrapper.and(w -> w.isNull(PortalSystem::getLoginUsername)
                        .or().eq(PortalSystem::getLoginUsername, ""));
            }
        }
        if (hasUrl != null) {
            if (hasUrl) {
                wrapper.isNotNull(PortalSystem::getUrl)
                        .ne(PortalSystem::getUrl, "");
            } else {
                wrapper.and(w -> w.isNull(PortalSystem::getUrl)
                        .or().eq(PortalSystem::getUrl, ""));
            }
        }
        wrapper.orderByAsc(PortalSystem::getSortOrder)
                .orderByDesc(PortalSystem::getCreatedAt);

        Page<PortalSystem> p = baseMapper.selectPage(new Page<>(page, size), wrapper);
        p.getRecords().forEach(this::maskPassword);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Override
    public List<PortalSystem> listByCategory(String category) {
        LambdaQueryWrapper<PortalSystem> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(PortalSystem::getCategory, category);
        }
        wrapper.eq(PortalSystem::getStatus, 1)
                .orderByAsc(PortalSystem::getSortOrder)
                .orderByDesc(PortalSystem::getCreatedAt);
        List<PortalSystem> list = baseMapper.selectList(wrapper);
        list.forEach(this::maskPassword);
        return list;
    }

    @Override
    public List<PortalSystem> listAllEnabled() {
        LambdaQueryWrapper<PortalSystem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PortalSystem::getStatus, 1)
                .orderByAsc(PortalSystem::getSortOrder)
                .orderByDesc(PortalSystem::getCreatedAt);
        List<PortalSystem> list = baseMapper.selectList(wrapper);
        list.forEach(this::maskPassword);
        return list;
    }

    @Override
    public PortalSystem getById(Long id) {
        PortalSystem portalSystem = baseMapper.selectById(id);
        if (portalSystem == null) {
            throw new NotFoundException("系统", id);
        }
        portalSystem.setLoginPassword(null);
        return portalSystem;
    }

    @Override
    public SystemCredentials getCredentials(Long id) {
        PortalSystem portalSystem = baseMapper.selectById(id);
        if (portalSystem == null) {
            throw new NotFoundException("系统", id);
        }
        SystemCredentials credentials = new SystemCredentials();
        credentials.setUsername(portalSystem.getLoginUsername());
        credentials.setPassword(cryptoUtil.decrypt(portalSystem.getLoginPassword()));
        return credentials;
    }

    @Override
    public PortalSystem create(PortalSystemRequest request) {
        PortalSystem portalSystem = new PortalSystem();
        copyFromRequest(portalSystem, request);
        if (portalSystem.getStatus() == null) {
            portalSystem.setStatus(1);
        }
        if (portalSystem.getSortOrder() == null) {
            portalSystem.setSortOrder(0);
        }
        baseMapper.insert(portalSystem);
        maskPassword(portalSystem);
        return portalSystem;
    }

    @Override
    public PortalSystem update(Long id, PortalSystemRequest request) {
        PortalSystem portalSystem = baseMapper.selectById(id);
        if (portalSystem == null) {
            throw new NotFoundException("系统", id);
        }
        copyFromRequest(portalSystem, request);
        baseMapper.updateById(portalSystem);
        maskPassword(portalSystem);
        return portalSystem;
    }

    @Override
    public void delete(Long id) {
        PortalSystem portalSystem = baseMapper.selectById(id);
        if (portalSystem == null) {
            throw new NotFoundException("系统", id);
        }
        baseMapper.deleteById(id);
    }

    private void copyFromRequest(PortalSystem portalSystem, PortalSystemRequest r) {
        portalSystem.setName(r.getName());
        portalSystem.setDescription(r.getDescription());
        portalSystem.setUrl(r.getUrl());
        portalSystem.setIcon(r.getIcon());
        portalSystem.setColor(r.getColor());
        portalSystem.setCategory(r.getCategory());
        if (r.getStatus() != null) {
            portalSystem.setStatus(r.getStatus());
        }
        portalSystem.setHealthCheckUrl(r.getHealthCheckUrl());
        portalSystem.setDocs(r.getDocs());
        portalSystem.setDownloadPath(r.getDownloadPath());
        portalSystem.setTechStack(r.getTechStack());
        portalSystem.setLoginUsername(r.getLoginUsername());
        if (r.getLoginPassword() != null) {
            if (r.getLoginPassword().isEmpty()) {
                portalSystem.setLoginPassword(null);
            } else {
                portalSystem.setLoginPassword(cryptoUtil.encrypt(r.getLoginPassword()));
            }
        }
        if (r.getSortOrder() != null) {
            portalSystem.setSortOrder(r.getSortOrder());
        }
    }

    private void maskPassword(PortalSystem portalSystem) {
        portalSystem.setLoginPassword(null);
    }
}
