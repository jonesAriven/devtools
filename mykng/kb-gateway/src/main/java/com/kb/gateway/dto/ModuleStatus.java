package com.kb.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模块状态 DTO（M7-1 新增，2026-09-10 Phase 0.5 扩展为四态）
 * <p>
 * 返回给前端用于「灰显 + 原因提示」，不再用于"整块隐藏菜单"。
 * 期望集来源：{@code kb-gateway/src/main/resources/module-manifest.json}
 * （由 {@code mykng/gen-registry.py} 从 {@code mykng/module-registry.yml} 派生）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleStatus {

    /** 模块名（Nacos 服务名，如 kb-file / auth-center） */
    private String name;

    /** 四态：OK / DOWN / MISSING / UNEXPECTED / UNKNOWN，语义见 {@link ModuleState} */
    private ModuleState state;

    /** 是否在注册表期望集（module-manifest.json 的 expectedModules）内 */
    private boolean expected;

    /** Nacos 实际实例数 */
    private int actual;

    /**
     * 网关进程启动以来是否见过该模块的实例。
     * 用于区分 DOWN（曾有实例 → 服务下线）与 MISSING（从未见过 → 命名漂移）。
     * 进程重启后清零，因此还会结合 Nacos 服务列表（{@link #inNacosServiceList}）一起判断。
     */
    private boolean everSeen;

    /** Nacos 服务列表中是否存在该名字（即使实例数为 0）；为 false 时说明名字根本没被注册过 */
    private boolean inNacosServiceList;

    /** 是否对用户可用。等价于 {@code state.isAvailable()}，前端可直接用 */
    private boolean available;

    /** @deprecated 兼容字段，取值 UP / DOWN / UNKNOWN，新代码请用 {@link #state} */
    @Deprecated
    private String status;

    /** @deprecated 兼容字段，等于 {@link #actual}，新代码请用 {@link #actual} */
    @Deprecated
    private int instances;
}
