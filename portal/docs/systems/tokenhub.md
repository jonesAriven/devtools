# 令狐 TokenHub 控制台

> 自托管 AI 网关 / 令牌管理控制台（开源 TokenHub：自研后端二进制 + Next.js 前端），
> 为内部「令狐」相关令牌发放与管控提供 Web 界面，
> 部署于 mykng 宿主 systemd 服务。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 自研 Web 系统 / 工具（AI 网关 / 令牌管理） |
| 版本 | 发布版本 0.7.0（`releases/0.7.0`）；前端 Next.js `next-server v16.2.9` |
| 部署位置 | mykng（192.168.31.105）宿主 **systemd 服务** `tokenhub.service`（非 Docker） |
| 部署位置 | 运行用户/组 `tokenhub`，监听端口 13000 |
| 源码位置 | 服务器 `/opt/tokenhub/`（release 目录 `releases/0.7.0`，`current` 软链指向） |
| 源码位置 | 上游为开源项目（systemd 单元 Documentation 标注 github.com/astaxie/TokenHub，本环境无法访问 GitHub，细节待确认） |
| 源码位置 | 本地 devtools 工作区未见对应仓库 (待确认是否纳入版本库) |
| CI/CD | 无流水线（systemd 托管 + releases 目录发布） |

## 访问入口

- 公网：`https://tokenhub.marschat.online`（腾讯云2号 nginx 终止 TLS 443）
- 内网：`http://192.168.31.105:13000`（mykng 宿主）
- Tailscale：`http://100.93.36.113:13000`

## 全链路

```
浏览器 → 腾讯云2号 nginx (:443, 域名 tokenhub.marschat.online)
       → http://100.93.36.113:13000  (mykng 宿主 tokenhub 进程)
```

## 系统设计

### 组件架构（开源 TokenHub）

- TokenHub 为开源 AI 网关类项目（systemd 单元 Description：**TokenHub AI Gateway**）。
- 采用「后端自研二进制 `tokenhub` + 前端 Next.js」前后端分离架构。
- 二者经同一 13000 端口对外（Next.js 承接 HTTP 并代理/复用后端 API）。

实采进程结构（mykng，2026-09-05）：

- `bash /opt/tokenhub/current/bin/tokenhub-run`（父进程 1，systemd 拉起）
- `/opt/tokenhub/current/bin/tokenhub`（后端主进程）
- `next-server (v16.2.9)`（前端，cwd = `/opt/tokenhub/releases/0.7.0/frontend`）
- 监听：`0.0.0.0:13000`（next-server 占用，队列 511）

### 我们的集成设计

- **实例角色**
  - mykng 上以 systemd 常驻服务运行。
  - 作为内部「令狐」体系令牌/授权的集中管理面；公网经腾讯云2号 nginx 反代。
- **与哪些系统连接**
  - 上游：腾讯云2号 nginx。
  - 下游：使用「令狐」令牌的服务（具体消费方未实采）(待确认)。
  - 后端存储位置未实采；systemd 单元 `ReadWritePaths` 含 `/var/lib/tokenhub`，推断数据目录在 `/var/lib/tokenhub` (待确认)。
- **为什么选它**
  - 开源成品 + 独立二进制发布，无需容器编排即可宿主部署。
  - 运维面收敛为一个 systemd 单元。
- **关键配置思路**
  - 配置经 `EnvironmentFile=/etc/tokenhub/tokenhub.env` 注入（变量含凭证，见 Vaultwarden，本文不落盘）。
  - systemd 沙箱加固：`NoNewPrivileges` / `PrivateDevices` / `PrivateTmp` / `ProtectHome` / `ProtectSystem=strict`。
  - 仅 `/opt/tokenhub` 与 `/var/lib/tokenhub` 可写（ReadWritePaths）。
  - `Restart=always` + `RestartSec=5` 崩溃自动拉起；`LimitNOFILE=65535`。

### systemd 单元关键字段（实采 /etc/systemd/system/tokenhub.service）

| 字段 | 值 | 说明 |
|------|----|------|
| Description | TokenHub AI Gateway | 项目定位 |
| User/Group | tokenhub | 专用低权限用户 |
| EnvironmentFile | /etc/tokenhub/tokenhub.env | 配置与凭证注入 |
| WorkingDirectory | /opt/tokenhub/current | 跟随软链 |
| ExecStart | /opt/tokenhub/current/bin/tokenhub-run | 启动脚本 |
| Restart / RestartSec | always / 5 | 崩溃自愈 |
| KillMode | control-group | 停止时整组杀掉 |
| UMask | 0077 | 文件权限收敛 |
| ProtectSystem | strict | 文件系统只读加固 |
| ReadWritePaths | /opt/tokenhub /var/lib/tokenhub | 仅这两个目录可写 |

### 对外接口概览

- Web 管理界面（Next.js SSR）+ 后端 API（统一 13000 端口）。
- 具体路由分组未实采（无源码/在线文档），避免编造 (待确认)。

## 部署与发布

