package com.kb.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.exception.NotFoundException;
import com.marschat.common.page.PageResult;
import com.kb.ops.dto.DependencyRequest;
import com.kb.ops.entity.Dependency;
import com.kb.ops.mapper.DependencyMapper;
import com.kb.ops.service.DependencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DependencyServiceImpl implements DependencyService {

    private final DependencyMapper dependencyMapper;

    @Override
    public PageResult<Dependency> list(Long serviceId, int page, int size) {
        LambdaQueryWrapper<Dependency> wrapper = new LambdaQueryWrapper<>();
        if (serviceId != null) {
            wrapper.eq(Dependency::getServiceId, serviceId);
        }
        wrapper.orderByDesc(Dependency::getCreatedAt);

        Page<Dependency> p = dependencyMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Override
    public Dependency getById(Long id) {
        Dependency dependency = dependencyMapper.selectById(id);
        if (dependency == null) {
            throw new NotFoundException("依赖关系", id);
        }
        return dependency;
    }

    @Override
    public Dependency create(DependencyRequest request) {
        Dependency dependency = new Dependency();
        copyFromRequest(dependency, request);
        if (dependency.getDependencyType() == null) {
            dependency.setDependencyType("REQUIRED");
        }
        dependencyMapper.insert(dependency);
        return dependency;
    }

    @Override
    public Dependency update(Long id, DependencyRequest request) {
        Dependency dependency = dependencyMapper.selectById(id);
        if (dependency == null) {
            throw new NotFoundException("依赖关系", id);
        }
        copyFromRequest(dependency, request);
        dependencyMapper.updateById(dependency);
        return dependency;
    }

    @Override
    public void delete(Long id) {
        Dependency dependency = dependencyMapper.selectById(id);
        if (dependency == null) {
            throw new NotFoundException("依赖关系", id);
        }
        dependencyMapper.deleteById(id);
    }

    private void copyFromRequest(Dependency d, DependencyRequest r) {
        d.setServiceId(r.getServiceId());
        d.setServiceName(r.getServiceName());
        d.setDependsOnServiceId(r.getDependsOnServiceId());
        d.setDependsOnServiceName(r.getDependsOnServiceName());
        d.setDependencyType(r.getDependencyType());
        d.setDescription(r.getDescription());
    }
}
