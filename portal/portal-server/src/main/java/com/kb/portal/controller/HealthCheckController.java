package com.kb.portal.controller;

import com.kb.portal.entity.PortalSystem;
import com.kb.portal.mapper.PortalSystemMapper;
import com.marschat.common.result.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 系统健康检测（后端代跑）—— 台账 L026。
 *
 * <p>背景：面板 31 个系统的「健康检测」原先在前端直接 fetch 各系统的 {@code healthCheckUrl}，
 * 而面板跑在 {@code main.marschat.online}、目标分散在 {@code kb.marschat.online} / {@code tools.marschat.online} 等
 * 异域，属跨域请求且目标未返回 CORS 头 → 浏览器必然失败，31 个系统全部显示「未检测」。
 *
 * <p>改为 portal-server 同源代跑：后端在服务端发请求，不存在跨域；且能读到真实 HTTP 状态码
 * （前端 {@code mode:'no-cors'} 只能拿到 opaque 响应，状态码恒为 0，无法区分 200 与 503）。
 *
 * <p>安全：接口只接受「系统 id」，URL 一律从数据库读取，<b>不接收任意 URL</b>，因此不存在 SSRF 面。
 * 该路径落在 {@code /api/sys/**} 下，受 {@link com.kb.portal.config.JwtInterceptor} 保护，仅登录用户可触发。
 */
@Slf4j
@RestController
@RequestMapping("/api/sys/health")
@RequiredArgsConstructor
public class HealthCheckController {

    private final PortalSystemMapper portalSystemMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 批量健康检测：入参 ids → 出参每项 { id, url, status, latency, httpStatus, error } */
    @PostMapping("/check")
    public Result<List<Map<String, Object>>> check(@RequestBody(required = false) HealthCheckRequest request) {
        List<Long> ids = request == null ? null : request.getIds();
        if (ids == null || ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<PortalSystem> systems = portalSystemMapper.selectBatchIds(ids);
        if (systems.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());
        // 大池会同时打满 30+ 个目标；限制并发避免瞬时不响应
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, systems.size()));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (PortalSystem sys : systems) {
                futures.add(pool.submit(() -> results.add(probe(sys))));
            }
            for (Future<?> f : futures) {
                try {
                    f.get(9, TimeUnit.SECONDS);
                } catch (Exception ignore) {
                    // 单项超时不阻塞整体；probe 内部已有 5s 超时
                }
            }
        } finally {
            pool.shutdown();
        }
        return Result.ok(results);
    }

    private Map<String, Object> probe(PortalSystem sys) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", sys.getId());
        String url = sys.getHealthCheckUrl();
        r.put("url", url);
        if (url == null || url.isBlank()) {
            r.put("status", "unknown");
            r.put("error", "未配置健康检查地址");
            return r;
        }
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            int code = resp.statusCode();
            r.put("latency", System.currentTimeMillis() - start);
            r.put("httpStatus", code);
            if (code >= 200 && code < 400) {
                r.put("status", "online");
            } else if (code == 401 || code == 403) {
                // 服务本身在线，只是该端点需要鉴权 —— 不能计为离线
                r.put("status", "online");
                r.put("error", "服务在线，健康端点需鉴权（HTTP " + code + "）");
            } else {
                r.put("status", "offline");
                r.put("error", "HTTP " + code);
            }
        } catch (Exception e) {
            r.put("latency", System.currentTimeMillis() - start);
            r.put("status", "offline");
            r.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return r;
    }

    @Data
    public static class HealthCheckRequest {
        private List<Long> ids;
    }
}
