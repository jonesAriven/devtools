# cosmic-studio 部署文档

> 适用版本 v0.2 · 部署目标 mykng（192.168.31.105）· 更新 2026-08-29

## 1. 架构

```
浏览器/手机 ──8310──▶ cosmic-web (nginx:alpine)
                        ├── /       前端静态（SPA 回退 index.html）
                        └── /api/ ──▶ cosmic-api (FastAPI/uvicorn)
                                        └──▶ platform-mysql 集群
                                             105:3306（GR 多主，成员含 182:3307/3308）
```

- **前后端分离容器**：`cosmic-web`（nginx 托管 dist + 反代）与 `cosmic-api`（纯后端）各自独立镜像，可独立升级
- **数据库**：MySQL GR 集群（三节点多主：105:3306 / 182:3307 / 182:3308），应用写 105 节点，GR 同步其余节点；cosmic 三库：`cosmic_active` / `cosmic_archive` / `cosmic_studio`
- 对外唯一端口：**8310**（web）；api 仅 compose 内网络

## 2. 环境依赖

| 依赖 | 说明 |
|---|---|
| mykng Docker | 已有（注意 daemon registry-mirrors=Nexus，docker.io 被 DNS 污染，新基础镜像用 `scripts/pull-via-lobster.sh` 经龙虾中转） |
| 基础镜像 | api=`python:3.12-slim`、web=`nginx:alpine`（均已在 mykng 本地） |
| Node 22（仅构建机） | 前端 dist 在**本机 Windows** 构建，服务器不需要 Node |
| MySQL 集群 | platform-mysql 三节点 ONLINE；专用账号 cosmic（init_db 自动创建） |
| 网络 | mykng → Nexus 8081（pypi）；应用 → 105:3306 |

## 3. 首次部署

```bash
# ── 构建机（Windows 本机 D:\huliang\java\ideaworkspace\cosmic-studio）──
cd frontend && npm install --registry http://192.168.31.105:8081/repository/npm-public/ && npm run build && cd ..
rm -rf app/static && cp -r frontend/dist app/static   # 本地开发模式静态文件（可选）

# ── 同步到 mykng ──
tar czf - --exclude=_ref --exclude=node_modules --exclude=__pycache__ \
    app scripts frontend/dist frontend/nginx.conf frontend/Dockerfile.web \
    requirements.txt Dockerfile docker-compose.yml docs README.md \
  | ssh root@192.168.31.105 "tar xzf - -C /root/devtools/cosmic-studio"

# ── mykng：建库建表（首次；需 root 密码）──
ssh root@192.168.31.105
cd /root/devtools/cosmic-studio
docker compose up -d --build
docker exec -e DB_ADMIN_PASSWORD=<root密码> cosmic-api python scripts/init_db.py

# ── 存量迁移（仅首次，Hermes SQLite → MySQL）──
docker exec cosmic-api python scripts/migrate_from_hermes.py --hermes-dir /root/hermes-workspace/cosmic/db

# 验证
curl -s http://127.0.0.1:8310/api/health     # {"status":"ok",...三个 db 全 true}
```

## 4. 日常发布（流水线，已接入 Woodpecker）

- **自动发布**：push 到 main → Woodpecker 自动执行 `scripts/ci/deploy.sh`（git pull → compose build → health check）
- **手动发布**：Woodpecker UI（woodci.marschat.online → jonesAriven/cosmic-studio → New Pipeline）或 CLI 触发
- 前端有改动时：构建机先 `npm run build` 后 commit（dist 已入库，流水线无需 Node）
- ⚠️ 必须 `--build`：`docker compose restart` 不会更新镜像内代码（踩过）

## 4b. 手动发布（绕过流水线的应急方式）

```bash
# 构建机：改代码 → 前端有改动则先 build → tar 同步（见 §3）→ 服务器：
cd /root/devtools/cosmic-studio && git pull && docker compose up -d --build
# 或直接执行流水线同款脚本：bash scripts/ci/deploy.sh
```

## 5. 回滚

- **代码回滚**：git 仓库切上一 tag/commit → 重新 tar 同步 → `up -d --build`
- **数据回滚（覆盖导入）**：每次覆盖导入自动备份在 `/data/backups/<库>_pre_overwrite_<ts>.json`，用「导入 JSON + 增量/覆盖」灌回，或人工核对
- **版本交付件**：`/data/versions/` 全量 xlsx+sha256，随时可人工比对

## 6. 数据库运维注意

- 集群为 **GR 多主**（三节点 ONLINE），应用固定写 105:3306（成员之一），勿直写 182 节点
- 集群恢复初期可能震荡（偶发 Lost connection），应用层有连接池自然重试；持续报错先查 `performance_schema.replication_group_members`
- init_db.py 幂等可重复执行（建库/建表/加列/种子均 IF NOT EXISTS / IGNORE）

## 7. 已知限制

- 版本文件（/data/versions）与备份（/data/backups）无自动清理，需人工定期处理
- LLM 配置未设置时，对话与「自动优化」功能返回 409 提示（属预期）
- 服务重启不丢数据（数据全在 MySQL 与 /data 卷）
