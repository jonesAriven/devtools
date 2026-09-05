# tools-test 测试环境

> 激活码服务（activecode）的公网测试环境，
> 与生产 `tools.marschat.online`（内网 Deb activecode 18080）相隔离，
> 用于激活码相关功能的上线前验证与回归。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 测试环境 / 工具（激活码服务测试版） |
| 版本 | 激活码服务测试版（与生产同体系：Spring Boot 3.4 + Java 21） |
| 部署位置 | 公网域名经腾讯云2号 nginx 反代 |
| 部署位置 | 上游 `tools_test_backend` = `aliyun.marschat.online:18081`（阿里云主机 120.26.66.182 的 18081 端口） |
| 源码位置 | 与激活码服务同体系（自研 active-manager） |
| 源码位置 | 本地 devtools 工作区未见独立测试版仓库，后端位于阿里云主机 (待确认) |
| CI/CD | 无（测试环境，单独部署于阿里云） |

## 访问入口

- 公网：`https://tools-test.marschat.online`（腾讯云2号 nginx 终止 TLS 443）
- 内网：无独立内网入口，经公网反代指向阿里云 18081
- Tailscale：阿里云 100.89.102.74:18081
  - 上游真实地址，直连需网络可达（mykng 侧实测不可达，见常见问题）

## 全链路

```
浏览器 → 腾讯云2号 nginx (:443, 域名 tools-test.marschat.online)
       → upstream tools_test_backend
       → aliyun.marschat.online:18081  (阿里云主机 120.26.66.182:18081)
```

## 系统设计

### 组件架构

激活码服务（activecode）为自研 Spring Boot 单体。
以下为同体系**生产部署实采 compose 要点**（材料包 activecode.yml），测试环境与其同构：

- 服务名：`activation-code-server`；镜像：`activecode:latest`（`build: .`）。
- 技术栈：Spring Boot 3.4 + Java 21；profile `kafka-log`。
- 数据：MySQL `tools` 库。
  - 生产走 MySQL GR 三节点集群：192.168.31.105:3306 / 192.168.31.182:3307 / 192.168.31.182:3308。
  - 负载均衡 + 故障转移；账密见 Vaultwarden。
- 文件：挂载共享下载目录（QRCodeTools exe 文件）只读，下载功能从该目录列文件。
- 生产容器：`activecode`，映射 `18080:8080`（容器内 8080），compose project `activecode`。

### 我们的集成设计（测试环境角色）

- **实例角色**
  - 测试环境是激活码服务的影子实例，部署在阿里云 18081 端口。
  - 数据与生产隔离，专供上线前验证。
- **与哪些系统连接**
  - 上游：腾讯云2号 nginx（`tools-test.marschat.online` 唯一公网入口）。
  - 后端依赖与生产同构的 MySQL/中间件（阿里云侧具体依赖未实采）(待确认)。
- **为什么这样设计**
  - 激活码涉及发放/校验等业务规则，直接在生产试错风险高。
  - 独立域名 + 独立后端端口使测试流量与生产 `tools.marschat.online`（→ 内网 Deb 18080）完全隔离。
- **关键配置思路**
  - nginx 侧 upstream keepalive 8、读写超时 86400s（长连接友好）。
  - 80 端口一律 404，强制 HTTPS。

### 对外接口概览

- 与生产激活码服务同构的 HTTP 接口（发放/校验/查询等），具体契约未实测 (待确认)。
- 管理页面路径未实测 (待确认)。

## 部署与发布

- 编排与位置
  - nginx 反代配置：`/etc/nginx/sites-available/tools-test.marschat.online`。
    - 位于腾讯云2号（1.117.70.30），`sites-enabled/` 软链生效，2026-07-14 创建。
  - 后端：阿里云主机 120.26.66.182:18081，其 compose/进程托管方式未实采 (待确认)。
- 配置清单（nginx 侧实采）
  - upstream：`tools_test_backend { server aliyun.marschat.online:18081; keepalive 8; }`
  - 监听：`listen 443 ssl; http2 on;`
  - 证书：`/etc/nginx/ssl/marschat.online/fullchain.pem`
  - 透传头：Host / X-Real-IP / X-Forwarded-For / X-Forwarded-Proto / X-Forwarded-Port
  - 超时：`proxy_read_timeout`、`proxy_send_timeout` 均 86400
  - 80 端口同域名 `return 404`，强制 HTTPS
