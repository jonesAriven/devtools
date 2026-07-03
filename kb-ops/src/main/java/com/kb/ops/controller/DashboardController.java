package com.kb.ops.controller;

import com.kb.common.result.Result;
import com.kb.ops.dto.DashboardVO;
import com.kb.ops.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public Result<DashboardVO> getDashboard() {
        return Result.ok(dashboardService.getDashboard());
    }

    /**
     * 手动刷新看板快照
     */
    @PostMapping("/snapshot/refresh")
    public Result<Void> refreshSnapshot() {
        dashboardService.refreshSnapshot();
        return Result.ok();
    }
}
