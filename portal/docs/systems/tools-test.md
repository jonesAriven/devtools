# tools-test 测试环境

> 激活码服务的公网测试环境，与生产 `tools.marschat.online`（内网 Deb activecode 18080）相隔离，用于激活码相关功能的上线前验证。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 测试环境 / 工具（激活码服务测试版） |
| 版本 | 激活码服务测试版（portal 表标注：Spring Boot 3.4 + Java 21） |
| 部署位置 | 公网域名经腾讯云2号 nginx 反代；上游 `tools_test_backend` = `aliyun.marschat.online:18081`（即阿里云主机 120.26.66.182 的 18081 端口） |
| 源码位置 | 与激活码服务同体系（自研）；本地 devtools 工作区未见独立仓库，后端位于阿里云主机 (待确认) |
| CI/CD | 无（测试环境，单独部署于阿里云） |

## 访问入口

- 公网：`https://tools-test.marschat.online`（腾讯云2号 nginx 终止 TLS 443）
- 内网：无独立内网入口，经公网反代指向阿里云 18081
- Tailscale：阿里云 100.89.102.74:18081（上游真实地址，直连需网络可达）

## 全链路

```
浏览器 → 腾讯云2号 nginx (:443, 域名 tools-test.marschat.online)
       → upstream tools_test_backend
       → aliyun.marschat.online:18081  (阿里云主机 120.26.66.182:18081)
```

## 实采依据（2026-09-05 腾讯云2号 nginx）

`/etc/nginx/sites-available/tools-test.marschat.online` 全文要点：

```
upstream tools_test_backend {
    server aliyun.marschat.online:18081;
    keepalive 8;
}

server {
    server_name tools-test.marschat.online;
    location / {
        proxy_pass http://tools_test_backend;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_read_timeout 86400;
        proxy_send_timeout 86400;
    }
    listen 443 ssl; http2 on;
    ssl_certificate /etc/nginx/ssl/marschat.online/fullchain.pem;
    ...
}
```

- `sites-enabled/` 中存在软链 `tools-test.marschat.online -> /etc/nginx/sites-available/tools-test.marschat.online`（7月14日创建），配置生效中。
- 80 端口 `server_name tools-test.marschat.online` 一律 `return 404`，强制走 HTTPS。

> 重要差异：材料包第 5 节曾注「upstream → mykng portal-server 8087 激活码测试环境」，与**实采 nginx 配置不符**——实际指向 `aliyun.marschat.online:18081`。本文以实采 nginx 为准，并在「常见问题」中标注。

## 核心功能与使用

- 激活码服务测试版：与生产激活码服务同构，但数据/部署隔离，用于：
  - 新激活码规则、发放/校验逻辑的上线前验证。
  - 回归测试，避免直接污染生产（`tools.marschat.online` → 内网 Deb activecode 18080）。
- 访问方式：通过公网测试域名直接打开测试页面/接口。

> 具体页面路径、接口契约、管理后台地址未经实测，按激活码服务测试用途描述；如启用后需补充界面细节。

## 测试环境 vs 生产环境

| 维度 | 生产 tools.marschat.online | 测试 tools-test.marschat.online |
|------|---------------------------|--------------------------------|
| 上游 | 内网 Deb `100.105.196.63:18080`（activecode 容器） | 阿里云 `aliyun.marschat.online:18081` |
| 数据 | 生产库 | 隔离测试数据 |
| 用途 | 正式发放/校验激活码 | 上线前验证、回归 |
| 域名创建 | 早期 | 2026-07-14 入 nginx enabled |

两点并列于同一 nginx 反代家族，互不干扰。

## 依赖与关联

- 依赖：阿里云主机 120.26.66.182 上的 18081 服务进程；按激活码服务同体系推断可能涉及 MySQL/Redis 等（未实采）(待确认)。
- 被依赖/关联系统：
  - 生产激活码服务：`tools.marschat.online`（内网 Deb activecode 容器 18080）。
  - 同一 nginx 反代家族：`tools.marschat.online`（→ 100.105.196.63:18080）与 `tools-test.marschat.online`（→ aliyun:18081）并列。

## 运维要点

- 启停方式：后端位于阿里云主机，启停命令未实采 (待确认)。
- 日志查看：阿里云侧服务日志路径未实采 (待确认)；nginx 侧见腾讯云2号 `/var/log/nginx/`。
- 数据与备份：测试环境数据隔离策略（是否独立库）未实采 (待确认)；建议测试数据定期清理，避免长期堆积。
- 常见问题
  - **upstream 与材料包不一致**：实采 nginx 指向 `aliyun.marschat.online:18081`，材料包注记的 `mykng:8087` 已过时或曾误记；以本文实采为准。若后端曾迁移，请在基础设施记录中更新。
  - 从 mykng 侧探测 `120.26.66.182:18081` 返回 000（不可达），可能该端口仅对腾讯云2号侧放行或当前未运行——域名经腾讯云2号反代可达，但后端直连性待确认。
  - 若测试环境返回 502：先查阿里云 18081 进程是否存活、再查 nginx upstream 解析 `aliyun.marschat.online` 是否正确。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于 SSH 实采 nginx 配置 + 材料包生成；标注了 upstream 实采值与材料包注记的差异）
