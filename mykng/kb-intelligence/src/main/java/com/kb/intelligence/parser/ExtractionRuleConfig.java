package com.kb.intelligence.parser;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * L2 YAML 规则引擎配置 POJO
 * <p>对应 classpath:extraction-rules.yml 结构。
 * <p>字段名采用 camelCase，由 {@link ExtractionRuleLoader} 内部将 YAML 的 kebab-case 自动转换匹配。
 */
@Data
public class ExtractionRuleConfig {

    /** 配置版本号 */
    private String version;

    /** 1. 实体提取正则模式（key: 模式名, value: 正则字符串） */
    private Map<String, String> patterns;

    /** 2. 服务名识别清单（用于从文档中匹配服务实体） */
    private List<String> serviceNames;

    /** 3. 服务 → 默认端口映射（用于推断服务、补全端口） */
    private Map<String, Integer> serviceDefaultPorts;

    /** 4. 服务类型推断规则（key: 服务类型, value: 服务名关键词清单） */
    private Map<String, List<String>> serviceTypeInference;

    /** 5. 主机别名 → IP 映射（用于域名→主机关联、文档主机名识别） */
    private Map<String, String> hostAliases;

    /** 6. 域名前缀 → 主机名提示（再通过 hostAliases 解析为 IP） */
    private Map<String, String> domainHostHints;

    /** 7. 噪音过滤清单 */
    private NoiseFilter noiseFilter;

    @Data
    public static class NoiseFilter {
        /** 占位符值（不应当作真实值） */
        private List<String> placeholders;

        /** 键名集合（提取的"值"等于这些词说明是键名误当值） */
        private List<String> keyNames;

        /** 外部公共域名（非用户私有运维域名，不应记录） */
        private List<String> externalDomains;

        /** 外部域名模式（正则匹配，如 .*xxx.* / .*example.*） */
        private List<String> externalDomainPatterns;
    }
}
