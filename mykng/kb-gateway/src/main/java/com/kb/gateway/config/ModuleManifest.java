package com.kb.gateway.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 模块清单（期望集）—— 网关侧的"期望值"来源。
 *
 * <h2>为什么需要它</h2>
 * 改造前 {@code ModuleHealthController} 把模块名硬编码在 {@code KNOWN_MODULES} 常量里，
 * 注释却写着"与 module-registry.yml 保持一致"——而两者并无任何机制保证一致，已经漂移
 * （registry 列了 kb-web、常量里没有）。改名 / 增删模块要手改 4 处，且漂移只表现为
 * "菜单静默消失"。
 * <p>
 * 现在期望集由 {@code mykng/module-registry.yml}（唯一真源）经
 * {@code mykng/gen-registry.py} 派生为 classpath 下的 {@code module-manifest.json}，
 * 网关只读不改。改注册表不跑生成器 → CI 的
 * {@code ModuleManifestConsistencyTest} 比对 {@code sourceSha256} 时会失败。
 *
 * <h2>为什么缺文件就启动失败（fail-fast）</h2>
 * 本文件是<b>打包进 jar 的构建产物</b>，不依赖任何运行时外部环境。它缺失只可能是三类
 * 原因：没跑生成器、没提交、构建脚本把资源漏掉了——全是构建期问题。
 * <p>
 * 反过来，如果这里选择"降级为全部可用"或"降级为空期望集"，就会重演本类要根治的那种
 * 静默失败：期望集空 → 所有模块被报成 UNEXPECTED/MISSING → 菜单集体异常，而日志里
 * 只有一行 WARN。宁可让部署阶段直接失败（流水线红），也不要在生产里带病静默运行。
 */
@Slf4j
@Component
public class ModuleManifest {

    /** 派生产物在 classpath 下的位置，由 mykng/gen-registry.py 生成 */
    public static final String RESOURCE_PATH = "module-manifest.json";

    /** 期望集必须包含的核心模块，缺失即视为产物异常 */
    private static final Set<String> MUST_HAVE = Set.of("kb-gateway", "auth-center");

    @Getter
    private final int schemaVersion;

    /** 生成时注册表的 sha256，供一致性测试比对 */
    @Getter
    private final String sourceSha256;

    @Getter
    private final String contextPath;

    /** 期望集：注册表中 nacos-registered=true 的模块名，已排序、不可变 */
    private final List<String> expectedModules;

    /** 全部模块条目（含不注册 Nacos 的前端模块），key = 模块名 */
    private final Map<String, Entry> modules;

    public ModuleManifest(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        Root root;
        try (InputStream in = resource.getInputStream()) {
            root = objectMapper.readValue(in, Root.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "无法加载模块清单 " + RESOURCE_PATH + "。该文件由 `python3 mykng/gen-registry.py` "
                            + "从 mykng/module-registry.yml 生成，属于必交的构建产物。"
                            + "请先在仓库根执行生成器并提交产物后重新构建。原始错误：" + e, e);
        }

        if (root.getExpectedModules() == null || root.getExpectedModules().isEmpty()) {
            throw new IllegalStateException(
                    RESOURCE_PATH + " 的 expectedModules 为空，无法进行「期望 vs 实际」比对。"
                            + "请检查 module-registry.yml 中是否存在 nacos-registered: true 的模块，并重新运行生成器。");
        }

        this.schemaVersion = root.getSchemaVersion();
        this.sourceSha256 = root.getSourceSha256();
        this.contextPath = root.getContextPath();
        this.expectedModules = List.copyOf(new TreeSet<>(root.getExpectedModules()));

        Map<String, Entry> map = new LinkedHashMap<>();
        if (root.getModules() != null) {
            for (Entry e : root.getModules()) {
                if (e != null && e.getName() != null && !e.getName().isBlank()) {
                    map.put(e.getName(), e);
                }
            }
        }
        this.modules = Collections.unmodifiableMap(map);

        Set<String> missing = new TreeSet<>(MUST_HAVE);
        missing.removeAll(this.expectedModules);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    RESOURCE_PATH + " 的 expectedModules 缺少核心模块 " + missing
                            + "，产物可疑，拒绝启动。实际值=" + this.expectedModules);
        }

        log.info("模块清单已加载：schemaVersion={} 期望集={} 源注册表 sha256={}",
                schemaVersion, this.expectedModules, shortSha());
    }

    /** 期望集（不可变、有序） */
    public List<String> expectedModules() {
        return expectedModules;
    }

    /** 该模块名是否在期望集内 */
    public boolean isExpected(String name) {
        return name != null && expectedModules.contains(name);
    }

    /** 按名取模块条目，可能为 null（当 name 属于"野模块"时） */
    public Entry entry(String name) {
        return modules.get(name);
    }

    /** 全部模块条目（不可变） */
    public Map<String, Entry> modules() {
        return modules;
    }

    private String shortSha() {
        return sourceSha256 == null || sourceSha256.length() < 12
                ? String.valueOf(sourceSha256)
                : sourceSha256.substring(0, 12) + "…";
    }

    // ------------------------------------------------------------ JSON 结构

    /** module-manifest.json 的根结构 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Root {
        private int schemaVersion;
        private String source;
        private String sourceSha256;
        private String contextPath;
        private List<String> expectedModules;
        private List<Entry> modules;
    }

    /** 单个模块条目；忽略未知字段，便于生成器向后兼容地新增字段 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entry {
        private String name;
        private String type;
        private boolean nacosRegistered;
        private Integer containerPort;
        private Integer hostPort;
        private String healthPath;
        private String registryAlias;
        private String database;
        private List<String> routes;
        private String description;
    }
}
