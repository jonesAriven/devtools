package com.kb.intelligence.parser;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * L2 YAML 规则引擎加载器
 * <p>启动时通过 @PostConstruct 加载 classpath:extraction-rules.yml 到静态字段，
 * 供 {@link EntityCleaner} / {@link GeneralParser} 等解析器使用。
 * <p>设计要点：
 * <ul>
 *   <li>静态字段 + 静态 getter：解析器无需注入 Bean，保持 static 调用接口不变</li>
 *   <li>加载失败不抛异常，返回 null/空集合，调用方 fallback 到硬编码兜底</li>
 *   <li>支持热更新：调用 {@link #reload()} 重新读取 YAML（运维时可手动触发）</li>
 * </ul>
 */
@Slf4j
@Component
public class ExtractionRuleLoader {

    /** 静态配置实例，volatile 保证多线程可见性 */
    private static volatile ExtractionRuleConfig CONFIG = null;

    @PostConstruct
    public void init() {
        reload();
    }

    /** 重新加载 YAML 配置（运维时可手动触发） */
    public synchronized void reload() {
        try (InputStream is = new ClassPathResource("extraction-rules.yml").getInputStream()) {
            Constructor constructor = new Constructor(ExtractionRuleConfig.class, new LoaderOptions());
            // 自定义 PropertyUtils：把 YAML 的 kebab-case 自动转换为 POJO 的 camelCase
            PropertyUtils propertyUtils = new PropertyUtils() {
                @Override
                public Property getProperty(Class<?> type, String name) {
                    String camel = kebabToCamel(name);
                    return super.getProperty(type, camel);
                }
            };
            propertyUtils.setSkipMissingProperties(true);
            constructor.setPropertyUtils(propertyUtils);

            Yaml yaml = new Yaml(constructor);
            ExtractionRuleConfig cfg = yaml.loadAs(is, ExtractionRuleConfig.class);
            CONFIG = cfg;

            log.info("L2 提取规则加载成功 version={} patterns={} services={} defaultPorts={} hostAliases={} domainHints={} placeholders={} keyNames={} externalDomains={}",
                    cfg.getVersion(),
                    size(cfg.getPatterns()),
                    size(cfg.getServiceNames()),
                    size(cfg.getServiceDefaultPorts()),
                    size(cfg.getHostAliases()),
                    size(cfg.getDomainHostHints()),
                    cfg.getNoiseFilter() != null ? size(cfg.getNoiseFilter().getPlaceholders()) : 0,
                    cfg.getNoiseFilter() != null ? size(cfg.getNoiseFilter().getKeyNames()) : 0,
                    cfg.getNoiseFilter() != null ? size(cfg.getNoiseFilter().getExternalDomains()) : 0);
        } catch (Exception e) {
            log.warn("L2 提取规则加载失败，将使用硬编码兜底: {}", e.getMessage());
            CONFIG = null;
        }
    }

    private static int size(Collection<?> c) {
        return c == null ? 0 : c.size();
    }

    private static int size(Map<?, ?> m) {
        return m == null ? 0 : m.size();
    }

    /** kebab-case → camelCase 转换（snakeyaml 默认按字段名精确匹配） */
    private static String kebabToCamel(String s) {
        if (s == null || s.indexOf('-') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        boolean upper = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-') {
                upper = true;
                continue;
            }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }

    // ===== 静态 getter（即使 Spring 未初始化也可调用，返回 null/空集合由调用方 fallback） =====

    public static ExtractionRuleConfig getConfig() {
        return CONFIG;
    }

    public static Set<String> getPlaceholders() {
        ExtractionRuleConfig.NoiseFilter nf = noiseFilter();
        if (nf == null || nf.getPlaceholders() == null) return Collections.emptySet();
        return new HashSet<>(nf.getPlaceholders());
    }

    public static Set<String> getKeyNames() {
        ExtractionRuleConfig.NoiseFilter nf = noiseFilter();
        if (nf == null || nf.getKeyNames() == null) return Collections.emptySet();
        return new HashSet<>(nf.getKeyNames());
    }

    public static Set<String> getExternalDomains() {
        ExtractionRuleConfig.NoiseFilter nf = noiseFilter();
        if (nf == null || nf.getExternalDomains() == null) return Collections.emptySet();
        return new HashSet<>(nf.getExternalDomains());
    }

    public static List<String> getExternalDomainPatterns() {
        ExtractionRuleConfig.NoiseFilter nf = noiseFilter();
        if (nf == null || nf.getExternalDomainPatterns() == null) return Collections.emptyList();
        return nf.getExternalDomainPatterns();
    }

    public static List<String> getServiceNames() {
        ExtractionRuleConfig c = CONFIG;
        if (c == null || c.getServiceNames() == null) return Collections.emptyList();
        return c.getServiceNames();
    }

    public static Map<String, Integer> getServiceDefaultPorts() {
        ExtractionRuleConfig c = CONFIG;
        if (c == null || c.getServiceDefaultPorts() == null) return Collections.emptyMap();
        return c.getServiceDefaultPorts();
    }

    public static Map<String, String> getHostAliases() {
        ExtractionRuleConfig c = CONFIG;
        if (c == null || c.getHostAliases() == null) return Collections.emptyMap();
        return c.getHostAliases();
    }

    public static Map<String, String> getDomainHostHints() {
        ExtractionRuleConfig c = CONFIG;
        if (c == null || c.getDomainHostHints() == null) return Collections.emptyMap();
        return c.getDomainHostHints();
    }

    private static ExtractionRuleConfig.NoiseFilter noiseFilter() {
        ExtractionRuleConfig c = CONFIG;
        return c == null ? null : c.getNoiseFilter();
    }
}
