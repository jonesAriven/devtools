package com.kb.gateway.dto;

/**
 * 模块健康状态（M7-1 四态 + 兜底态）
 * <p>
 * 改造动机：原先只有 {@code available} 一个布尔量，"模块名写错/没接入"与"服务真的宕机"被混为
 * 一谈，前端只能把菜单整块隐藏，导致改名类事故可以静默存活很久（2026-09-10 的 kb-auth →
 * auth-center 事故即为实例：菜单被隐藏三个月无人察觉）。引入「期望集（module-registry.yml）
 * vs 实际注册（Nacos）」比对后，可以拆出互不相同的语义。
 *
 * <table>
 *   <tr><th>取值</th><th>判据</th><th>语义</th><th>可用</th></tr>
 *   <tr><td>OK</td><td>在期望集 且 Nacos 有实例</td><td>正常</td><td>是</td></tr>
 *   <tr><td>DOWN</td><td>有注册痕迹但当前 0 实例（本进程见过，或仍留在 Nacos 服务列表）</td><td>运维事件：服务下线/崩溃</td><td>否</td></tr>
 *   <tr><td>MISSING</td><td>无任何注册痕迹（期望集内却从未注册，或期望集外且 Nacos 里压根不存在这个名字）</td><td>名字对不上 / 查无此注册</td><td>否</td></tr>
 *   <tr><td>UNEXPECTED</td><td>不在期望集 但 Nacos 有实例</td><td>野模块：注册了但注册表没声明</td><td>是</td></tr>
 *   <tr><td>UNKNOWN</td><td>探活基建本身不可用（Nacos 整体拉取失败/单模块超时）</td><td>无法判定，按可用放行避免误隐藏</td><td>是</td></tr>
 * </table>
 *
 * <p>注意 {@code DOWN} 与 {@code MISSING} 的区分是本枚举的核心价值：前者要运维介入重启，
 * 后者要开发改配置——前端 tooltip 会据此给出不同的排障指引。
 *
 * <p><b>⚠️ 已知限制</b>：{@code DOWN} 与 {@code MISSING} 的区分依赖进程内 {@code everSeen} 集合
 * （即"本次网关启动以来是否见过该服务"）。Nacos 会清理实例数为 0 的服务名，因此一个
 * <b>真正宕机、且跨过了网关重启</b>的服务同样会被判为 {@code MISSING}——
 * 此时它只能表达"自本次网关启动后未见注册"，<b>不能据此断言"配置/命名不一致"</b>。
 * 前端文案因此只陈述可观测事实并列出两种可能，不替运维下结论。
 * 彻底消除该歧义需持久化 {@code everSeen}，见 ADR Phase 0.5 §12.11「已知限制」。
 */
public enum ModuleState {

    /** 期望集内且 Nacos 有实例 */
    OK,
    /** 有注册痕迹但当前 0 实例（本进程见过，或仍留在 Nacos 服务列表）—— 服务下线（运维事件） */
    DOWN,
    /** 无任何注册痕迹 —— 名字对不上（期望集内从未注册）或查无此注册（期望集外且 Nacos 无） */
    MISSING,
    /** 期望集外、Nacos 却有实例 —— 未声明的野模块 */
    UNEXPECTED,
    /** 探活基建不可用，无法判定；按可用放行 */
    UNKNOWN;

    /**
     * 纯函数判定：把「期望 vs 实际」映射到状态。
     * <p>
     * 抽成无副作用的静态方法有两个目的：其一，这段逻辑是本模块最核心的语义，     * 抽出来才能用真值表锁死（见 {@code ModuleStateTest}），且不需要 Spring 与 Nacos；     * 其二，避免判定规则散落在 controller 的分支里、被注释和实现不一致地描述。
     *
     * @param expected 是否在注册表期望集（module-manifest.json）内
     * @param actual   Nacos 实际实例数
     * @param inList   名字是否出现在 Nacos 服务列表中（即使实例数为 0）
     * @param everSeen 本进程启动以来是否见过该模块的实例
     * @return 判定结果；{@code UNKNOWN} 不在此函数产出，它专指探活基建本身不可用
     */
    public static ModuleState resolve(boolean expected, int actual, boolean inList, boolean everSeen) {
        if (actual > 0) {
            return expected ? OK : UNEXPECTED;
        }
        if (!expected) {
            // 期望集外且零实例，两种来源语义不同，不能一律叫"宕机"：
            //  - 名字仍在 Nacos 服务列表里 → 注册痕迹还在，属残留条目 → 按"已下线"看待
            //  - 名字在 Nacos 里毫无痕迹   → 不是宕机，而是"查无此注册"
            //    （任意野生名字都会落到这里，例如前端 kb-web、已删的 kb-auth）
            return inList ? DOWN : MISSING;
        }
        // 期望集内却零实例：本进程见过 或 Nacos 仍留有条目 → 服务下线；否则是名字对不上
        return (everSeen || inList) ? DOWN : MISSING;
    }

    /**
     * 是否对用户可用。
     * <ul>
     *   <li>{@code OK}：实例存在，可用</li>
     *   <li>{@code UNEXPECTED}：实例存在（只是没在注册表里声明），对用户仍然可用</li>
     *   <li>{@code UNKNOWN}：这是 Nacos 整体故障导致的瞬时不可判定，若判为不可用会让全部菜单
     *       同时消失；按可用放行，与前端既有的"拉取失败即全放行"降级策略一致</li>
     *   <li>{@code DOWN}/{@code MISSING}：确实不可用</li>
     * </ul>
     */
    public boolean isAvailable() {
        return this == OK || this == UNEXPECTED || this == UNKNOWN;
    }

    /** 映射到旧的 status 字段，保持向后兼容（UP / DOWN / UNKNOWN） */
    public String toLegacyStatus() {
        return switch (this) {
            case OK, UNEXPECTED -> "UP";
            case DOWN -> "DOWN";
            case MISSING, UNKNOWN -> "UNKNOWN";
        };
    }
}