- 编排与位置
  - **非 Docker，systemd 单元**：`/etc/systemd/system/tokenhub.service`。
  - 程序目录：`/opt/tokenhub/`，结构为 `releases/<ver>/` + `current` 软链。
  - 当前 `current → /opt/tokenhub/releases/0.7.0`（2026-08-27 上线）。
  - 目录由 tokenhub 用户管理（`.tokenhub-managed-directory` 标记）。
- 配置清单
  - 端口：13000（`0.0.0.0` 监听，next-server 进程持有）。
  - 卷/目录：`/opt/tokenhub`（程序，只读为主）、`/var/lib/tokenhub`（可写数据，推断）(待确认)。
  - 环境变量：`/etc/tokenhub/tokenhub.env`（变量名/值不落盘，属凭证类）。
  - 运行身份：`User=tokenhub` / `Group=tokenhub`，UMask 0077。
- 发布/升级（releases + 软链模式，实际操作步骤）
  1. 新版本放置到 `/opt/tokenhub/releases/<new_ver>/`（含 `bin/` 与 `frontend/`）。
  2. `ln -sfn /opt/tokenhub/releases/<new_ver> /opt/tokenhub/current` 切换软链。
  3. `systemctl restart tokenhub`（KillMode=control-group 会整组停止旧进程）。
  4. 验证：`ss -tlnp | grep 13000` 与公网域名探活。
- 回滚
  - 把 `current` 软链切回旧版本目录 → `systemctl restart tokenhub`。
  - `releases/` 下旧版本保留即可即时回退。

## 核心功能与使用

### 功能清单

- 令牌/授权控制台：面向内部「令狐」体系的令牌发放、查看与管控。
  - 具体业务字段与操作流未经源码/文档实测，按架构与定位描述。
- AI 网关能力：作为 TokenHub（AI Gateway），推断承担 AI 调用的令牌签发/配额/代理职能 (待确认)。
- 典型场景：需要集中管理某类访问令牌/授权凭证时通过此控制台操作。

### 典型操作路径

1. **访问控制台**
   - 浏览器打开 `https://tokenhub.marschat.online`（或内网 `http://192.168.31.105:13000`）。
   - 登录（账密见 Vaultwarden）。
2. **服务异常排查**
   - `systemctl status tokenhub` 看运行态。
   - `journalctl -u tokenhub -n 100` 看日志。
   - `ss -tlnp | grep 13000` 确认监听。

> 具体功能菜单、令牌类型、权限模型未实采（GitHub 不可达、无本地源码），
> 避免编造按钮级步骤；如需精确功能清单，建议登录后补录或接入版本库。

## 依赖与关联

- 依赖
  - mykng 宿主运行环境（Node/Next 运行时，由 `tokenhub-run` 托管）。
  - `/etc/tokenhub/tokenhub.env` 配置。
  - 后端存储（推断 `/var/lib/tokenhub`，未实采）(待确认)。
- 被依赖/关联系统
  - 使用「令狐」令牌的下游服务（具体消费方未实采）(待确认)。
  - 与腾讯云2号 nginx 反代关联（域名 → 13000）。

## 运维要点

- 启停方式
  - `systemctl start|stop|restart|status tokenhub`（标准 systemd 管理，勿手工 kill 进程组）。
  - 修改配置后：编辑 `/etc/tokenhub/tokenhub.env` → `systemctl restart tokenhub`。
- 日志查看
  - `journalctl -u tokenhub -f`（Type=simple，标准输出进 journal）。
- 数据与备份
  - 确认 `/var/lib/tokenhub` 数据内容与备份策略 (待确认)。
  - 令牌属高敏感数据，务必纳入备份与受控访问。
- 版本与升级：见「部署与发布」节 releases + 软链流程。
- 常见问题
  - 该服务是宿主进程而非容器，**不在 `docker ps` 列表内**。
    - 排障用 `systemctl` / `journalctl` / `ss -tlnp | grep 13000`。
  - 13000 端口由 next-server 持有：前端进程对外承接 HTTP，后端二进制经其代理/复用（细节待确认）。
  - systemd 单元带 `ProtectSystem=strict`：
    - 服务进程无法写 `/opt/tokenhub`、`/var/lib/tokenhub` 之外路径。
    - 若升级后报权限错误，先检查 ReadWritePaths。

## 安全要点

- 令牌管理类系统属高敏感面：
  - `tokenhub.env` 与 `/var/lib/tokenhub` 权限 0077 UMask 已收敛。
  - 访问口令/API 密钥入 Vaultwarden，勿明文。
- 公网经腾讯云2号反代，建议确认是否启用访问控制（登录态/来源限制）(待确认)。
- systemd 沙箱（NoNewPrivileges/ProtectSystem）为纵深防御，修改单元时保留。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度
  - 实采 systemd 单元全文（TokenHub AI Gateway 定位、EnvironmentFile、沙箱加固）。
  - 明确 releases+软链发布/回滚流程与 /var/lib/tokenhub 数据目录推断。
- 2026-09-05 v1 首次生成（基于进程结构实采）
