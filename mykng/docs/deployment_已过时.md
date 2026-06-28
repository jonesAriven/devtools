# 部署文档

| 属性 | 值 |
|------|-----|
| 版本 | v1.4.0 |
| 更新日期 | 2026-06-28 |
| 适用环境 | 生产 (mykng-debain) |

## 1. 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| OS | Debian 13.5 | VirtualBox VM |
| Docker | 24+ | 容器运行时 |
| Docker Compose | v2+ | 编排工具 |
| 内存 | ≥4GB | 服务器内存 |
| 磁盘 | ≥40GB | 数据+日志+镜像 |

## 2. 部署架构

- **服务器**：mykng-debain（VirtualBox Debian VM）
  - Tailscale IP：100.93.36.113
  - LAN IP：192.168.31.105
  - SSH：root/root
- **代码同步**：SMB 共享 `/mnt/shared/devtools/mykng/`
- **Docker网络**：kb-deploy_kb-net（bridge, 172.20.0.0/16）
- **端口映射**：仅 gateway 映射宿主机端口（8090→8080），其余内部通信

## 3. 首次部署

### 3.1 准备环境变量
```bash
cd /mnt/shared/devtools/mykng
cp .env.example .env
# 编辑 .env，修改密码/密钥
vi .env
```

### 3.2 构建并启动
```bash
# 构建所有镜像
bash scripts/build.sh

# 启动基础设施（等待健康检查通过）
docker compose -p kb-deploy up -d mysql redis mongodb minio meilisearch

# 等待基础设施就绪
sleep 30

# 启动所有微服务
docker compose -p kb-deploy up -d kb-auth kb-file kb-knowledge kb-ops kb-intelligence kb-gateway
```

### 3.3 验证部署
```bash
# 健康检查
bash scripts/health-check.sh

# 访问网关
curl http://192.168.31.105:8090/kb/api/auth/login -X POST \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

## 4. 单服务部署

### 4.1 构建并部署单个服务
```bash
# 例如部署 kb-intelligence
docker compose -p kb-deploy build kb-intelligence
docker compose -p kb-deploy up -d --no-deps kb-intelligence
```

### 4.2 重建gateway（路由变更后）
```bash
docker compose -p kb-deploy build kb-gateway
docker compose -p kb-deploy up -d --no-deps kb-gateway
```

## 5. 回滚

### 5.1 回滚单个服务
```bash
bash scripts/rollback.sh kb-intelligence
```

### 5.2 回滚所有服务
```bash
bash scripts/rollback.sh all
```

### 5.3 数据库回滚
```bash
# 备份当前数据库
bash scripts/backup.sh

# 执行回滚SQL
docker exec -i kb-mysql mysql -uroot -p<password> kb_intelligence < sql/V2_rollback.sql
```

## 6. 发布流程

### 6.1 发布前检查
- [ ] 数据库备份已完成：`bash scripts/backup.sh`
- [ ] 镜像构建成功：`bash scripts/build.sh`
- [ ] 健康检查通过：`bash scripts/health-check.sh`
- [ ] 回滚方案已验证

### 6.2 发布执行
```bash
# 1. 备份数据库
bash scripts/backup.sh

# 2. 构建新镜像
bash scripts/build.sh

# 3. 逐个重启服务（避免全部中断）
docker compose -p kb-deploy up -d --no-deps kb-auth
sleep 10
docker compose -p kb-deploy up -d --no-deps kb-file
sleep 10
docker compose -p kb-deploy up -d --no-deps kb-knowledge
sleep 10
docker compose -p kb-deploy up -d --no-deps kb-ops
sleep 10
docker compose -p kb-deploy up -d --no-deps kb-intelligence
sleep 10
docker compose -p kb-deploy up -d --no-deps kb-gateway

# 4. 健康检查
bash scripts/health-check.sh
```

### 6.3 发布后观测
- 0-30分钟：密集观测日志和健康状态
- 30分钟-2小时：在岗值守，关注告警
- 2-24小时：定期巡检

## 7. 日志查看

```bash
# 查看所有服务日志
docker compose -p kb-deploy logs --tail=100 -f

# 查看单个服务日志
docker logs kb-intelligence -f --tail=100

# 日志文件位置（容器内）
# /data/logs/{service-name}.log
```

## 8. 常见问题

### Q: 容器名冲突
```bash
# 停止并删除旧容器
docker stop kb-intelligence && docker rm kb-intelligence
# 重新启动
docker compose -p kb-deploy up -d --no-deps kb-intelligence
```

### Q: 网络不通
```bash
# 检查网络
docker network inspect kb-deploy_kb-net
# 确保所有容器在同一网络
docker inspect kb-intelligence --format '{{json .NetworkSettings.Networks}}'
```

### Q: 数据库不存在
```bash
# 手动创建数据库
docker exec kb-mysql mysql -uroot -p<password> \
  -e "CREATE DATABASE kb_intelligence CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```
