package com.kb.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marschat.common.exception.NotFoundException;
import com.marschat.common.page.PageResult;
import com.kb.ops.dto.DeploymentRecordRequest;
import com.kb.ops.entity.DeploymentRecord;
import com.kb.ops.entity.OpsService;
import com.kb.ops.mapper.DeploymentRecordMapper;
import com.kb.ops.mapper.OpsServiceMapper;
import com.kb.ops.service.DeploymentRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeploymentRecordServiceImpl implements DeploymentRecordService {

    private final DeploymentRecordMapper recordMapper;
    private final OpsServiceMapper serviceMapper;

    @Override
    public PageResult<DeploymentRecord> list(Long serviceId, int page, int size) {
        LambdaQueryWrapper<DeploymentRecord> wrapper = new LambdaQueryWrapper<>();
        if (serviceId != null) {
            wrapper.eq(DeploymentRecord::getServiceId, serviceId);
        }
        wrapper.orderByDesc(DeploymentRecord::getDeployTime);
        Page<DeploymentRecord> p = recordMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    @Override
    @Transactional
    public DeploymentRecord create(DeploymentRecordRequest request) {
        OpsService svc = serviceMapper.selectById(request.getServiceId());
        if (svc == null) {
            throw new NotFoundException("服务", request.getServiceId());
        }
        DeploymentRecord record = new DeploymentRecord();
        record.setServiceId(request.getServiceId());
        record.setServiceName(svc.getName());
        record.setHostId(request.getHostId() != null ? request.getHostId() : svc.getHostId());
        record.setVersion(request.getVersion());
        record.setPreviousVersion(request.getPreviousVersion());
        record.setOperator(request.getOperator());
        record.setDeployTime(LocalDateTime.now());
        record.setResult(request.getResult() != null ? request.getResult() : 1);
        record.setRollback(request.getRollback() != null ? request.getRollback() : 0);
        record.setRollbackInfo(request.getRollbackInfo());
        record.setRemark(request.getRemark());
        recordMapper.insert(record);

        // 同步更新服务当前版本
        if (record.getResult() == 1 && record.getVersion() != null) {
            svc.setVersion(record.getVersion());
            serviceMapper.updateById(svc);
        }
        return record;
    }

    @Override
    public List<DeploymentRecord> recent(int limit) {
        LambdaQueryWrapper<DeploymentRecord> wrapper = new LambdaQueryWrapper<DeploymentRecord>()
                .orderByDesc(DeploymentRecord::getDeployTime)
                .last("LIMIT " + Math.max(1, limit));
        return recordMapper.selectList(wrapper);
    }
}
