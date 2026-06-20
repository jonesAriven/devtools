package com.kb.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.exception.BusinessException;
import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
import com.kb.ops.dto.ServiceRequest;
import com.kb.ops.entity.OpsService;
import com.kb.ops.mapper.OpsServiceMapper;
import com.kb.ops.service.OpsServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OpsServiceServiceImpl implements OpsServiceService {

    private final OpsServiceMapper serviceMapper;

    @Override
    public PageResult<OpsService> list(String keyword, Long hostId, Integer status, int page, int size) {
        LambdaQueryWrapper<OpsService> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(OpsService::getName, keyword)
                    .or().like(OpsService::getType, keyword)
                    .or().like(OpsService::getTags, keyword));
        }
        if (hostId != null) {
            wrapper.eq(OpsService::getHostId, hostId);
        }
        if (status != null) {
            wrapper.eq(OpsService::getStatus, status);
        }
        wrapper.orderByDesc(OpsService::getCreatedAt);

        Page<OpsService> p = serviceMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Override
    public OpsService getById(Long id) {
        OpsService svc = serviceMapper.selectById(id);
        if (svc == null) {
            throw new NotFoundException("服务", id);
        }
        return svc;
    }

    @Override
    public OpsService create(ServiceRequest request) {
        OpsService svc = new OpsService();
        copyFromRequest(svc, request);
        if (svc.getStatus() == null) {
            svc.setStatus(1);
        }
        serviceMapper.insert(svc);
        return svc;
    }

    @Override
    public OpsService update(Long id, ServiceRequest request) {
        OpsService svc = serviceMapper.selectById(id);
        if (svc == null) {
            throw new NotFoundException("服务", id);
        }
        copyFromRequest(svc, request);
        serviceMapper.updateById(svc);
        return svc;
    }

    @Override
    public void delete(Long id) {
        OpsService svc = serviceMapper.selectById(id);
        if (svc == null) {
            throw new NotFoundException("服务", id);
        }
        serviceMapper.deleteById(id);
    }

    @Override
    public List<OpsService> listAll() {
        return serviceMapper.selectList(null);
    }

    private void copyFromRequest(OpsService svc, ServiceRequest r) {
        svc.setName(r.getName());
        svc.setType(r.getType());
        svc.setVersion(r.getVersion());
        svc.setPort(r.getPort());
        svc.setHostId(r.getHostId());
        svc.setDeployPath(r.getDeployPath());
        if (r.getStatus() != null) {
            svc.setStatus(r.getStatus());
        }
        svc.setDependencies(r.getDependencies());
        svc.setTags(r.getTags());
        svc.setRemark(r.getRemark());
    }
}
