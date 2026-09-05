# 激活码服务

> 自研的软件授权（激活码）管理服务：管理员（或扫码自助）生成带 RSA 签名的激活码，客户端软件内嵌验证库离线校验激活码有效性/过期/设备绑定，并记录激活流水。分为「生成/管理后台」与「客户端验证库」两部分，本篇为服务端（生成与管理）。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | Web 系统（自研） |
| 版本 | Spring Boot（Java）+ MyBatis-Plus；镜像 `activecode:latest`（实采 `docker inspect`） |
| 部署位置 | 生产：内网 Debian（192.168.31.182）容器 `activecode`，`0.0.0.0:18080->8080`；挂载 `/mnt/shared/www/download/QRCodeTools → /app/downloads` |
| 源码位置 | 本地 `D:\huliang\java\ideaworkspace\devtools\active-manager\activation-code-server\`（仓库 `active-manager`，与验证库、设计文档同仓） |
| CI/CD | Woodpecker 流水线项目 `active-manager`（woodScript `deploy-active-manager.sh` 部署到内网 Debian activecode compose，健康检查 `localhost:18080/activecode/login.html`） |
| 编排 | compose project：`activecode`（材料包镜像 `material/composes/activecode.yml`） |

## 访问入口

- 公网（生产）：`https://tools.marschat.online/activecode/login.html`（腾讯云2号 nginx `tools.marschat.online` → `http://100.105.196.63:18080`）
- 内网：`http://192.168.31.182:18080/activecode/login.html`（内网 Debian 直连）
- Tailscale：`http://100.105.196.63:18080/activecode/login.html`
- 页面：登录页 `/activecode/login.html`；管理后台 `/activecode/main.html`（登录后）；自助激活页 `/activecode/index.html?sn=<序列号>`（扫码进入，自动填充序列号）；下载页 `/activecode/downloads.html`

## 全链路（生产）

```
管理员/终端用户浏览器
  → https://tools.marschat.online/activecode/... (腾讯云2号 nginx :443)
  → http://100.105.196.63:18080 (内网 Debian activecode 容器)
       /activecode/*.html       静态页由 PageController 从 classpath 读取返回
       /activecode/api/*        Spring Boot 接口处理
  → MySQL（GR 集群 tools 库，多主机 failover URL）
```

## 系统设计

### 总体架构

active-manager 仓库按「三大工程」分层（docs/v2/design.md）：

```
工具软件层（QRCodeTool 等，可多个语言实现）
   ↓ 加密序列号（上行） / 激活码（下行）
activation-code-verifier（客户端验证工具层，见 activation-verifier.md）
   ↓ POST /activation/generate、/verify
activation-code-server（本服务，服务端层）
   ↓
MySQL（activation_record / activation_log / admin_user / sys_config）
```

