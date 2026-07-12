package com.kb.portal.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cn.hutool.crypto.digest.BCrypt;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始初始化 portal 数据库...");
        try {
            initTable();
            initData();
            log.info("portal 数据库初始化完成");
        } catch (Exception e) {
            log.error("portal 数据库初始化失败", e);
        }
    }

    private void initTable() throws Exception {
        ClassPathResource resource = new ClassPathResource("sql/portal_init.sql");
        String sql = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);

        try (Connection conn = jdbcTemplate.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {

            String[] statements = sql.split(";");
            for (String s : statements) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--") && !trimmed.startsWith("SET") && !trimmed.startsWith("PREPARE") && !trimmed.startsWith("EXECUTE") && !trimmed.startsWith("DEALLOCATE")) {
                    try {
                        stmt.execute(trimmed);
                    } catch (Exception e) {
                        log.debug("执行 SQL 跳过（可能已存在）: {}", trimmed.substring(0, Math.min(50, trimmed.length())));
                    }
                }
            }
        }
    }

    private void initAdminUser() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND username = 'admin'", Integer.class);
            if (count != null && count > 0) {
                log.info("admin 用户已存在，跳过初始化");
                return;
            }

            log.info("初始化 admin 默认用户...");
            String encodedPassword = BCrypt.hashpw("admin123", BCrypt.gensalt());
            jdbcTemplate.update(
                    "INSERT INTO sys_user (username, password, nickname, status) VALUES (?, ?, ?, ?)",
                    "admin", encodedPassword, "管理员", 1
            );
            log.info("admin 用户初始化完成（默认密码: admin123）");
        } catch (Exception e) {
            log.warn("初始化 admin 用户失败（可能表不存在或已存在）: {}", e.getMessage());
        }
    }

    private void initData() {
        initAdminUser();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM portal_system WHERE deleted = 0", Integer.class);
        if (count != null && count > 0) {
            log.info("portal_system 表已有数据，跳过初始化（{}条）", count);
            return;
        }

        log.info("portal_system 表为空，开始初始化默认数据...");

        List<Object[]> systems = Arrays.asList(
            new Object[]{"mykng 知识库", "个人知识库系统：文档管理、文件存储、网页收藏、全文搜索、知识引擎", "https://kb.marschat.online/kb/", "Reading", "#409eff", "web", 1, "https://kb.marschat.online/kb/api/auth/actuator/health", "[{\"label\":\"产品文档\",\"url\":\"https://kb.marschat.online/kb/#/dashboard\"},{\"label\":\"部署方案\",\"url\":\"https://kb.marschat.online/kb/\"}]", null, "Spring Boot 3.2 + Vue3 + MySQL + MongoDB + MinIO + MeiliSearch", 1},
            new Object[]{"激活码服务", "激活码生成与管理平台：RSA签名、设备绑定、验证工具库", "https://tools.marschat.online", "Key", "#e6a23c", "web", 1, "https://tools.marschat.online/actuator/health", "[{\"label\":\"设计文档\",\"url\":\"https://tools.marschat.online\"}]", null, "Spring Boot 3.4 + Java 21 + MyBatis-Plus + MySQL", 2},
            new Object[]{"激活码使用页面", "激活码在线解析与验证工具，无需登录即可使用", "https://tools.marschat.online/activecode/index.html", "Promotion", "#e6a23c", "web", 1, null, null, null, "激活码解析与验证（无需登录）", 3},
            new Object[]{"Nexus 私服", "统一包管理仓库：npm / Maven / pip / Docker 全栈制品管理", "https://nexus.marschat.online", "Box", "#67c23a", "infra", 1, "https://nexus.marschat.online/service/rest/v1/status", null, null, "Nexus Repository Manager 3", 3},
            new Object[]{"Nexus 私服（内网）", "mykng 内网 Nexus 私服：npm / Maven / pip / Docker 全栈制品管理", "http://192.168.31.105:8081", "Box", "#67c23a", "infra", 1, "http://192.168.31.105:8081/service/rest/v1/status", null, null, "Nexus Repository Manager 3 (mykng 内网)", 4},
            new Object[]{"FRP 仪表盘", "FRP 内网穿透管理：隧道监控、客户端管理、配置预览", "http://120.26.66.182:7500", "Connection", "#f56c6c", "infra", 1, "http://120.26.66.182:7500", null, null, "FRP + Spring Boot + Vue2", 5},
            new Object[]{"DolphinScheduler", "分布式任务调度平台：定时任务编排、工作流管理", "https://tools.marschat.online/dolphin/", "Calendar", "#909399", "infra", 1, "https://tools.marschat.online/dolphin/", null, null, "Apache DolphinScheduler 3.x", 6},
            new Object[]{"kb-ops 运维管理", "运维管理平台：主机/服务/端口/凭据/域名/依赖/部署记录/看板/矛盾检测/导入", "https://kb.marschat.online/ops/", "SetUp", "#9c27b0", "infra", 1, null, "[{\"label\":\"项目源码\",\"url\":\"https://github.com/\"}]", null, "Spring Boot 3.2 + Java 21 + MyBatis-Plus + MySQL + Redis", 7},
            new Object[]{"Woodpecker CI", "持续集成平台：自动化构建、测试、部署流水线", "https://woodci.marschat.online/repos", "Cpu", "#06b6d4", "infra", 1, null, null, null, "Woodpecker CI", 8},
            new Object[]{"Drone CI", "持续集成平台：已下线", "https://ci.marschat.online", "Cpu", "#06b6d4", "infra", 0, null, null, null, "Drone CI + Docker", 50},
            new Object[]{"Nacos 服务中心", "服务注册与配置中心：服务发现、配置管理、服务治理", "https://kb.marschat.online/nacos/", "Connection", "#67c23a", "infra", 1, null, null, null, "Nacos 2.3.x", 9},
            new Object[]{"MinIO 对象存储", "分布式对象存储：文件管理、桶管理、访问策略、监控", "https://kb.marschat.online/minio/", "FolderOpened", "#e6a23c", "infra", 1, null, null, null, "MinIO RELEASE.2024", 10},
            new Object[]{"MeiliSearch 搜索引擎", "全文搜索引擎：索引管理、搜索预览、API密钥管理", "https://kb.marschat.online/meilisearch/", "Search", "#ff6b6b", "infra", 1, null, null, null, "MeiliSearch 1.12", 11},
            new Object[]{"Vaultwarden 密码管理", "密码管理器：个人密码、安全笔记、身份认证器、组织共享", "https://vault.marschat.online", "Lock", "#175ddc", "web", 1, null, null, null, "Vaultwarden (Bitwarden compatible)", 12},
            new Object[]{"QRCodeTool (C#)", "二维码扫描与生成工具，已接入激活码验证体系", null, "Aim", "#9c27b0", "tool", 1, null, "[{\"label\":\"使用说明\",\"url\":\"https://kb.marschat.online/kb/\"}]", "/portal/downloads/QRCodeTool.zip", "C# .NET 6 WinForms", 20},
            new Object[]{"激活码验证库", "C# 类库：设备指纹、防调试、时间篡改检测、RSA验证", null, "Lock", "#9c27b0", "tool", 1, null, null, "/portal/downloads/Jones.Activation.dll", "C# .NET 6 类库 (Jones.Activation.dll)", 21},
            new Object[]{"QR Generator (Rust)", "Rust 版二维码工具：高性能截图识别与生成", null, "Aim", "#9c27b0", "tool", 1, null, null, "/portal/downloads/qr-rust.zip", "Rust + egui", 22},
            new Object[]{"Git 自动化工具", "批量 Git 仓库标签管理：自动打标签、版本号管理", null, "Files", "#9c27b0", "tool", 1, null, null, "/portal/downloads/git-auto.zip", "Python + Bat", 23},
            new Object[]{"项目文档中心", "devtools 项目文档：架构设计、部署手册、API规范", "https://kb.marschat.online/kb/", "Document", "#00bcd4", "doc", 1, null, "[{\"label\":\"mykng 文档\",\"url\":\"https://kb.marschat.online/kb/\"},{\"label\":\"激活码文档\",\"url\":\"https://tools.marschat.online\"},{\"label\":\"Nexus 文档\",\"url\":\"https://nexus.marschat.online\"}]", null, null, 30},
            new Object[]{"OpenClaw 知识库", "龙虾 OpenClaw 体系文档：主机清单、凭据汇总、运维方案", "https://kb.marschat.online/kb/", "Memo", "#00bcd4", "doc", 1, null, null, null, null, 31}
        );

        String sql = "INSERT INTO portal_system (name, description, url, icon, color, category, status, health_check_url, docs, download_path, tech_stack, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        for (Object[] sys : systems) {
            jdbcTemplate.update(sql, sys);
        }

        log.info("初始化完成，共插入 {} 条系统记录", systems.size());
    }
}
