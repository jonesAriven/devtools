# Vaultwarden 密码管理

> 全基础设施账密「唯一真相源」：Bitwarden 兼容的自托管密码库，集中保管所有主机、容器、服务与第三方 API 的账号密码/密钥。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 基础设施 / 安全（密码管理） |
| 版本 | `vaultwarden/server:latest`（材料包实采） |
| 部署位置 | mykng 容器 `vaultwarden`（镜像 `vaultwarden/server:latest`），端口 8222 → 80，`restart` 策略见 compose |
| 源码位置 | 开源 `vaultwarden/server`（Rust 实现 Bitwarden 服务端）；本地无构建仓库，直接拉官方镜像 |
| CI/CD | 无（自部署容器） |

## 访问入口

- 公网：`https://vault.marschat.online`（腾讯云2号 nginx 终止 TLS 443）
- 内网：`http://192.168.31.105:8222`（mykng 宿主）
- Tailscale：`http://100.93.36.113:8222`

## 全链路

```
浏览器 → 腾讯云2号 nginx (:443, 域名 vault.marschat.online)
       → http://100.93.36.113:8222  (mykng vaultwarden 容器)
```
（mykng 本机 nginx 另有 `/vault/ → 127.0.0.1:8222` 的 path 反代。）

## 核心功能与使用

- 密码库（Bitwarden 兼容）：登录/密码、安全笔记、API Key、卡片等条目的加密存储。
- 多端客户端：浏览器扩展、桌面/移动 App、CLI 均可通过 `vault.marschat.online` 同步。
- 作为「真相源」的用途：
  - 全基础设施的账密（主机 SSH、数据库、各服务后台、云厂商、LLM API Key 等）统一存放于此。
  - 全组文档（含本次 portal 文档补全）约定：**任何文档不得出现明文密码/token/密钥**，一律写「账密见 Vaultwarden（vault.marschat.online）或 infrastructure-map 技能」。
- 典型场景：新成员/新服务开通时，从 Vaultwarden 取对应凭据；轮换密码后回写此处。

## 依赖与关联

- 依赖：SQLite 主库 `db.sqlite3`（默认）、可选 `attachments/` 附件卷、可选 SMTP（邮件/邀请）。
- 被依赖/关联系统：**几乎所有**其他系统的凭证都存放在此（mykng/Deb/腾讯云/阿里云主机、MySQL/Redis/Mongo/MinIO/Nacos/Woodpecker、FRP dashboard、各类 API Key 等）。它与 `infrastructure-map` 技能互为「运行时凭证」与「架构事实」的两大知识源。

## 运维要点

- 启停方式：`docker`（容器 `vaultwarden`，端口 8222）；具体 compose/启停命令未实采 (待确认)。
- 日志查看：`docker logs vaultwarden`；Grafana/Loki（Deb 侧）可纳入日志归集。
- 数据与备份
  - 备份仓库：`D:\huliang\java\ideaworkspace\vaultwarden-backup\`。
  - 备份内容（实采 2026-09-05）：每日 **02:00** 左右生成一组：
    - `db_<YYYYMMDD>_*.sqlite3`（约 278KB，主数据库）
    - `config_<YYYYMMDD>_*.json`（约 1.9KB，服务配置）
    - `attachments_<YYYYMMDD>_*.tar.gz`（附件归档，当前约 45B，近乎空）
  - 连续性：已连续存在 **2026-08-29 至 2026-09-05**（每日一份，9/5 当天也在），说明存在定时备份任务且稳定运行。
  - 备份任务位置未实采（可能在 mykng cron 或独立脚本拉取/导出）(待确认)；建议确认备份是否异地/长寿保留。
- 安全要点
  - 这是最高敏感系统：**禁止任何明文凭据外泄**（含提交到仓库、写入文档）。
  - 建议开启 admin 令牌、失败锁定、定期改密；备份文件同样含敏感数据，需受控访问。
- 常见问题
  - 容器非默认端口 80 而是宿主机 8222，nginx 反代注意 `/vault/` 路径与根域名两种入口的转发一致性。
  - 备份 `attachments` 体积长期为 45B：可能未实际使用附件功能，属正常。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于 mykng 容器实采 + vaultwarden-backup 仓库实采 + 材料包生成；明确全基础设施账密真相源定位与每日备份现状）