服务端内部：Controller（4 个）→ Service（ActivationService）→ Mapper（MyBatis-Plus）；CryptoUtil 承担加解密，RsaKeyConfig 加载 `rsa_keys/private_key.pem` 与 `public_key.pem`（RSA 2048）；AuthInterceptor 做 /api/* 会话拦截。

### 核心数据模型

| 表 | 用途 |
|----|------|
| `admin_user` | 管理员账号（SHA-256 + 随机盐哈希口令；首次启动为空表时自动建内置默认管理员） |
| `activation_record` | 激活码生成记录（序列号、激活码、设备别名、状态） |
| `activation_log` | 激活流水（recordId/serialNumber/eventType/deviceId/时间） |
| `sys_config` | 系统配置（默认有效期、版本校验策略等键值对） |

### 关键设计决策

1. **RSA 签名激活码**：`激活码 = Base64URL(payload) + "." + Base64URL(signature)`，payload = 序列号|设备ID|过期时间戳(毫秒)，SHA256withRSA 私钥签名。无私钥不可伪造、不可篡改，设备 ID 嵌入 payload 实现绑定。
2. **唯一序列号 4 段加密格式**：明文 = 初始序列号|设备ID|机器码|应用版本，逐字节 XOR 0x5A → Base64。第 4 段 appVersion 由客户端经 `LaunchWithProtection` 传入，服务端生成激活码前按 `version-check`（mode: none/required/minimum）校验版本，控制哪些客户端版本可领码。
3. **离线优先 + 自助扫码双路径**：客户端验签只依赖内置公钥，断网可激活；同时提供扫码自助路径——序列号弹窗二维码指向 `tools.marschat.online/activecode/index.html?sn=xxx`，扫码自动填充序列号即可生成激活码，免去人工传话。
4. **版本号单点维护**：工具项目 CMakeLists.txt → version.h → LaunchWithProtection 参数 → verifier 静态字段，verifier 库自身不持有版本定义，改版本只动一处。

### 对外接口概览

服务端 4 个 Controller（前缀 `/activecode/api`）：

- AuthController `/auth`：login / logout / session / change-password（HttpSession 会话，SHA-256 + 随机盐哈希口令；首次启动 `admin_user` 空表时自动建内置默认管理员）
- ActivationController `/activation`（核心，明细）：
  - `POST /generate`：解密客户端序列号（XOR 0x5A + Base64 → 4 段明文）→ 校验第 4 段 appVersion（version-check 策略）→ 以「序列号 + 过期时间戳」RSA 私钥签名生成激活码 → 记录落库
  - `POST /verify`：激活码在线校验（验证库离线验签的在线版，回写激活流水）
  - `GET /list`：激活码记录查询（keyword/status 分页）
  - `GET /logs`：激活流水（按 recordId/serialNumber/eventType/deviceId/时间区间）
  - `GET /parse-code`、`/parse-serial`：解析激活码/序列号明文（排查"过期/设备不匹配"专用）
  - `DELETE /{id}`、`DELETE /batch`（≤100 条）：单条/批量删除记录
  - `PUT /{id}/alias`：修改设备别名（管理识别用）
  - `GET/PUT /config/default-expire`：默认有效期（默认 43200 分钟 = 30 天）
  - `GET/PUT /config/version-check`：版本校验配置（enabled + mode: none/required/minimum）
- DownloadController `/download`：`GET /list`（扫描挂载目录 .exe 按修改时间倒序，含大小）、`GET /{filename}.exe`（带路径遍历防护）
- PageController：login/main/index/downloads 四类静态页（classpath 读取）；未登录访问 main.html 自动 302 跳登录页

## 部署与发布

### 编排与位置

- compose project：`activecode`（内网 Debian 192.168.31.182）；compose 原文（材料包 `material/composes/activecode.yml`）：

```yaml
services:
  activation-code-server:
    build: .
    image: activecode:latest
    container_name: activecode
    restart: on-failure:5
    ports:
      - "18080:8080"
    extra_hosts:
      - "host.docker.internal:host-gateway"
    environment:
      - TZ=Asia/Shanghai
      - SPRING_PROFILES_ACTIVE=kafka-log
      - SPRING_DATASOURCE_URL=jdbc:mysql://192.168.31.105:3306,192.168.31.182:3307,192.168.31.182:3308/tools?...（GR 多主机 failover URL，完整串见 compose）
      - SPRING_DATASOURCE_USERNAME=（账密见 Vaultwarden / infrastructure-map）
      - SPRING_DATASOURCE_PASSWORD=（账密见 Vaultwarden / infrastructure-map）
    volumes:
      - "/mnt/shared/www/download/QRCodeTools:/app/downloads:ro"
    networks:
      - activecode-net
```

### 配置清单

| 项 | 值 |
|----|----|
| 端口映射 | 宿主机 18080 → 容器 8080 |
| 卷挂载 | `/mnt/shared/www/download/QRCodeTools → /app/downloads`（只读，QRCodeTools exe 分发目录；Windows 本地开发对应 `D:\huliang\java\ideaworkspace\www\download\QRCodeTools`） |
| 数据库 | MySQL GR 集群 `tools` 库（105:3306 / 182:3307 / 182:3308 failover URL，`loadBalanceStrategy=random` + `failOverReadOnly=false`） |
| RSA 密钥 | `rsa_keys/private_key.pem`、`public_key.pem`（classpath；私钥严禁外泄/入库） |
| 版本校验 | `version-check.enabled` / `mode`（none/required/minimum） |

### 发布/升级

走 Woodpecker 流水线（推荐）：

```bash
python woodScript/trigger-pipeline.py active-manager --wait
```

链路：CI 构建 → drone-ssh 到内网 Debian → 执行 `deploy-active-manager.sh` 重建 activecode 容器 → 健康检查 `localhost:18080/activecode/login.html` 返回 200。

手动部署（docs/v2/deploy.md，流水线不可用时兜底）：

```bash
# 1. 本地打包
cd D:\huliang\java\ideaworkspace\devtools\active-manager\activation-code-server
mvn clean package -DskipTests
# 产物：target/activation-code-server-1.0.0.jar

# 2. 上传 JAR + Dockerfile 到内网 Debian（凭据见 Vaultwarden / infrastructure-map）
scp target/activation-code-server-1.0.0.jar <user>@192.168.31.182:~/
scp Dockerfile <user>@192.168.31.182:~/

# 3. 远程构建镜像并经 compose 重建
ssh <user>@192.168.31.182
cd ~ && docker build -t activecode .
docker compose -p activecode up -d --force-recreate
```

### 回滚

- 镜像回退：保留旧 JAR 重新 `docker build -t activecode:<旧tag> .`，compose 中 image 指向旧 tag 后 `docker compose -p activecode up -d --force-recreate`
- 数据在 MySQL GR（多副本），应用本身无状态，重建容器即完成回滚
- RSA 密钥对不可随意回滚：私钥换回旧版会使新签激活码在已更新公钥的客户端上验签失败

## 核心功能与使用

### 功能清单

- **激活码生成**：管理员后台或用户扫码自助，凭客户端上报的加密序列号生成 RSA 签名激活码，全量落库。
- **激活流水与审计**：每次校验/激活事件（eventType/deviceId/时间）记录，可按序列号、事件类型、时间区间检索。
- **策略配置**：默认有效期（默认 43200 分钟 = 30 天）、版本校验策略（控制哪些客户端版本可领码），后台在线改。
- **客户端分发**：扫描挂载目录列出 QRCodeTools 等 exe 版本并提供下载。
- **调试工具**：parse-code / parse-serial 解析激活码与序列号明文，排查"过期/设备不匹配"类问题。

### 典型操作路径

- **管理员发码**：登录 `/activecode/login.html` → 进入 `main.html` 后台 → 粘贴用户提供的序列号（或生成记录里选）→ 生成激活码 → 回传给用户。
- **用户扫码自助**：客户端弹窗显示序列号 + 二维码 → 手机扫码打开 `index.html?sn=xxx`（自动填充）→ 点"生成激活码" → 复制回客户端粘贴激活。
- **流水排查**：后台按 keyword/status 查激活记录；或调 `GET /activecode/api/activation/logs` 按设备/时间过滤；先用 `/parse-code` 判断过期还是设备不匹配。
- **客户端分发**：把新版 exe 放到 `/mnt/shared/www/download/QRCodeTools` → 下载页 `/activecode/downloads.html` 自动按修改时间倒序列出。

## 依赖与关联

- 数据库：MySQL GR 集群 `tools` 库（连接信息见 Vaultwarden / infrastructure-map 技能）。
- 关联系统：
  - **激活码使用页面 / 验证库（activation-verifier）**：客户端内嵌的验证 SDK，离线用 RSA 公钥验签，与服务端 `/generate` 生成的激活码一一对应。本服务是「发证机关」，对端是「验票终端」。
  - 客户端软件（如二维码工具 QRCodeTool）：`LaunchWithProtection` 启动即进入验证流程，相关 exe 由本服务 `/download` 分发。

## 运维要点

- 启停：内网 Debian 上 `docker compose -p activecode up -d / down / logs -f`（compose project 名 activecode）。
- 健康检查：`curl -s -o /dev/null -w '%{http_code}' http://192.168.31.182:18080/activecode/login.html` → `200`（2026-09-05 实采确认）。
- 日志：`docker logs activecode`；内网 Debian 侧 `obs-dozzle`（:15888）实时日志，Grafana/Loki 聚合。
- 数据与备份：激活码记录与流水在 MySQL GR（多副本）；默认管理员账号在 `admin_user` 表，改密后请同步更新 Vaultwarden。
- 数据库连接：`application-prod.yml` 使用 GR 集群多主机 failover URL（105:3306 / 182:3307 / 182:3308，`loadBalanceStrategy=random` + `failOverReadOnly=false` + `retriesAllDown=3`），单节点故障自动切换，无需改配置。
- 常见问题：
  - 默认密码风险：首次部署的默认管理员账号必须改密（账密见 Vaultwarden 或 infrastructure-map 技能），否则任何人可进入后台生成激活码。
  - 客户端激活失败：先 `/parse-code` 看激活码是否过期或设备不匹配；再核对 `/version-check` 配置是否拦截了当前客户端版本（第 4 段 appVersion 校验）。
  - 生成激活码报序列号无效：核对客户端序列号是否完整复制（4 段密文，XOR 0x5A + Base64 编码，服务端按 `|` 切分 4 段）。
  - 下载列表为空：挂载目录 `/mnt/shared/www/download/QRCodeTools` 不存在或为空时返回空列表，检查挂载。

## 变更记录

- 2026-09-05 v2 补全设计/部署/使用三维度（新增系统设计：三大工程架构/4 表模型/RSA 签名与 4 段序列号机制/扫码自助链路；新增部署与发布：compose 原文/配置清单/流水线与回滚；使用节补扫码自助与流水排查路径；凭证改为 Vaultwarden 引用）
- 2026-09-05 v1 首次生成（portal 文档补全任务，AI 基于实采 docker ps/inspect + 本地 `activation-code-server` 源码 Controller + 腾讯云2号 nginx 配置生成）
