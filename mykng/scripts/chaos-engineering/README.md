# mykng 故障注入与高可用验证（SOP附录F）

本目录提供一组故障注入脚本，用于验证 mykng 知识库微服务在各种异常场景下的高可用性、降级策略与恢复能力。

## 故障注入脚本

| 脚本                          | 故障场景                                | 影响                             |
| ----------------------------- | --------------------------------------- | -------------------------------- |
| `inject-mysql-down.sh`        | 模拟 MySQL 宕机（停止 kb-mysql 容器）   | 微服务数据库操作失败             |
| `inject-redis-down.sh`        | 模拟 Redis 宕机（停止 kb-redis 容器）   | 缓存失效、JWT 黑名单失效         |
| `inject-network-delay.sh`     | 模拟网络延迟（tc qdisc netem）          | 微服务间调用超时                 |
| `inject-oom.sh`               | 模拟 OOM（限制容器内存 + 触发压力）     | 服务被 OOM Killer 杀死           |
| `verify-ha.sh`                | 高可用综合验证（注入 + 恢复 + 校验）    | 一键式混沌演练                   |

## 使用流程

### 1. 单项故障注入

```bash
# 模拟 MySQL 宕机 30 秒后自动恢复
bash scripts/chaos-engineering/inject-mysql-down.sh --duration 30

# 模拟 Redis 宕机 60 秒
bash scripts/chaos-engineering/inject-redis-down.sh --duration 60

# 模拟 kb-auth 与 kb-knowledge 之间网络延迟 500ms
bash scripts/chaos-engineering/inject-network-delay.sh --target kb-auth --delay 500ms

# 模拟 kb-file 容器 OOM
bash scripts/chaos-engineering/inject-oom.sh --target kb-file
```

### 2. 综合高可用验证

```bash
# 执行完整混沌演练（依次注入各类故障，每次注入后自动恢复并健康检查）
bash scripts/chaos-engineering/verify-ha.sh
```

### 3. 手动恢复

```bash
# 恢复所有服务
bash scripts/chaos-engineering/verify-ha.sh --recover
```

## 安全提示

- ⚠ **仅在测试环境执行**，禁止在生产环境运行
- ⚠ 注入前请确保已通过 `backup.sh` 完成数据备份
- ⚠ 所有故障注入脚本默认带 `--duration` 自动恢复机制
- ⚠ `inject-network-delay.sh` 需要容器具有 NET_ADMIN capability

## 期望验证结果

| 故障场景        | 期望行为                                                  |
| --------------- | --------------------------------------------------------- |
| MySQL 宕机      | 微服务返回数据库错误，但不崩溃；MySQL 恢复后自动重连      |
| Redis 宕机      | 缓存降级到数据库直查，登录黑名单失效（安全风险已知）      |
| 网络延迟 500ms  | Feign 调用超时熔断，Gateway 返回 503                      |
| OOM             | 容器被杀后 Docker 自动重启（restart: unless-stopped）     |

## 报告输出

每次执行 `verify-ha.sh` 会生成报告：`/data/logs/chaos-reports/chaos-<ts>.log`
