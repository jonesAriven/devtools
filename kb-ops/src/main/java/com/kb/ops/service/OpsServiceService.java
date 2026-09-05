package com.kb.ops.service;

import com.marschat.common.page.PageResult;
import com.kb.ops.dto.ServiceRequest;
import com.kb.ops.entity.OpsService;

public interface OpsServiceService {

    PageResult<OpsService> list(String keyword, Long hostId, Integer status, int page, int size);

    OpsService getById(Long id);

    OpsService create(ServiceRequest request);

    OpsService update(Long id, ServiceRequest request);

    void delete(Long id);

    /** 查询所有服务（供矛盾检测/看板使用） */
    java.util.List<OpsService> listAll();
}
