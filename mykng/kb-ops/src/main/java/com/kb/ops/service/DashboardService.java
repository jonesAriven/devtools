package com.kb.ops.service;

import com.kb.ops.dto.DashboardVO;

public interface DashboardService {

    DashboardVO getDashboard();

    /** 刷新当日快照（定时任务调用） */
    void refreshSnapshot();
}
