package com.kb.ops.service;

import com.kb.common.page.PageResult;
import com.kb.ops.entity.OpsConflict;

public interface ConflictDetectionService {

    /**
     * 执行全量矛盾检测，返回本次检测到的矛盾数量。
     * 检测规则：
     * 1. VERSION_MISMATCH  同一服务在不同主机版本不一致
     * 2. PORT_CONFLICT     同一主机同端口被多服务占用
     * 3. HOST_DOWN_SERVICE_RUNNING  主机停机但服务仍标记运行中
     * 4. DUPLICATE_HOST_IP 重复的主机IP
     * 5. MISSING_DEPENDENCY 服务依赖了不存在的服务
     * 6. DUPLICATE_SERVICE_NAME 同名服务部署版本冲突
     */
    int detect();

    PageResult<OpsConflict> list(String ruleCode, Integer status, int page, int size);

    void resolve(Long id);
}
