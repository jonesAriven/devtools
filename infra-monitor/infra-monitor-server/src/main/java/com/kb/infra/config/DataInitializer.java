package com.kb.infra.config;

import com.kb.infra.entity.InfraItem;
import com.kb.infra.repository.InfraItemRepository;
import com.kb.infra.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final InfraItemRepository repository;
    private final CryptoUtil cryptoUtil;

    @Override
    public void run(String... args) {
        long count = repository.countByTypeAndDeleted("host", 0);
        if (count > 0) {
            log.info("基础设施数据已存在，跳过初始化（host 数量: {}）", count);
            return;
        }
        log.info("开始初始化基础设施数据...");
        initHosts();
        initCredentials();
        initConfigs();
        initServices();
        log.info("基础设施数据初始化完成");
    }

    private void initHosts() {
        List<Map<String, Object>> hosts = List.of(
            host("旧Windows主机", "192.168.31.243", "物理机", "Windows",
                Map.of("role", "宿主机", "notes", "龙虾 OpenClaw 体系宿主机", "virtualBox", "7.2.2")),
            host("内网Debian", "192.168.31.182", "虚拟机", "Debian",
                Map.of("hypervisor", "VirtualBox 7.2.2", "host", "旧Windows(192.168.31.243)",
                    "memoryGb", 4, "cpuCores", 2, "diskGb", 50, "role", "FRP客户端/Hive/MinIO/MeiliSearch/Nacos")),
            host("mykng-debian", "192.168.31.105", "虚拟机", "Debian 13.5 (trixie)",
                Map.of("hypervisor", "VirtualBox 7.2.2", "host", "旧Windows(192.168.31.243)",
                    "tailscaleIp", "100.93.36.113", "kernel", "6.12.90+deb13.1-amd64",
                    "memoryGb", 8, "cpuCores", 4, "diskGb", 100,
                    "mounts", List.of("/mnt/shared", "/mnt/0000sharebak"),
                    "clash", "v1.18.10, :7890, systemd自启",
                    "role", "知识库/运维平台/CI/CD")),
            host("腾讯云2号", "1.117.70.30", "云服务器", "Linux",
                Map.of("provider", "腾讯云", "bandwidthMbps", 3,
                    "eipNote", "弹性公网IP，带宽约350KB/s",
                    "services", List.of("Nexus", "Nginx", "acme.sh SSL"),
                    "tailscaleIp", "100.110.114.16")),
            host("阿里云(FRP)", "120.26.66.182", "云服务器", "Linux",
                Map.of("provider", "阿里云",
                    "frpServerPort", 7000,
                    "frpDashboardPort", 7500,
                    "frpToken", "YourStrongToken!",
                    "services", List.of("FRP服务端"))),
            host("龙虾主机", "", "物理机", "Linux",
                Map.of("shadowsocksPort", 8388, "shadowsocksPassword", "已加密存储",
                    "shadowsocksMethod", "aes-256-gcm",
                    "role", "出口节点 / RAG MCP / 知识时光机",
                    "memoryRepo", "/home/liangzi/document/",
                    "toolsPath", "/home/liangzi/tools/"))
        );

        int sort = 1;
        for (Map<String, Object> h : hosts) {
            InfraItem item = new InfraItem();
            item.setType("host");
            item.setName((String) h.get("name"));
            item.setCategory((String) h.get("category"));
            item.setDescription((String) h.get("description"));
            @SuppressWarnings("unchecked")
            Map<String, Object> extra = new HashMap<>((Map<String, Object>) h.get("extra"));
            extra.put("ip", h.get("ip"));
            extra.put("os", h.get("os"));
            item.setExtra(extra);
            item.setSortOrder(sort++);
            item.setDeleted(0);
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            repository.save(item);
        }
        log.info("  - 初始化 {} 台主机", hosts.size());
    }

    private Map<String, Object> host(String name, String ip, String category, String os, Map<String, Object> extra) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("ip", ip);
        m.put("category", category);
        m.put("os", os);
        m.put("description", name + " - " + os);
        m.put("extra", extra);
        return m;
    }

    private void initCredentials() {
        List<Map<String, Object>> creds = List.of(
            cred("激活码系统-管理员", "WEB", "admin", "admin123", "内网Debian", "激活码系统",
                "https://tools.marschat.online"),
            cred("FRP仪表盘", "WEB", "admin", "MySecurePassword@2025", "阿里云", "FRP仪表盘",
                "http://120.26.66.182:7500"),
            cred("FRP管理平台", "WEB", "admin", "admin123", "阿里云", "FRP管理平台",
                ""),
            cred("DolphinScheduler", "WEB", "admin", "dolphinscheduler123", "内网Debian", "DolphinScheduler",
                "https://tools.marschat.online/dolphin/"),
            cred("内网MySQL", "DB", "root", "Hwx@1120930", "内网Debian", "MySQL",
                ""),
            cred("tools库", "DB", "tools", "toolsmarschat", "内网Debian", "MySQL/tools",
                ""),
            cred("SMB共享", "OTHER", "share", "share123", "", "SMB",
                "//192.168.31.77/ideaworkspace"),
            cred("JWT密钥", "API_TOKEN", "",
                "YourSuperSecretKeyForJwtTokenGenerationMustBeAtLeast256BitsLong!!", "", "系统密钥",
                "JWT签名"),
            cred("AES密钥", "API_TOKEN", "", "WVszLTI1Ni1nY20ta2V5LTMyLWJ5dGVzISE=", "", "系统密钥",
                "AES-256-GCM加密")
        );

        int sort = 1;
        for (Map<String, Object> c : creds) {
            InfraItem item = new InfraItem();
            item.setType("credential");
            item.setName((String) c.get("name"));
            item.setCategory((String) c.get("type"));
            item.setDescription((String) c.get("serviceName"));
            Map<String, Object> extra = new HashMap<>();
            extra.put("username", c.get("username"));
            String pwd = (String) c.get("password");
            if (pwd != null && !pwd.isEmpty()) {
                extra.put("passwordEncrypted", cryptoUtil.encrypt(pwd));
            }
            extra.put("host", c.get("host"));
            extra.put("serviceName", c.get("serviceName"));
            extra.put("url", c.get("url"));
            item.setExtra(extra);
            item.setSortOrder(sort++);
            item.setDeleted(0);
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            repository.save(item);
        }
        log.info("  - 初始化 {} 条凭据", creds.size());
    }

    private Map<String, Object> cred(String name, String type, String username, String password,
                                      String host, String serviceName, String url) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("username", username);
        m.put("password", password);
        m.put("host", host);
        m.put("serviceName", serviceName);
        m.put("url", url);
        return m;
    }

    private void initConfigs() {
        List<Map<String, Object>> configs = List.of(
            config("FRP隧道状态", "NETWORK", "TABLE",
                List.of(
                    Map.of("publicPort", 3381, "service", "旧Windows RDP", "status", "✅"),
                    Map.of("publicPort", 3382, "service", "旧Windows SSH", "status", "✅"),
                    Map.of("publicPort", 3383, "service", "内网Debian SSH", "status", "✅"),
                    Map.of("publicPort", 3384, "service", "内网Debian RDP", "status", "❌ 已挂"),
                    Map.of("publicPort", 3385, "service", "mykng-debain SSH", "status", "✅"),
                    Map.of("publicPort", 18080, "service", "激活码系统", "status", "✅"),
                    Map.of("publicPort", 18081, "service", "新Windows激活码", "status", "✅")
                ),
                "阿里云FRP服务端:7000"),
            config("SMB共享详情", "STORAGE", "TABLE",
                List.of(
                    Map.of("share", "ideaworkspace", "user", "share/share123", "mount", "/mnt/shared", "note", "日常读写"),
                    Map.of("share", "0000sharebak", "user", "share/share123", "mount", "/mnt/0000sharebak", "note", "数据备份")
                ),
                "挂载命令: mount -t cifs //192.168.31.77/ideaworkspace /mnt/shared -o username=share,password=share123,vers=3.0,_netdev"),
            config("代理链路", "NETWORK", "KEY_VALUE",
                Map.of("出口节点", "龙虾主机 Shadowsocks:8388 (password0, aes-256-gcm)",
                    "中间层", "各机器 Clash Meta v1.18.10 :7890",
                    "机器列表", "内网Debian/阿里云/腾讯云2号/mykng-debain",
                    "代理规则", "google/github/docker/pypi → Proxy, 国内域名 → DIRECT",
                    "Clash二进制", "/usr/local/bin/clash-meta, systemd管理",
                    "DNS说明", "内网Debian开启Clash DNS(家庭宽带DNS污染), 云机器关闭"),
                "全局代理架构"),
            config("mykng Nginx反代", "NETWORK", "TABLE",
                List.of(
                    Map.of("location", "/portal/", "type", "alias", "target", "/var/www/portal/ (SPA前端)"),
                    Map.of("location", "/portal/api/auth/", "type", "proxy", "target", "kb-gateway:8090/kb/api/auth/"),
                    Map.of("location", "/portal/api/sys/", "type", "proxy", "target", "portal-server:8087/portal/"),
                    Map.of("location", "/ops/", "type", "alias", "target", "/var/www/kb-ops-web/ (SPA前端)"),
                    Map.of("location", "/ops/auth-api/", "type", "proxy", "target", "kb-gateway:8090/kb/api/auth/"),
                    Map.of("location", "/ops-api/", "type", "proxy", "target", "kb-ops:8084/kb-ops/"),
                    Map.of("location", "/kb/", "type", "proxy", "target", "kb-web:8091 (前端)"),
                    Map.of("location", "/kb/api/", "type", "proxy", "target", "kb-gateway:8090 (API)"),
                    Map.of("location", "/minio/", "type", "proxy", "target", "127.0.0.1:9001 (MinIO控制台)"),
                    Map.of("location", "/nacos/", "type", "proxy", "target", "127.0.0.1:8848 (Nacos控制台)")
                ),
                "mykng本机Nginx，监听80端口"),
            config("Nexus缓存策略", "CACHE", "TABLE",
                List.of(
                    Map.of("配置项", "negativeCache", "值", "关闭(0)", "说明", "避免临时失败被缓存为不存在"),
                    Map.of("配置项", "contentMaxAge", "值", "525600(365天)", "说明", "制品不可变,缓存一年"),
                    Map.of("配置项", "metadataMaxAge", "值", "1440(24小时)", "说明", "版本列表每天刷新")
                ),
                "Nexus私服缓存配置"),
            config("SSL证书", "CERT", "KEY_VALUE",
                Map.of("工具", "acme.sh + 阿里云DNS API",
                    "类型", "泛域名SAN证书: marschat.online + *.marschat.online",
                    "有效期", "90天, 自动续期",
                    "证书路径", "/etc/nginx/ssl/marschat.online/ (腾讯云2号)",
                    "acme目录", "/root/.acme.sh/ (含阿里云AK配置)"),
                "SSL证书配置"),
            config("RAG记忆增强", "OTHER", "TABLE",
                List.of(
                    Map.of("组件", "Qdrant向量库", "位置", "内网Debian", "端口", "6333(REST)/6334(gRPC)", "说明", "collection: memory, top-5, 阈值0.65"),
                    Map.of("组件", "Embedding服务", "位置", "内网Debian", "端口", "8081", "说明", "bge-small-zh-v1.5(512维CPU推理)"),
                    Map.of("组件", "MCP Server", "位置", "龙虾主机", "端口", "STDIO", "说明", "/opt/rag-mcp/mcp_server.py")
                ),
                "RAG记忆增强组件"),
            config("知识时光机", "OTHER", "KEY_VALUE",
                Map.of("执行时间", "每天02:00(龙虾OpenClaw cron)",
                    "元数据库", "SQLite, /home/liangzi/document/知识时光机/metadata.db",
                    "架构", "v3.0: 采集→解析→入库→矛盾检测→动态验证→双通道输出",
                    "输出A", "YAML→覆盖工作目录(仅managed_by:time-machine文件)",
                    "输出B", "Markdown/PNG→知识看板(人类阅读)"),
                "知识时光机配置"),
            config("记忆仓库架构", "OTHER", "TABLE",
                List.of(
                    Map.of("仓库", "记忆仓库", "Gitee", "openclaw-work-space.git", "本地Hermes", "/root/openclaw-work-space", "本地龙虾", "/home/liangzi/document"),
                    Map.of("仓库", "开发仓库", "Gitee", "devtools.git(dev分支)", "本地Hermes", "/root/devtools", "本地龙虾", "/home/liangzi/devtools")
                ),
                "双Agent共享，Git自动同步: 两台主机×两个仓库=4个定时任务, 每10分钟"),
            config("Nexus缓存预热", "CACHE", "KEY_VALUE",
                Map.of("脚本", "/home/liangzi/tools/nexus-warmup.sh (腾讯云2号)",
                    "定时", "每周日03:30",
                    "覆盖", "npm(Top250+80包) / maven(33个) / pypi(47个) / docker(22个基础镜像)",
                    "日志", "/var/log/nexus-warmup.log",
                    "效果", "react 6.8MB 首次19s→缓存后0.08s (237x提速)"),
                "Nexus缓存预热")
        );

        int sort = 1;
        for (Map<String, Object> c : configs) {
            InfraItem item = new InfraItem();
            item.setType("config");
            item.setName((String) c.get("name"));
            item.setCategory((String) c.get("category"));
            item.setDescription((String) c.get("description"));
            Map<String, Object> extra = new HashMap<>();
            extra.put("content", c.get("content"));
            extra.put("configType", c.get("configType"));
            item.setExtra(extra);
            item.setSortOrder(sort++);
            item.setDeleted(0);
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            repository.save(item);
        }
        log.info("  - 初始化 {} 条配置", configs.size());
    }

    private Map<String, Object> config(String name, String category, String configType, Object content, String description) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("category", category);
        m.put("configType", configType);
        m.put("content", content);
        m.put("description", description);
        return m;
    }

    private void initServices() {
        List<Map<String, Object>> services = List.of(
            svc("mykng知识库", "WEB", "https://kb.marschat.online/kb/",
                "https://kb.marschat.online/kb/api/auth/actuator/health",
                "Spring Boot 3.2 + Vue3 + MySQL + MongoDB + MinIO + MeiliSearch"),
            svc("激活码服务", "WEB", "https://tools.marschat.online",
                "https://tools.marschat.online/actuator/health",
                "Spring Boot 3.4 + Java 21 + MyBatis-Plus + MySQL"),
            svc("Nexus私服", "INFRA", "https://nexus.marschat.online",
                "https://nexus.marschat.online/service/rest/v1/status",
                "Nexus Repository Manager 3"),
            svc("FRP仪表盘", "INFRA", "http://120.26.66.182:7500",
                "http://120.26.66.182:7500",
                "FRP + Spring Boot + Vue2"),
            svc("kb-ops运维管理", "INFRA", "https://kb.marschat.online/ops/",
                null,
                "Spring Boot 3.2 + Java 21 + MyBatis-Plus + MySQL + Redis"),
            svc("Drone CI", "INFRA", "https://ci.marschat.online",
                null,
                "Drone CI + Docker"),
            svc("Nacos服务中心", "INFRA", "https://kb.marschat.online/nacos/",
                "https://kb.marschat.online/nacos/v1/console/health/liveness",
                "Nacos 2.3.x"),
            svc("MinIO对象存储", "INFRA", "https://kb.marschat.online/minio/",
                "https://kb.marschat.online/minio/minio/health/live",
                "MinIO RELEASE.2024"),
            svc("MeiliSearch搜索引擎", "INFRA", "https://kb.marschat.online/meilisearch/",
                "https://kb.marschat.online/meilisearch/health",
                "MeiliSearch 1.12"),
            svc("Vaultwarden密码管理", "WEB", "https://vault.marschat.online",
                null,
                "Vaultwarden (Bitwarden compatible)"),
            svc("Portal主看板", "WEB", "https://main.marschat.online/portal/",
                null,
                "Vue3 + Spring Boot"),
            svc("FRP管理平台", "INFRA", "http://120.26.66.182:18084",
                null,
                "Spring Boot + Vue3 (context-path=/frp_manager/)")
        );

        int sort = 1;
        for (Map<String, Object> s : services) {
            InfraItem item = new InfraItem();
            item.setType("service");
            item.setName((String) s.get("name"));
            item.setCategory((String) s.get("category"));
            item.setDescription((String) s.get("techStack"));
            Map<String, Object> extra = new HashMap<>();
            extra.put("url", s.get("url"));
            extra.put("healthCheckUrl", s.get("healthCheckUrl"));
            extra.put("techStack", s.get("techStack"));
            extra.put("enabled", 1);
            extra.put("status", "UNKNOWN");
            item.setExtra(extra);
            item.setSortOrder(sort++);
            item.setDeleted(0);
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            repository.save(item);
        }
        log.info("  - 初始化 {} 个服务监控", services.size());
    }

    private Map<String, Object> svc(String name, String category, String url, String healthCheckUrl, String techStack) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("category", category);
        m.put("url", url);
        m.put("healthCheckUrl", healthCheckUrl);
        m.put("techStack", techStack);
        return m;
    }
}
