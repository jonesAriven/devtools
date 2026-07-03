package com.kb.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kb.common.exception.BusinessException;
import com.kb.common.exception.NotFoundException;
import com.kb.common.page.PageResult;
import com.kb.ops.dto.PortRequest;
import com.kb.ops.entity.Port;
import com.kb.ops.mapper.PortMapper;
import com.kb.ops.service.PortService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PortServiceImpl implements PortService {

    private final PortMapper portMapper;

    @Override
    public PageResult<Port> list(Long hostId, Long serviceId, String keyword, int page, int size) {
        LambdaQueryWrapper<Port> wrapper = new LambdaQueryWrapper<>();
        if (hostId != null) {
            wrapper.eq(Port::getHostId, hostId);
        }
        if (serviceId != null) {
            wrapper.eq(Port::getServiceId, serviceId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Port::getPurpose, keyword)
                    .or().like(Port::getRemark, keyword));
        }
        wrapper.orderByDesc(Port::getCreatedAt);

        Page<Port> p = portMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Override
    public Port getById(Long id) {
        Port port = portMapper.selectById(id);
        if (port == null) {
            throw new NotFoundException("端口", id);
        }
        return port;
    }

    @Override
    public Port create(PortRequest request) {
        Port port = new Port();
        copyFromRequest(port, request);
        if (port.getStatus() == null) {
            port.setStatus(1);
        }
        if (port.getExposed() == null) {
            port.setExposed(0);
        }
        portMapper.insert(port);
        return port;
    }

    @Override
    public Port update(Long id, PortRequest request) {
        Port port = portMapper.selectById(id);
        if (port == null) {
            throw new NotFoundException("端口", id);
        }
        copyFromRequest(port, request);
        portMapper.updateById(port);
        return port;
    }

    @Override
    public void delete(Long id) {
        Port port = portMapper.selectById(id);
        if (port == null) {
            throw new NotFoundException("端口", id);
        }
        portMapper.deleteById(id);
    }

    private void copyFromRequest(Port port, PortRequest r) {
        port.setHostId(r.getHostId());
        if (StringUtils.hasText(r.getPort())) {
            try {
                port.setPort(Integer.valueOf(r.getPort().trim()));
            } catch (NumberFormatException e) {
                throw new BusinessException(400, "端口号格式不正确: " + r.getPort());
            }
        }
        port.setProtocol(r.getProtocol());
        port.setServiceId(r.getServiceId());
        port.setPurpose(r.getPurpose());
        if (r.getStatus() != null) {
            port.setStatus(r.getStatus());
        }
        if (r.getExposed() != null) {
            port.setExposed(r.getExposed());
        }
        port.setRemark(r.getRemark());
    }
}
