package com.kb.gateway.modules;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 「曾见过实例」的模块名持久化（Redis Hash：{@code name -> lastSeenMillis}）。
 *
 * <h2>要解决什么问题</h2>
 * {@code DOWN}（服务宕机，运维去重启）与 {@code MISSING}（从未见过，运维去查配置/命名）的
 * 判据是「本进程启动以来是否见过该服务有实例」。这份记忆原本存在进程内 Set 里，
 * <b>网关重启即清零</b>；而 Nacos 会清理掉实例数为 0 的服务名 —— 于是「真正宕机 + 跨过一次网关重启」
 * 的服务会被误判成 MISSING，把运维从"重启服务"误导到"改配置"（ADR §12.11 已记录的已知限制）。
 * 本类把这份记忆外置到 Redis，使其跨网关重启存活。
 *
 * <h2>为什么是 Redis 而不是「启动预热」</h2>
 * 启动预热只能从 Nacos 拉当前的服务名列表，而 Nacos 恰恰会清掉 0 实例的名字 ——
 * 宕机的服务不在列表里，预热拿不到它，问题照旧。必须有一处「上次见到的时间」的外部存储。
 *
 * <h2>降级策略（硬要求）</h2>
 * 这是给健康检查端点用的<b>增强信息，不是主链路</b>。任何 Redis 故障都必须只打 WARN 并退回纯内存，
 * 绝不能让 {@code /modules} 失败或变慢：
 * <ul>
 *   <li><b>装载</b>：启动时一次性拉取（由调用方设超时），失败 → 空集合 + WARN</li>
 *   <li><b>写入</b>：只在「首次见到」时写一次，且<b>异步 fire-and-forget</b>，失败只打 WARN，
 *       不参与响应路径，因此不增加 {@code /modules} 的延迟</li>
 * </ul>
 * 另外 {@code redis-enabled=false} 或压根没有 Redis 连接工厂（未引入依赖）时，
 * 本类退化为纯内存，行为与改造前完全一致。
 *
 * <h2>TTL 与清理</h2>
 * 整个 key 带 TTL，每次写入刷新，时长取 {@code ttl-days}（默认 30 天）；
 * 装载时还会按 {@code lastSeen} 再剪一遍，避免已下线 / 已改名的模块名永久占位
 * （典型：kb-auth 早已改名 auth-center，不该永远留在集合里）。
 */
@Slf4j
@Component
public class ModuleEverSeenStore {

    private final ReactiveStringRedisTemplate redis;
    private final boolean enabled;
    private final String redisKey;
    private final Duration ttl;

    public ModuleEverSeenStore(ObjectProvider<ReactiveRedisConnectionFactory> factoryProvider,
                               @Value("${kb.gateway.modules.ever-seen.redis-enabled:true}") boolean enabled,
                               @Value("${kb.gateway.modules.ever-seen.redis-key:kb:gateway:modules:ever-seen}")
                                       String redisKey,
                               @Value("${kb.gateway.modules.ever-seen.ttl-days:30}") long ttlDays) {
        ReactiveRedisConnectionFactory factory = factoryProvider == null ? null : factoryProvider.getIfAvailable();
        this.enabled = enabled && factory != null;
        this.redis = factory == null ? null : new ReactiveStringRedisTemplate(factory);
        this.redisKey = redisKey;
        this.ttl = Duration.ofDays(ttlDays > 0 ? ttlDays : 30);
        if (!this.enabled) {
            log.info("模块 everSeen 持久化未启用（redis-enabled={}，连接工厂={}），退回纯内存模式："
                            + "网关重启后 DOWN 与 MISSING 仍可能混淆",
                    enabled, factory == null ? "缺失" : "已就绪");
        }
    }

    /** 是否真的在用 Redis（未启用时所有操作都是安全的空操作）。 */
    public boolean isEnabled() {
        return enabled && redis != null;
    }

    /**
     * 装载已持久化的「曾见过」集合。调用方需自行施加超时（启动期一次性调用）。
     * 任何失败都返回空 Map 并打 WARN —— 装载失败只影响 DOWN/MISSING 的区分精度，不影响端点可用性。
     */
    public Mono<Map<String, Long>> load() {
        if (!isEnabled()) {
            return Mono.just(Map.of());
        }
        return redis.opsForHash().entries(redisKey)
                // ReactiveStringRedisTemplate 的 hash ops 泛型是 <String,Object,Object>，key 需显式转 String
                .collectMap(e -> String.valueOf(e.getKey()), e -> parseMillis(e.getValue()))
                .map(m -> pruneExpired(m, System.currentTimeMillis(), ttl.toMillis()))
                .doOnSuccess(m -> log.info("已从 Redis 恢复模块 everSeen 记录 {} 条（key={}，ttl={}）",
                        m.size(), redisKey, ttl))
                .onErrorResume(e -> {
                    log.warn("从 Redis 装载模块 everSeen 失败，本次退回纯内存（只影响 DOWN/MISSING 区分精度）：{}",
                            e.toString());
                    return Mono.just(Map.of());
                });
    }

    /**
     * 记录「刚见到该模块有实例」。只在首次见到时调用一次即可，异步落盘，失败只打 WARN。
     */
    public void markSeen(String name) {
        if (!isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        redis.opsForHash().put(redisKey, name, String.valueOf(now))
                .then(redis.expire(redisKey, ttl))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(v -> { /* 成功无需处理 */ },
                        e -> log.warn("模块 everSeen 写入 Redis 失败（{}）：{}", name, e.toString()));
    }

    // ------------------------------------------------------------------ 可测的纯逻辑

    /** 剪掉超过 TTL 未见的名字；解析不出毫秒数的一律按 0 处理（必然被剪掉）。 */
    static Map<String, Long> pruneExpired(Map<String, Long> raw, long nowMillis, long ttlMillis) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> kept = new HashMap<>();
        for (Map.Entry<String, Long> e : raw.entrySet()) {
            Long last = e.getValue();
            if (last != null && nowMillis - last <= ttlMillis) {
                kept.put(e.getKey(), last);
            }
        }
        return kept;
    }

    static long parseMillis(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
