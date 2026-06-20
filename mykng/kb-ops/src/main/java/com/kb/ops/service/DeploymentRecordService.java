package com.kb.ops.service;

import com.kb.common.page.PageResult;
import com.kb.ops.dto.DeploymentRecordRequest;
import com.kb.ops.entity.DeploymentRecord;

public interface DeploymentRecordService {

    PageResult<DeploymentRecord> list(Long serviceId, int page, int size);

    DeploymentRecord create(DeploymentRecordRequest request);

    /** 最近 N 条部署记录 */
    java.util.List<DeploymentRecord> recent(int limit);
}
