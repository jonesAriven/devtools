# 令狐 TokenHub 控制台

> 自托管令牌/授权管理类控制台（Next.js 前端 + 自研后端），为内部「令狐」相关令牌发放与管控提供 Web 界面。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 自研 Web 系统 / 工具 |
| 版本 | 发布版本 0.7.0（进程 cwd 路径 `releases/0.7.0/frontend`）；前端 Next.js `next-server v16.2.9` |
| 部署位置 | mykng 宿主进程（**非 Docker**），运行用户 `tokenhub`；启动脚本 `/opt/tokenhub/current/bin/tokenhub-run`；监听端口 13000 |
| 源码位置 | 服务器 `/opt/tokenhub/`（release 目录 `releases/0.7.0`）；本地 devtools 工作区未见对应仓库 (待确认是否纳入版本库) |
| CI/CD | 无（自部署，由 `tokenhub-run` 脚本拉起） |

## 访问入口

- 公网：`https://tokenhub.marschat.online`（腾讯云2号 nginx 终止 TLS 443）
- 内网：`http://192.168.31.105:13000`（mykng 宿主）
- Tailscale：`http://100.93.36.113:13000`

## 全链路

```
浏览器 → 腾讯云2号 nginx (:443, 域名 tokenhub.marschat.online)
       → http://100.93.36.113:13000  (mykng 宿主 tokenhub 进程)
```

## 实采进程结构（mykng，2026-09-05）

`ss -tlnp | grep 13000` 与 `ps -ef` 实采：

- `bash /opt/tokenhub/current/bin/tokenhub-run`（PID 3521202，8月27日启动，父进程 1 → 疑似开机自启）
- `/opt/tokenhub/current/bin/tokenhub`（PID 3522096，后端主进程，累计 CPU ~5.5h）
- `next-server (v16.2.9)`（PID 3522097，前端，cwd = `/opt/tokenhub/releases/0.7.0/frontend`）
- 监听：`0.0.0.0:13000`（`next-server` 占用，队列 511）

由此可判定：

- 采用「前端（Next.js）+ 后端（自研二进制 `tokenhub`）」前后端分离架构，二者经同一 13000 端口对外（Next.js 很可能代理/复用后端 API）。
- 以 `releases/<ver>/` 目录 + `current` 软链管理版本（结构推断，待确认），当前 `current → 0.7.0`。

## 核心功能与使用

- 令牌/授权控制台：面向内部「令狐」体系的令牌发放、查看与管控（具体业务字段与操作流未经源码/文档实测，按架构推断描述）。
- 架构：后端自研二进制 `tokenhub` 提供 API，Next.js 前端（`releases/0.7.0/frontend`）提供管理界面，统一经 13000 端口对外。
- 典型场景：需要集中管理某类访问令牌/授权凭证时通过此控制台操作。

> 具体功能菜单、令牌类型、权限模型未实采（无源码/在线文档），避免编造按钮级步骤；如需精确功能清单，建议登录后补录或接入版本库。

## 依赖与关联

- 依赖：mykng 宿主运行环境（Node/Next 运行时，由 `tokenhub-run` 托管）；可能的后端存储（数据库/缓存）未实采 (待确认)。
- 被依赖/关联系统：使用「令狐」令牌的下游服务（具体消费方未实采）(待确认)；与腾讯云2号 nginx 反代关联（域名 → 13000）。

## 运维要点

- 启停方式
  - 启动：`/opt/tokenhub/current/bin/tokenhub-run`（由 init/systemd 或手动拉起，父进程为 1，疑似开机自启）。
  - 停止/重启：结束 `tokenhub`（PID 3522096）与 `next-server`（PID 3522097）后重新执行 `tokenhub-run`（具体 systemd 单元未实采）(待确认)。
- 日志查看：进程标准输出经 `tokenhub-run` 托管，日志落盘位置未实采 (待确认)；可用 `obs-dozzle` 或在 mykng 查进程输出。
- 数据与备份：令牌/授权数据后端存储位置未实采 (待确认)；建议确认是否纳入备份体系（令牌属高敏感数据）。
- 版本与升级（结构推断）
  - 新版本以 `releases/<ver>/` 形式放置于 `/opt/tokenhub/`，由 `current` 软链指向当前版本；升级即切换软链后重启 `tokenhub-run`（具体回滚步骤待确认）。
- 常见问题
  - 该服务为宿主进程而非容器，故 **不在 `docker ps` 列表内**，排障时用 `ss -tlnp | grep 13000` 与 `ps -ef | grep tokenhub` 定位。
  - 13000 端口被 `next-server` 占用而非后端二进制，是因为前端进程对外承接 HTTP；后端二进制可能监听同端口或仅进程内通信（细节待确认）。
  - 若需重启且不敢误杀：先 `ps -ef | grep tokenhub` 确认三个进程 PID，再按「后端→前端」顺序停止、用 `tokenhub-run` 拉起。

## 安全要点

- 令牌管理类系统属高敏感面，访问口令/API 密钥应入 Vaultwarden，勿明文。
- 公网经腾讯云2号反代，建议确认是否启用了访问控制（登录态/来源限制）。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于 mykng SSH 实采进程结构 + 材料包生成）
