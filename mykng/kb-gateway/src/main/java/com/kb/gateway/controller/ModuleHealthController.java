package com.kb.gateway.controller;

import com.kb.gateway.config.ModuleManifest;
import com.kb.gateway.dto.ModuleState;
import com.kb.gateway.dto.ModuleStatus;
import com.kb.gateway.modules.ModuleEverSeenStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模块健康检查端点（M7-1 新增，2026-09-10 Phase 0.5 改为「期望 vs 实际」比对）
 *
 * <p>端点：{@code GET ${KB_CONTEXT}/api/system/modules}
 *
 * <h2>与改造前的差异</h2>
 * <ol>
 *   <li><b>期望集不再硬编码</b>：改为读取 {@link ModuleManifest}（由 module-registry.yml 派生）。
 *       改名 / 增删模块只需改注册表并跑生成器，不必碰 Java。</li>
 *   <li><b>状态从布尔升级为四态</b>：见 {@link ModuleState}。关键收益是把
 *       {@code DOWN}（服务宕机，运维事件）与 {@code MISSING}（模块名对不上，配置漂移）分开——
 *       后者正是 kb-auth→auth-center 改名事故被隐藏三个月的原因。</li>
 *   <li><b>不再阻塞 Netty 事件循环</b>：{@code DiscoveryClient} 是同步阻塞 API，原先直接跑在
 *       事件循环上且无超时，单个模块的 Nacos 慢响应会串行拖垮整条 {@code /modules}。
 *       现在全部调度到 {@link Schedulers#boundedElastic()}，并按模块施加独立超时。</li>
 * </ol>
 *
 * <h2>降级策略（刻意选择，不是疏漏）</h2>
 * Nacos <b>整体</b>不可达 / 拉取超时时，返回全部 UNKNOWN。UNKNOWN 在
 * {@link ModuleState#isAvailable()} 里算可用，于是前端不会因为一次瞬时抖动就把所有菜单灰掉。
 * 这与前端既有的"拉取失败即全放行"策略一致。注意这不同于"静默失败"：降级会打 WARN 日志，
 * 且语义明确标注为"无法判定"而不是伪装成"一切正常"。
 *
 * <h2>向后兼容</h2>
 * 原有字段 {@code status} / {@code instances} / {@code available} 全部保留（前两者已标记
 * deprecated）。前端可继续只读 {@code available}，行为不变。
 *
 * <h2>已知取舍</h2>
 * <ul>
 *   <li>未加短期缓存，每次请求仍访问 Nacos（N+1 次调用，与改造前一致）。{@code /modules} 目前只在
 *       前端启动时调用一次，暂不构成热点；若后续调用频次上升，可在此加 TTL 缓存。</li>
 *   <li>{@code everSeen} 已外置到 Redis（见 {@link ModuleEverSeenStore}），跨网关重启存活。
 *       Redis 不可用时退回纯内存，此时仍存在“跨重启的宕机被判成 MISSING”的旧歧义，
 *       但前端文案只陈述可观测事实、不替运维下结论，不会把人引向错误的处置方向。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("${kb.gateway.context-path:/kb}/api/system")
public class ModuleHealthController {

    private final DiscoveryClient discoveryClient;
    private final ModuleManifest manifest;

    /** 单模块探活超时（毫秒）。超时按 UNKNOWN 处理，不让一个慢模块拖垮整条返回。 */
    private final long probeTimeoutMs;

    /**
     * 网关进程启动以来"见过实例"的模块名，用于区分 DOWN（曾有实例）与 MISSING（从未见过）。
     * 进程重启会清零，因此判定时还会结合 Nacos 服务列表里是否仍留有该名字；
     * 并在启动时从 {@link ModuleEverSeenStore} 装载历史记录，使这份记忆跨重启存活。
     */
    private final Set<String> everSeen = ConcurrentHashMap.newKeySet();

    /** everSeen 的外部持久化（Redis）。未启用 / Redis 不可用时为空操作，退回纯内存。 */
    private final ModuleEverSeenStore everSeenStore;

    public ModuleHealthController(DiscoveryClient discoveryClient,
                                  ModuleManifest manifest,
                                  ModuleEverSeenStore everSeenStore,
                                  @Value("${kb.gateway.modules.probe-timeout-ms:800}") long probeTimeoutMs) {
        this.discoveryClient = discoveryClient;
        this.manifest = manifest;
        this.everSeenStore = everSeenStore;
        this.probeTimeoutMs = probeTimeoutMs > 0 ? probeTimeoutMs : 800L;
    }

    /**
     * 启动期装载 everSeen 历史。这里允许阻塞（初始化阶段，不在 Netty 事件循环上）且设了上限；
     * 失败只打 WARN —— 装载失败仅降低 DOWN/MISSING 的区分精度，不影响端点可用。
     */
    @PostConstruct
    public void restoreEverSeen() {
        if (!everSeenStore.isEnabled()) {
            return;
        }
        try {
            Map<String, Long> restored = everSeenStore.load().block(Duration.ofSeconds(3));
            if (restored != null && !restored.isEmpty()) {
                everSeen.addAll(restored.keySet());
                log.info("模块 everSeen 已从持久化恢复 {} 条：{}", restored.size(), restored.keySet());
            }
        } catch (Exception e) {
            log.warn("模块 everSeen 装载失败，本次以纯内存运行（只影响 DOWN/MISSING 区分精度）：{}",
                    e.toString());
        }
    }

    /**
     * 获取所有模块状态：期望集 ∪ Nacos 实际注册集。
     *
     * <p>返回项只保留两类——期望集内的模块（供前端显隐/灰显），以及期望集外但实例数 &gt; 0 的
     * "野模块"（提示注册表漏声明）。期望集外且无实例的名字属于 Nacos 残留噪声，一律过滤。
     */
    @GetMapping("/modules")
    public Mono<List<ModuleStatus>> listModules() {
        return fetchServiceNames()
                .flatMapMany(serviceNames -> {
                    Set<String> candidates = new LinkedHashSet<>(manifest.expectedModules());
                    candidates.addAll(serviceNames);
                    return Flux.fromIterable(candidates)
                            .flatMap(name -> probeOne(name, serviceNames));
                })
                .filter(st -> st.isExpected() || st.getActual() > 0)
                .collectSortedList(Comparator.comparing(ModuleStatus::getName))
                .onErrorResume(this::degradeAllToUnknown);
    }

    /**
     * 获取单个模块状态。模块名不在期望集内也允许查询（用于排查野模块），
     * 返回体会用 {@code expected=false} 明示。
     */
    @GetMapping("/modules/{name}")
    public Mono<ModuleStatus> getModule(@PathVariable String name) {
        return fetchServiceNames()
                .flatMap(serviceNames -> probeOne(name, serviceNames))
                .onErrorResume(e -> {
                    log.warn("Nacos 服务列表拉取失败，单模块 {} 探活降级为 UNKNOWN：{}", name, e.toString());
                    return Mono.just(build(name, ModuleState.UNKNOWN, 0, false));
                });
    }

    // ------------------------------------------------------------------ 探活

    /** 拉取 Nacos 服务名列表（阻塞调用，调度到弹性线程池并加整体超时）。失败即向上抛，由调用方统一降级。 */
    private Mono<Set<String>> fetchServiceNames() {
        return Mono.<Set<String>>fromCallable(() -> {
                    List<String> services = discoveryClient.getServices();
                    return new HashSet<>(services == null ? List.<String>of() : services);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofMillis(probeTimeoutMs * 3));
    }

    /** 单模块探活：独立线程 + 独立超时，一个模块异常不影响其他模块。 */
    private Mono<ModuleStatus> probeOne(String name, Set<String> serviceNames) {
        return Mono.fromCallable(() -> {
                    List<ServiceInstance> instances = discoveryClient.getInstances(name);
                    return instances == null ? 0 : instances.size();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofMillis(probeTimeoutMs))
                .map(actual -> evaluate(name, actual, serviceNames))
                .onErrorResume(e -> {
                    log.warn("模块 {} 探活失败（超时或 Nacos 异常）：{}", name, e.toString());
                    return Mono.just(build(name, ModuleState.UNKNOWN, 0,
                            serviceNames != null && serviceNames.contains(name)));
                });
    }

    /**
     * 「期望 vs 实际」判定。**判定规则本身在 {@link ModuleState#resolve}**（纯函数 + 真值表测试），
     * 这里只负责 {@code everSeen} 的副作用与 DTO 组装。
     * <p>
     * 2026-09-10 修正：原先 {@code !expected && actual == 0} 直接返回 DOWN，注释声称
     * "交由上层 filter 过滤"，但单模块端点 {@code getModule(name)} 是直接调 {@code probeOne} 的、
     * 绕过了 filter —— 于是任意野生名字（如前端 kb-web、已删的 kb-auth）都会被报成"服务宕机"。
     * 现在改成：仍在 Nacos 列表里才算 DOWN（残留条目），毫无注册痕迹则判 MISSING（查无此注册）。
     */
    private ModuleStatus evaluate(String name, int actual, Set<String> serviceNames) {
        // 只在"首次见到"时落盘：写入是异步 best-effort，不参与响应路径，因此不增加 /modules 延迟
        if (actual > 0 && everSeen.add(name)) {
            everSeenStore.markSeen(name);
        }
        boolean inList = serviceNames != null && serviceNames.contains(name);
        ModuleState state = ModuleState.resolve(
                manifest.isExpected(name), actual, inList, everSeen.contains(name));
        return build(name, state, actual, inList);
    }

    /** Nacos 整体不可用时的统一降级：全部期望模块报 UNKNOWN（前端视作可用），并打一条 WARN。 */
    private Mono<List<ModuleStatus>> degradeAllToUnknown(Throwable e) {
        log.warn("Nacos 服务列表拉取失败（{}），本次模块探活整体降级为 UNKNOWN，"
                + "前端按可用放行以免误隐藏菜单", e.toString());
        List<ModuleStatus> all = new ArrayList<>();
        for (String name : manifest.expectedModules()) {
            all.add(build(name, ModuleState.UNKNOWN, 0, false));
        }
        return Mono.just(all);
    }

    // ------------------------------------------------------------------ 构造

    private ModuleStatus build(String name, ModuleState state, int actual, boolean inNacosServiceList) {
        boolean expected = manifest.isExpected(name);
        return ModuleStatus.builder()
                .name(name)
                .state(state)
                .expected(expected)
                .actual(actual)
                .everSeen(everSeen.contains(name))
                .inNacosServiceList(inNacosServiceList)
                .available(state.isAvailable())
                .status(state.toLegacyStatus())
                .instances(actual)
                .build();
    }
}
