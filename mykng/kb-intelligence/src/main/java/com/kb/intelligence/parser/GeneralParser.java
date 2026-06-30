package com.kb.intelligence.parser;

import cn.hutool.crypto.digest.DigestUtil;
import com.kb.intelligence.entity.*;
import com.kb.intelligence.entity.KnCommand;
import com.kb.intelligence.entity.KnDoc;
import com.kb.intelligence.entity.KnHost;
import com.kb.intelligence.mongo.doc.KnContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeneralParser implements DocParser {

    private final MarkdownParser markdownParser;
    private final ContentCleaner contentCleaner;
    private final CommandExtractor commandExtractor;

    private static final Pattern IP_PATTERN = Pattern.compile(
            "(?:(?:10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})|(?:192\\.168\\.\\d{1,3}\\.\\d{1,3})|(?:172\\.(?:1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3})|(?:100\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}))"
    );
    private static final Pattern TAILSCALE_IP = Pattern.compile("100\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    private static final Pattern TAGS_PATTERN = Pattern.compile("#[\\u4e00-\\u9fa5a-zA-Z0-9_-]+");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ===== 智能提取增强正则 =====

    /** 域名提取：匹配 xxx.online / xxx.com / xxx.cn / xxx.org / xxx.net / xxx.io / xxx.dev / xxx.app / xxx.cloud / xxx.top / xxx.xyz 等 */
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "\\b((?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+(?:online|com|cn|org|net|io|dev|app|cloud|top|xyz|info|me|tv|cc|site|shop|club|fun|tech|store))\\b"
    );

    /** IP:端口 模式（如 192.168.31.77:8080） */
    private static final Pattern IP_PORT_PATTERN = Pattern.compile(
            "((?:10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})|(?:192\\.168\\.\\d{1,3}\\.\\d{1,3})|(?:172\\.(?:1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3})|(?:100\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})):(\\d{2,5})"
    );

    /** 键值对模式：字段：值 或 字段: 值（中英文冒号） */
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "(?:^|\\s|\\|)(用户名|账号|密码|password|pwd|端口|port|SSH|ssh|域名|domain|服务|service|版本|version|角色|role|用途|系统|os)[：:]\\s*([^\\s|,，;；}]+)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    /** 服务名识别（常见运维服务） */
    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile(
            "\\b(Nginx|MySQL|Redis|MongoDB|MinIO|MeiliSearch|Docker|FRP|Tailscale|Clash|Nexus|Jenkins|GitLab|Gitea|Elasticsearch|Kibana|Logstash|Kafka|RabbitMQ|Nacos|Consul|Etcd|Prometheus|Grafana|InfluxDB|PostgreSQL|MariaDB|SQLite|Tomcat|Apache|Node\\.js|Python|Java)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /** 版本号提取（如 v1.6、8.0、7-alpine） */
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "(?:v?(\\d+(?:\\.\\d+){1,3}(?:-\\w+)?))|(?:(?<=:)(\\d+(?:\\.\\d+){0,3}(?:-\\w+)?))",
            Pattern.MULTILINE
    );

    /** markdown 段落分隔（## 标题） */
    private static final Pattern SECTION_HEADER = Pattern.compile("^#{1,6}\\s+.+$", Pattern.MULTILINE);

    /** 已知排除的域名（非真实域名） */
    private static final Set<String> EXCLUDED_DOMAINS = new HashSet<>(Arrays.asList(
            "example.com", "example.cn", "example.org", "example.net",
            "domain.com", "localhost.com", "test.com", "test.cn"
    ));

    /** 服务-默认端口映射（用于推断服务） */
    private static final Map<String, Integer> SERVICE_DEFAULT_PORTS = new HashMap<>();
    static {
        SERVICE_DEFAULT_PORTS.put("nginx", 80);
        SERVICE_DEFAULT_PORTS.put("mysql", 3306);
        SERVICE_DEFAULT_PORTS.put("redis", 6379);
        SERVICE_DEFAULT_PORTS.put("mongodb", 27017);
        SERVICE_DEFAULT_PORTS.put("mongo", 27017);
        SERVICE_DEFAULT_PORTS.put("minio", 9000);
        SERVICE_DEFAULT_PORTS.put("meilisearch", 7700);
        SERVICE_DEFAULT_PORTS.put("docker", 2375);
        SERVICE_DEFAULT_PORTS.put("frp", 7000);
        SERVICE_DEFAULT_PORTS.put("tailscale", 41641);
        SERVICE_DEFAULT_PORTS.put("clash", 7890);
        SERVICE_DEFAULT_PORTS.put("nexus", 8081);
        SERVICE_DEFAULT_PORTS.put("jenkins", 8080);
        SERVICE_DEFAULT_PORTS.put("gitea", 3000);
        SERVICE_DEFAULT_PORTS.put("gitlab", 80);
        SERVICE_DEFAULT_PORTS.put("elasticsearch", 9200);
        SERVICE_DEFAULT_PORTS.put("kibana", 5601);
        SERVICE_DEFAULT_PORTS.put("grafana", 3000);
        SERVICE_DEFAULT_PORTS.put("prometheus", 9090);
        SERVICE_DEFAULT_PORTS.put("postgresql", 5432);
        SERVICE_DEFAULT_PORTS.put("mariadb", 3306);
        SERVICE_DEFAULT_PORTS.put("tomcat", 8080);
        SERVICE_DEFAULT_PORTS.put("apache", 80);
    }

    @Override
    public boolean supports(DocType docType) {
        return true;
    }

    @Override
    public ParseResult parse(String filePath, String fileName, String content, ParseResult result) {
        String baseName = fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;

        KnDoc docMeta = result.getDocMeta();
        if (docMeta == null) {
            docMeta = new KnDoc();
            result.setDocMeta(docMeta);
        }

        docMeta.setTitle(extractTitle(baseName, content));
        docMeta.setFilePath(filePath);
        docMeta.setSourceId(generateSourceId(filePath));
        docMeta.setContentHash(DigestUtil.sha256Hex(content));
        docMeta.setCategory(extractCategory(filePath));
        docMeta.setTags(extractTags(content, baseName));
        docMeta.setSummary(extractSummary(content));
        docMeta.setStatus(1);
        docMeta.setWordCount(content.length());
        docMeta.setCreatedAt(LocalDateTime.now());
        docMeta.setUpdatedAt(LocalDateTime.now());

        List<KnContent.Section> sections = markdownParser.parseSections(content);
        KnContent knContent = new KnContent();
        knContent.setSections(sections);
        knContent.setPlainText(contentCleaner.extractPlainText(content));
        knContent.setWordCount(content.length());
        result.setContent(knContent);

        List<MarkdownParser.CommandBlock> blocks = markdownParser.extractCodeBlocks(content);
        List<KnCommand> commands = commandExtractor.extractCommands(null, blocks);
        result.getCommands().addAll(commands);

        // 智能实体提取（不依赖 LLM，基于正则 + 上下文关联 + 键值对识别）
        extractInlineHosts(content, result);
        extractDomains(content, result);
        extractContextualEntities(content, result);
        extractServicesFromContent(content, result);

        docMeta.setSectionCount(sections.size());
        docMeta.setCommandCount(commands.size());
        docMeta.setEntityCount(result.getHosts().size() + result.getServices().size());

        return result;
    }

    String extractTitle(String fileName, String content) {
        String[] lines = content.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim().replaceAll("\\*+", "");
            }
        }
        return fileName;
    }

    String extractCategory(String filePath) {
        String normalized = filePath.replace("\\", "/").toLowerCase();
        if (normalized.contains("/ops/") || normalized.contains("运维") || normalized.contains("部署")) return "运维";
        if (normalized.contains("/dev/") || normalized.contains("开发")) return "开发";
        if (normalized.contains("/domain/") || normalized.contains("域名")) return "域名";
        if (normalized.contains("/network/") || normalized.contains("网络")) return "网络";
        if (normalized.contains("/plan/") || normalized.contains("方案")) return "方案";
        if (normalized.contains("/kb/") || normalized.contains("知识库")) return "知识库";
        if (normalized.contains("/project/") || normalized.contains("项目")) return "项目";
        if (normalized.contains("/memory/") || normalized.contains("记忆")) return "记忆";
        if (normalized.contains("openclaw")) return "OpenClaw";
        return "其他";
    }

    String extractTags(String content, String fileName) {
        Set<String> tags = new LinkedHashSet<>();
        Matcher m = TAGS_PATTERN.matcher(content);
        while (m.find()) {
            tags.add(m.group());
        }
        String lower = content.toLowerCase();
        if (lower.contains("docker") || lower.contains("容器")) tags.add("#Docker");
        if (lower.contains("nginx")) tags.add("#Nginx");
        if (lower.contains("mysql")) tags.add("#MySQL");
        if (lower.contains("redis")) tags.add("#Redis");
        if (lower.contains("mongodb") || lower.contains("mongo")) tags.add("#MongoDB");
        if (lower.contains("meilisearch")) tags.add("#MeiliSearch");
        if (lower.contains("kubernetes") || lower.contains("k8s")) tags.add("#K8s");
        if (lower.contains("frp") || lower.contains("内网穿透")) tags.add("#FRP");
        if (lower.contains("tailscale")) tags.add("#Tailscale");
        if (lower.contains("nexus") || lower.contains("maven")) tags.add("#Nexus");
        if (lower.contains("minio")) tags.add("#MinIO");
        return String.join(",", tags);
    }

    String extractSummary(String content) {
        String[] lines = content.split("\\n");
        StringBuilder summary = new StringBuilder();
        boolean foundFirst = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) continue;
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("```")) { foundFirst = false; continue; }
            if (trimmed.startsWith("|")) continue;
            String clean = trimmed.replaceAll("[*#>`_\\-]", "").trim();
            if (clean.length() < 5) continue;
            summary.append(clean).append(" ");
            if (summary.length() > 200) break;
        }
        String s = summary.toString().trim();
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    String generateSourceId(String filePath) {
        File f = new File(filePath);
        String path = f.getAbsolutePath().replace("\\", "/");
        String marker = "openclaw-work-space/";
        int idx = path.indexOf(marker);
        if (idx >= 0) {
            return "openclaw://" + path.substring(idx + marker.length());
        }
        return "file://" + path;
    }

    private void extractInlineHosts(String content, ParseResult result) {
        Set<String> existingIps = new HashSet<>();
        for (KnHost h : result.getHosts()) existingIps.add(h.getIp());

        // 增强：同时提取 IP:端口 模式，识别暴露的端口
        Matcher m = IP_PORT_PATTERN.matcher(content);
        Set<String> processedIpPort = new HashSet<>();
        while (m.find()) {
            String ip = m.group(1);
            String portStr = m.group(2);
            String ipPortKey = ip + ":" + portStr;
            if (processedIpPort.contains(ipPortKey)) continue;
            processedIpPort.add(ipPortKey);

            int port = parseIntSafe(portStr);
            if (port <= 0 || port > 65535) continue;

            // 确保主机存在，获取主机索引
            int hostIdx = findOrCreateHostIndex(result, ip, existingIps);

            // 添加端口（去重）
            addPortIfNotExists(result, (long) (hostIdx + 1), port, null);
        }

        // 提取裸 IP（不带端口的）
        Matcher mIp = IP_PATTERN.matcher(content);
        while (mIp.find()) {
            String ip = mIp.group();
            if (!existingIps.contains(ip)) {
                findOrCreateHostIndex(result, ip, existingIps);
            }
        }
    }

    /** 查找或创建主机，返回主机在列表中的索引（0-based） */
    private int findOrCreateHostIndex(ParseResult result, String ip, Set<String> existingIps) {
        for (int i = 0; i < result.getHosts().size(); i++) {
            if (ip.equals(result.getHosts().get(i).getIp())) return i;
        }
        existingIps.add(ip);
        KnHost host = new KnHost();
        host.setIp(ip);
        host.setName(ip);
        host.setSshPort(22);
        host.setStatus("running");
        if (TAILSCALE_IP.matcher(ip).matches()) {
            host.setTailscaleIp(ip);
            host.setRole("tailscale-node");
        }
        result.getHosts().add(host);
        return result.getHosts().size() - 1;
    }

    /** 添加端口（去重，按 hostId + port） */
    private void addPortIfNotExists(ParseResult result, Long hostId, int port, Long serviceId) {
        for (KnPort p : result.getPorts()) {
            if (p.getHostId() != null && p.getHostId().equals(hostId)
                    && p.getPort() != null && p.getPort() == port) {
                return;
            }
        }
        KnPort p = new KnPort();
        p.setHostId(hostId);
        p.setServiceId(serviceId);
        p.setPort(port);
        p.setProtocol("tcp");
        p.setExposed(0);
        result.getPorts().add(p);
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }

    /**
     * 域名智能提取（不依赖 LLM）
     * 匹配 xxx.online / xxx.com / xxx.cn 等常见域名格式
     */
    private void extractDomains(String content, ParseResult result) {
        Set<String> existingDomains = new HashSet<>();
        for (KnDomain d : result.getDomains()) existingDomains.add(d.getDomain());

        // 同时识别 域名 → IP 的映射（如 nexus.marschat.online → 1.117.70.30）
        Map<String, String> domainIpMap = new HashMap<>();

        // 模式1：域名 + 紧跟的 IP（如 `nexus.marschat.online (1.117.70.30)` 或 `nexus.marschat.online → 1.117.70.30`）
        Pattern domainToIp = Pattern.compile(
                "([a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*\\.(?:online|com|cn|org|net|io|dev|app|cloud|top|xyz|info|me|tv|cc|site|shop|club|fun|tech|store))" +
                        "\\s*(?:[(（→]|->|=>|指向|映射)\\s*" +
                        "((?:10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})|(?:192\\.168\\.\\d{1,3}\\.\\d{1,3})|(?:172\\.(?:1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3})|(?:100\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})|(?:[0-9]{1,3}\\.){3}[0-9]{1,3})"
        );

        Matcher mIp = domainToIp.matcher(content);
        while (mIp.find()) {
            String domain = mIp.group(1).toLowerCase();
            String ip = mIp.group(2);
            if (!EXCLUDED_DOMAINS.contains(domain)) {
                domainIpMap.put(domain, ip);
            }
        }

        // 模式2：裸域名提取
        Matcher m = DOMAIN_PATTERN.matcher(content);
        while (m.find()) {
            String domain = m.group(1).toLowerCase();
            if (EXCLUDED_DOMAINS.contains(domain)) continue;
            if (existingDomains.contains(domain)) continue;

            existingDomains.add(domain);
            KnDomain d = new KnDomain();
            d.setDomain(domain);
            d.setStatus("active");

            // 如果有域名→IP 映射，关联主机
            String targetIp = domainIpMap.get(domain);
            if (targetIp != null) {
                for (int i = 0; i < result.getHosts().size(); i++) {
                    KnHost h = result.getHosts().get(i);
                    if (targetIp.equals(h.getIp())) {
                        d.setTargetHostId((long) (i + 1));
                        break;
                    }
                }
            }

            // 推断端口：从 URL 中提取（如 https://nexus.marschat.online:8081）
            Pattern urlPort = Pattern.compile("https?://" + Pattern.quote(domain) + ":(\\d+)");
            Matcher mUrlPort = urlPort.matcher(content);
            if (mUrlPort.find()) {
                d.setTargetPort(parseIntSafe(mUrlPort.group(1)));
            }

            result.getDomains().add(d);
        }
    }

    /**
     * 上下文关联实体提取
     * 在 markdown 段落（## 章节）内，将分散的 IP、用户名、密码、端口关联起来
     */
    private void extractContextualEntities(String content, ParseResult result) {
        String[] lines = content.split("\\n");
        int currentHostIdx = -1;
        String currentSection = "";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // 检测段落标题
            if (trimmed.startsWith("#")) {
                currentSection = trimmed.replaceAll("^#+\\s*", "");
                currentHostIdx = -1;
                continue;
            }

            // 检测 IP（作为段落内主机的锚点）
            Matcher mIp = IP_PATTERN.matcher(trimmed);
            if (mIp.find()) {
                String ip = mIp.group();
                if (ip != null) {
                    // 查找或创建主机
                    for (int j = 0; j < result.getHosts().size(); j++) {
                        if (ip.equals(result.getHosts().get(j).getIp())) {
                            currentHostIdx = j;
                            break;
                        }
                    }
                    if (currentHostIdx == -1) {
                        KnHost host = new KnHost();
                        host.setIp(ip);
                        host.setName(currentSection.isEmpty() ? ip : currentSection);
                        host.setSshPort(22);
                        host.setStatus("running");
                        if (TAILSCALE_IP.matcher(ip).matches()) {
                            host.setTailscaleIp(ip);
                            host.setRole("tailscale-node");
                        }
                        result.getHosts().add(host);
                        currentHostIdx = result.getHosts().size() - 1;
                    }
                }
            }

            if (currentHostIdx < 0) continue;

            KnHost currentHost = result.getHosts().get(currentHostIdx);
            Long hostIdx = (long) (currentHostIdx + 1);

            // 上下文窗口：当前行 + 前后各 3 行
            int startLine = Math.max(0, i - 3);
            int endLine = Math.min(lines.length - 1, i + 3);
            StringBuilder context = new StringBuilder();
            for (int k = startLine; k <= endLine; k++) {
                context.append(lines[k]).append("\n");
            }
            String contextStr = context.toString();

            // 提取用户名/密码（键值对模式）
            Matcher mKv = KEY_VALUE_PATTERN.matcher(trimmed);
            while (mKv.find()) {
                String key = mKv.group(1).toLowerCase();
                String val = mKv.group(2);

                if (key.contains("用户名") || key.contains("账号") || key.equals("user") || key.equals("username")) {
                    if (currentHost.getUsername() == null) {
                        currentHost.setUsername(val);
                    }
                    // 同时创建凭据
                    addCredentialIfNotExists(result, hostIdx, val, null, currentSection);
                } else if (key.contains("密码") || key.equals("password") || key.equals("pwd")) {
                    // 更新已有凭据的密码，或创建新凭据
                    updateOrCreateCredentialPassword(result, hostIdx, val);
                } else if (key.contains("端口") || key.equals("port") || key.equals("ssh")) {
                    int port = parseIntSafe(val.replaceAll("\\D", ""));
                    if (port > 0 && port <= 65535) {
                        if (key.equals("ssh") || key.contains("SSH")) {
                            currentHost.setSshPort(port);
                        } else {
                            addPortIfNotExists(result, hostIdx, port, null);
                        }
                    }
                } else if (key.contains("角色") || key.equals("role") || key.contains("用途")) {
                    if (currentHost.getRole() == null) {
                        currentHost.setRole(val);
                    }
                } else if (key.contains("系统") || key.equals("os")) {
                    if (currentHost.getOsType() == null) {
                        currentHost.setOsType(val);
                    }
                }
            }

            // 上下文中提取 用户名/密码 对（user: xxx pass: xxx）
            Matcher mUserPass = Pattern.compile(
                    "(?:用户名|账号|user|username)[：:]\\s*([^\\s|,，;；}]+)[\\s,，;；|]*" +
                            "(?:密码|password|pwd)[：:]\\s*([^\\s|,，;；}]+)",
                    Pattern.CASE_INSENSITIVE
            ).matcher(contextStr);
            while (mUserPass.find()) {
                String username = mUserPass.group(1);
                String password = mUserPass.group(2);
                addCredentialIfNotExists(result, hostIdx, username, password, currentSection);
            }
        }
    }

    /** 添加凭据（去重：按 username + hostId） */
    private void addCredentialIfNotExists(ParseResult result, Long hostIdx, String username, String password, String section) {
        if (username == null && password == null) return;
        for (KnCredential c : result.getCredentials()) {
            if (c.getHostId() != null && c.getHostId().equals(hostIdx)) {
                if ((username == null || username.equals(c.getUsername()))) {
                    if (password != null && c.getPasswordEncrypted() == null) {
                        c.setPasswordEncrypted(password);
                    }
                    return;
                }
            }
        }
        KnCredential c = new KnCredential();
        c.setHostId(hostIdx);
        c.setUsername(username);
        c.setPasswordEncrypted(password);
        c.setCredType(inferCredType(section, username));
        result.getCredentials().add(c);
    }

    /** 更新已有凭据的密码，或创建新凭据 */
    private void updateOrCreateCredentialPassword(ParseResult result, Long hostIdx, String password) {
        for (KnCredential c : result.getCredentials()) {
            if (c.getHostId() != null && c.getHostId().equals(hostIdx)) {
                if (c.getPasswordEncrypted() == null) {
                    c.setPasswordEncrypted(password);
                    return;
                }
            }
        }
        // 没有已有凭据，创建新的
        KnCredential c = new KnCredential();
        c.setHostId(hostIdx);
        c.setPasswordEncrypted(password);
        c.setCredType("other");
        result.getCredentials().add(c);
    }

    /** 推断凭据类型 */
    private String inferCredType(String section, String username) {
        String lower = (section + " " + (username == null ? "" : username)).toLowerCase();
        if (lower.contains("mysql") || lower.contains("mongo")) return "mysql";
        if (lower.contains("redis")) return "redis";
        if (lower.contains("minio")) return "minio";
        if (lower.contains("ssh") || "root".equals(username) || (username != null && username.startsWith("root"))) return "ssh";
        if (lower.contains("nginx")) return "nginx";
        return "other";
    }

    /**
     * 从内容中识别服务（基于服务名 + 版本号 + 端口）
     */
    private void extractServicesFromContent(String content, ParseResult result) {
        Set<String> existingServiceNames = new HashSet<>();
        for (KnService s : result.getServices()) existingServiceNames.add(s.getName());

        Matcher m = SERVICE_NAME_PATTERN.matcher(content);
        while (m.find()) {
            String serviceName = m.group(1);
            String lowerName = serviceName.toLowerCase();
            if (existingServiceNames.contains(lowerName)) continue;
            existingServiceNames.add(lowerName);

            KnService svc = new KnService();
            svc.setName(serviceName);
            svc.setServiceType(inferServiceType(serviceName));
            svc.setStatus("running");

            // 尝试从上下文提取版本号
            int idx = m.start();
            int contextStart = Math.max(0, idx - 50);
            int contextEnd = Math.min(content.length(), idx + 100);
            String context = content.substring(contextStart, contextEnd);
            Matcher mVer = VERSION_PATTERN.matcher(context);
            if (mVer.find()) {
                String ver = mVer.group(1) != null ? mVer.group(1) : mVer.group(2);
                if (ver != null && ver.length() < 20) {
                    svc.setVersion(ver);
                }
            }

            result.getServices().add(svc);
        }
    }

    /** 推断服务类型 */
    private String inferServiceType(String serviceName) {
        String lower = serviceName.toLowerCase();
        if (lower.contains("nginx") || lower.contains("apache") || lower.contains("tomcat")) return "web";
        if (lower.contains("mysql") || lower.contains("postgres") || lower.contains("mariadb") || lower.contains("sqlite")) return "database";
        if (lower.contains("redis")) return "cache";
        if (lower.contains("mongo")) return "database";
        if (lower.contains("minio")) return "storage";
        if (lower.contains("meilisearch") || lower.contains("elastic")) return "search";
        if (lower.contains("docker")) return "container";
        if (lower.contains("frp") || lower.contains("tailscale") || lower.contains("clash")) return "network";
        if (lower.contains("nexus") || lower.contains("gitea") || lower.contains("gitlab")) return "repository";
        if (lower.contains("jenkins")) return "ci";
        if (lower.contains("prometheus") || lower.contains("grafana") || lower.contains("kibana")) return "monitor";
        if (lower.contains("kafka") || lower.contains("rabbit")) return "mq";
        return "other";
    }
}
