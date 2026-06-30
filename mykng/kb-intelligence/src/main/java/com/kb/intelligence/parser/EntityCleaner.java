package com.kb.intelligence.parser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 实体值清理与校验工具
 * <p>职责：
 * <ul>
 *   <li>清理 markdown 标记（~~删除线~~、`代码`、**加粗**、__下划线__）</li>
 *   <li>去除首尾引号、括号补充说明</li>
 *   <li>过滤占位符（待确认/待补充/xxx 等）</li>
 *   <li>过滤键名误当值（用户名/密码 等键名不能作为值）</li>
 *   <li>校验值长度与合法性</li>
 * </ul>
 * <p>被 TableParser / GeneralParser / PlanDocParser 等所有解析器共用，
 * 避免清理逻辑散落各处，便于统一维护与扩展。
 */
public final class EntityCleaner {

    private EntityCleaner() {}

    /** 已知占位符（不应当作真实值） */
    private static final Set<String> PLACEHOLDER_VALUES = new HashSet<>(Arrays.asList(
            "待确认", "待补充", "待定", "未知", "tbd", "TBD", "xxx", "XXX", "yyy", "YYY",
            "占位", "placeholder", "n/a", "N/A", "null", "NULL", "无", "暂无", "暂未", "未设置",
            "待提供", "待录入", "见上文", "见上", "同上", "略", "..."
    ));

    /** 键名集合（提取出来的"值"如果等于这些词，说明是键名误当值） */
    private static final Set<String> KEY_NAME_VALUES = new HashSet<>(Arrays.asList(
            "用户", "用户名", "账号", "账户", "密码", "口令", "端口", "域名", "服务", "版本",
            "角色", "用途", "系统", "路径", "地址", "备注", "说明",
            "user", "username", "account", "password", "pwd", "pass", "port", "domain",
            "service", "version", "role", "os", "path", "address", "remark", "desc"
    ));

    /** 外部公共域名（非用户私有运维域名，通常不应作为运维实体记录） */
    private static final Set<String> EXTERNAL_DOMAINS = new HashSet<>(Arrays.asList(
            "www.google.com", "google.com", "gitee.com", "github.com", "gitlab.com",
            "tailscale.com", "npmjs.org", "www.npmjs.com", "pypi.org",
            "nuget.org", "163.com", "www.163.com", "qq.com", "www.qq.com",
            "baidu.com", "www.baidu.com", "bing.com", "www.bing.com",
            "docker.com", "hub.docker.com", "maven.org", "central.maven.org",
            "repo1.maven.org", "mirrors.aliyun.com", "mirrors.tuna.tsinghua.edu.cn",
            "zoho.com", "www.zoho.com", "zohomail.com", "www.zohomail.com",
            "smtp.zoho.com.cn", "zohomail.cn", "www.zohomail.cn",
            "aliyun.com", "www.aliyun.com", "tencent.com", "cloud.tencent.com",
            "huaweicloud.com", "www.huaweicloud.com",
            "wikipedia.org", "www.wikipedia.org", "stackoverflow.com",
            "nodejs.org", "www.nodejs.org", "python.org", "www.python.org",
            "java.com", "www.java.com", "oracle.com", "www.oracle.com"
    ));

    /**
     * 清理 markdown 标记和噪音字符
     * - 去除 ~~删除线~~、`代码`、**加粗**、__下划线__、*斜体*
     * - 去除首尾成对引号（中英文）、成对括号
     * - 去除尾部括号补充说明（如 "root（SSH已启用）" → "root"）
     * - 去除首尾空白
     */
    public static String clean(String val) {
        if (val == null) return null;
        String s = val.trim();
        if (s.isEmpty()) return null;

        // 去除 markdown 标记（成对出现）
        s = s.replaceAll("~~([^~]+)~~", "$1");         // ~~删除线~~
        s = s.replaceAll("`([^`]+)`", "$1");            // `代码`
        s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");  // **加粗**
        s = s.replaceAll("__([^_]+)__", "$1");          // __下划线__
        s = s.replaceAll("\\*([^*]+)\\*", "$1");        // *斜体*
        s = s.replaceAll("==([^=]+)==", "$1");          // ==高亮==

        // 去除首尾成对引号
        s = stripPairQuotes(s);

        // 去除尾部补充说明（如 "root（SSH已启用）" → "root"）
        s = stripTrailingParens(s);

        return s == null || s.isEmpty() ? null : s;
    }

    private static String stripPairQuotes(String s) {
        if (s == null || s.length() < 2) return s;
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        boolean paired =
                (first == '"' && last == '"') ||
                (first == '\'' && last == '\'') ||
                (first == '「' && last == '」') ||
                (first == '「' && last == '」') ||
                (first == '《' && last == '》') ||
                (first == '【' && last == '】') ||
                (first == '[' && last == ']') ||
                (first == '(' && last == ')') ||
                (first == '（' && last == '）');
        if (paired) {
            String inner = s.substring(1, s.length() - 1).trim();
            return stripPairQuotes(inner);  // 递归处理多层引号
        }
        return s;
    }

    private static String stripTrailingParens(String s) {
        if (s == null) return null;
        int idx = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '（' || c == '(') { idx = i; break; }
        }
        if (idx > 0) {
            String before = s.substring(0, idx).trim();
            if (!before.isEmpty()) return before;
        }
        return s;
    }

    /** 判断是否为无效值（占位符/键名当值/过短/纯符号） */
    public static boolean isValid(String val) {
        if (val == null || val.trim().isEmpty()) return false;
        String s = val.trim();
        if (PLACEHOLDER_VALUES.contains(s)) return false;
        if (KEY_NAME_VALUES.contains(s.toLowerCase())) return false;
        // 纯符号或过短（少于2个字符且非数字）
        if (s.length() < 2 && !s.matches("\\d")) return false;
        // 纯 markdown 标记
        if (s.matches("^[~`*_=\\[\\]()（）「」\"'+\\-]+$")) return false;
        return true;
    }

    /** 清理并校验值，无效返回 null */
    public static String normalize(String val) {
        String cleaned = clean(val);
        return isValid(cleaned) ? cleaned : null;
    }

    /** 判断域名是否为外部公共域名（不应记录为运维实体） */
    public static boolean isExternalDomain(String domain) {
        if (domain == null) return false;
        String lower = domain.toLowerCase();
        // 精确匹配外部域名清单
        if (EXTERNAL_DOMAINS.contains(lower)) return true;
        // 模式匹配示例域名：xxx.com / example.com / test.com / sit.xxx.com 等
        if (lower.contains("xxx") || lower.contains("example") || lower.contains("placeholder")) return true;
        // 常见公共图床/工具站（非用户私有运维域名）
        if (lower.equals("draw.io") || lower.equals("youtube.com") || lower.equals("cloudflare.com")
                || lower.equals("medium.com") || lower.equals("reddit.com") || lower.equals("twitter.com")
                || lower.equals("x.com") || lower.equals("linkedin.com") || lower.equals("facebook.com")
                || lower.equals("instagram.com") || lower.equals("whatsapp.com") || lower.equals("telegram.org")
                || lower.equals("slack.com") || lower.equals("discord.com") || lower.equals("notion.so")
                || lower.equals("figma.com") || lower.equals("canva.com") || lower.equals("miro.com")) {
            return true;
        }
        return false;
    }

    /** 判断字符串是否为纯数字（用于端口校验） */
    public static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.matches("\\d+");
    }
}
