package com.kb.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
import com.kb.ops.dto.DomainRequest;
import com.kb.ops.entity.Domain;
import com.kb.ops.mapper.DomainMapper;
import com.kb.ops.service.DomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DomainServiceImpl implements DomainService {

    private final DomainMapper domainMapper;

    @Override
    public PageResult<Domain> list(String keyword, Integer status, int page, int size) {
        LambdaQueryWrapper<Domain> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Domain::getDomain, keyword)
                    .or().like(Domain::getPurpose, keyword)
                    .or().like(Domain::getRegistrar, keyword)
                    .or().like(Domain::getRemark, keyword));
        }
        if (status != null) {
            wrapper.eq(Domain::getStatus, status);
        }
        wrapper.orderByDesc(Domain::getCreatedAt);

        Page<Domain> p = domainMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Override
    public Domain getById(Long id) {
        Domain domain = domainMapper.selectById(id);
        if (domain == null) {
            throw new NotFoundException("域名", id);
        }
        return domain;
    }

    @Override
    public Domain create(DomainRequest request) {
        Domain domain = new Domain();
        copyFromRequest(domain, request);
        if (domain.getStatus() == null) {
            domain.setStatus(1);
        }
        domainMapper.insert(domain);
        return domain;
    }

    @Override
    public Domain update(Long id, DomainRequest request) {
        Domain domain = domainMapper.selectById(id);
        if (domain == null) {
            throw new NotFoundException("域名", id);
        }
        copyFromRequest(domain, request);
        domainMapper.updateById(domain);
        return domain;
    }

    @Override
    public void delete(Long id) {
        Domain domain = domainMapper.selectById(id);
        if (domain == null) {
            throw new NotFoundException("域名", id);
        }
        domainMapper.deleteById(id);
    }

    private void copyFromRequest(Domain d, DomainRequest r) {
        d.setDomain(r.getDomain());
        d.setType(r.getType());
        d.setPurpose(r.getPurpose());
        d.setRegistrar(r.getRegistrar());
        d.setExpiresAt(r.getExpiresAt());
        d.setSslExpiresAt(r.getSslExpiresAt());
        if (r.getStatus() != null) {
            d.setStatus(r.getStatus());
        }
        d.setRemark(r.getRemark());
    }
}