- 发布/升级
  - 无流水线。
  - 后端在阿里云主机，实际构建/启动步骤未实采 (待确认)。
  - 参考同体系生产方式（activecode compose）：
    - `docker compose -p activecode up -d` 启动
    - `docker compose -p activecode down` 停止
    - `docker compose -p activecode logs -f` 日志
  - 测试环境如为同款 compose 则操作一致。
- 回滚
  - nginx 配置可回退 `sites-available` 历史文件并 reload。
  - 后端回滚方式同生产 compose 重建（镜像/代码回退）(待确认)。

## 核心功能与使用

### 功能清单

- 激活码服务测试版：与生产激活码服务同构，但数据/部署隔离，用于：
  - 新激活码规则、发放/校验逻辑的上线前验证。
  - 回归测试，避免直接污染生产（`tools.marschat.online` → 内网 Deb activecode 18080）。
- 访问方式：通过公网测试域名直接打开测试页面/接口。

### 典型操作路径

1. **上线前验证**
   - 本地开发完成激活码相关改动。
   - 部署到阿里云 18081 测试实例。
   - 浏览器打开 `https://tools-test.marschat.online` 走一遍发放/校验流程。
   - 通过后再发生产。
2. **回归对比**
   - 生产 `tools.marschat.online` 与测试 `tools-test.marschat.online` 并列于同一 nginx 反代家族。
   - 可对照验证行为差异。

### 测试环境 vs 生产环境

| 维度 | 生产 tools.marschat.online | 测试 tools-test.marschat.online |
|------|---------------------------|--------------------------------|
| 上游 | 内网 Deb `100.105.196.63:18080`（activecode 容器） | 阿里云 `aliyun.marschat.online:18081` |
| 数据 | 生产 `tools` 库（MySQL GR） | 隔离测试数据（是否独立库未实采）(待确认) |
| 用途 | 正式发放/校验激活码 | 上线前验证、回归 |
| 域名创建 | 早期 | 2026-07-14 入 nginx enabled |

## 依赖与关联

- 依赖
  - 阿里云主机 120.26.66.182 上的 18081 服务进程。
  - 与生产同构推断涉及 MySQL/Redis 等 (待确认)。
- 被依赖/关联系统
  - 生产激活码服务：`tools.marschat.online`（内网 Deb activecode 容器 18080）。
  - 同一 nginx 反代家族：`tools.marschat.online`（→ 100.105.196.63:18080）与 `tools-test.marschat.online`（→ aliyun:18081）并列，互不干扰。

## 运维要点

- 启停方式
  - 后端位于阿里云主机，启停命令未实采 (待确认)。
  - 参考生产 compose：`docker compose -p activecode up -d / down`。
- 日志查看
  - 阿里云侧服务日志路径未实采 (待确认)。
  - nginx 侧见腾讯云2号 `/var/log/nginx/`。
- 数据与备份
  - 测试环境数据隔离策略（是否独立库）未实采 (待确认)。
  - 建议测试数据定期清理，避免长期堆积。
- 常见问题
  - **upstream 与旧材料注记不一致**：
    - 实采 nginx 指向 `aliyun.marschat.online:18081`。
    - 旧材料注记的 `mykng:8087` 已过时或曾误记；以本文实采为准。
    - 若后端曾迁移，请更新基础设施记录。
  - 从 mykng 侧探测 `120.26.66.182:18081` 返回 000（不可达）：
    - 可能该端口仅对腾讯云2号侧放行或当前未运行。
    - 域名经腾讯云2号反代可达，但后端直连性待确认。
  - 若测试环境返回 502：
    - 先查阿里云 18081 进程是否存活。
    - 再查 nginx upstream 解析 `aliyun.marschat.online` 是否正确。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度
  - 引入同体系生产 activecode compose 实采要点（镜像/库/端口映射/compose 命令）。
  - 补 nginx 配置细节与典型操作路径。
- 2026-09-05 v1 首次生成（标注了 upstream 实采值与材料包注记的差异）
