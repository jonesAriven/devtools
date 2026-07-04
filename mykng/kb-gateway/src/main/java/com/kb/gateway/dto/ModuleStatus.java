package com.kb.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模块状态 DTO（M7-1 新增）
 * <p>
 * 返回给前端，用于动态菜单显隐和降级提示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleStatus {

    /** 模块名（Nacos 服务名，如 kb-file） */
    private String name;

    /** 状态：UP（在线）/ DOWN（下线）/ UNKNOWN（未知） */
    private String status;

    /** 实例数（用于水平扩展感知） */
    private int instances;

    /** 模块是否可用（status=UP 时为 true） */
    private boolean available;
}
