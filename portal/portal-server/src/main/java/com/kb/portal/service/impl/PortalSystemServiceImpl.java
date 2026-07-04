package com.kb.portal.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
import com.kb.portal.dto.PortalSystemRequest;
import com.kb.portal.entity.PortalSystem;
import com.kb.portal.mapper.PortalSystemMapper;
import com.kb.portal.service.PortalSystemService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PortalSystemServiceImpl extends ServiceImpl<PortalSystemMapper, PortalSystem> implements PortalSystemService {

    @Override
    public PageResult<PortalSystem> list(String keyword, String category, Integer status, int page, int size) {
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
        wrapper.orderByAsc(PortalSystem::getSortOrder)
                .orderByDesc(PortalSystem::getCreatedAt);

        Page<PortalSystem> p = baseMapper.selectPage(new Page<>(page, size), wrapper);
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
        return baseMapper.selectList(wrapper);
    }

    @Override
    public List<PortalSystem> listAllEnabled() {
        LambdaQueryWrapper<PortalSystem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PortalSystem::getStatus, 1)
                .orderByAsc(PortalSystem::getSortOrder)
                .orderByDesc(PortalSystem::getCreatedAt);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public PortalSystem getById(Long id) {
        PortalSystem portalSystem = baseMapper.selectById(id);
        if (portalSystem == null) {
            throw new NotFoundException("系统", id);
        }
        return portalSystem;
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
        if (r.getSortOrder() != null) {
            portalSystem.setSortOrder(r.getSortOrder());
        }
    }
}
