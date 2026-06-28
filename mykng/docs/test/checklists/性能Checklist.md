# 性能 Checklist（Performance Checklist）

> **文档版本**：v1.1
> **更新日期**：2026-06-28
> **适用范围**：MyKNG 知识库平台 7 模块（kb-gateway 8090 / kb-auth 8081 / kb-file 8082 / kb-knowledge 8083 / kb-ops 8084 / kb-intelligence 8086 / kb-common）+ 前端 kb-web + 双层 Nginx + 数据库/中间件
> **对应 SOP**：附录 E — 性能 Checklist
> **使用说明**：每次发布前由性能负责人逐项确认；关键指标不达标需评估是否延期。
> **检查频率**：每次发布前 + 每月性能巡检 + 大版本上线前压测
> **责任角色**：性能负责人 / 开发负责人 / DBA / 运维负责人

---

## 目录

- [一、性能基线指标](#一性能基线指标)
- [二、接口性能](#二接口性能)
- [三、数据库性能](#三数据库性能)
- [四、缓存性能](#四缓存性能)
- [五、JVM 性能](#五jvm-性能)
- [六、并发与线程](#六并发与线程)
- [七、前端性能](#七前端性能)
- [八、Nginx 性能](#八nginx-性能)
- [九、大文件与批处理](#九大文件与批处理)
- [十、压力测试](#十压力测试)
- [十一、容量规划](#十一容量规划)
- [附录 A：性能等级定义](#附录-a性能等级定义)
- [附录 B：性能测试工具速查](#附录-b性能测试工具速查)
- [附录 C：常见性能反模式](#附录-c常见性能反模式)

---

## 一、性能基线指标

### 1.1 核心 SLA 指标

| 编号 | 指标 | 目标值 | 警戒值 | 等级 | 状态 |
|------|------|--------|--------|------|------|
| PERF-SLA-01 | 单接口 RT P50 | < 50ms | 100ms | Blocker | ☐ ✅ ❌ |
| PERF-SLA-02 | 单接口 RT P95 | < 100ms | 200ms | Blocker | ☐ ✅ ❌ |
| PERF-SLA-03 | 单接口 RT P99 | < 200ms | 500ms | Blocker | ☐ ✅ ❌ |
| PERF-SLA-04 | 单接口 RT P999 | < 500ms | 1s | Major | ☐ ✅ ❌ |
| PERF-SLA-05 | 接口错误率 | < 0.1% | 1% | Blocker | ☐ ✅ ❌ |
| PERF-SLA-06 | 接口 5xx 错误率 | < 0.01% | 0.1% | Blocker | ☐ ✅ ❌ |
| PERF-SLA-07 | 服务可用性 | ≥ 99.9% | 99.5% | Blocker | ☐ ✅ ❌ |
| PERF-SLA-08 | 吞吐量 QPS | 满足业务预期 | 基线 80% | Major | ☐ ✅ ❌ |

### 1.2 资源使用基线

| 编号 | 指标 | 目标值 | 警戒值 | 等级 | 状态 |
|------|------|--------|--------|------|------|
| PERF-RES-01 | 服务器 CPU 平均 | < 50% | 70% | Major | ☐ ✅ ❌ |
| PERF-RES-02 | 服务器 CPU 峰值 | < 70% | 85% | Blocker | ☐ ✅ ❌ |
| PERF-RES-03 | 服务器内存 | < 70% | 80% | Major | ☐ ✅ ❌ |
| PERF-RES-04 | 磁盘使用率 | < 70% | 85% | Major | ☐ ✅ ❌ |
| PERF-RES-05 | 磁盘 IOPS | < 80% | 95% | Major | ☐ ✅ ❌ |
| PERF-RES-06 | 网络带宽 | < 50% | 80% | Major | ☐ ✅ ❌ |
| PERF-RES-07 | 磁盘 IO 等待 | < 5ms | 20ms | Major | ☐ ✅ ❌ |
| PERF-RES-08 | TCP 连接数 | < 5000 | 10000 | Major | ☐ ✅ ❌ |

---

## 二、接口性能

### 2.1 各模块接口性能指标

| 编号 | 模块 | 接口 | RT P99 目标 | 验证方式 | 等级 | 状态 |
|------|------|------|------------|---------|------|------|
| PERF-API-AUTH-01 | kb-auth | POST /auth/login | < 200ms | locust 压测 | Blocker | ☐ ✅ ❌ |
| PERF-API-AUTH-02 | kb-auth | POST /auth/refresh | < 100ms | locust 压测 | Blocker | ☐ ✅ ❌ |
| PERF-API-AUTH-03 | kb-auth | GET /user/profile | < 50ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-AUTH-04 | kb-auth | PUT /user/password | < 100ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-AUTH-05 | kb-auth | POST /token | < 100ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-FILE-01 | kb-file | POST /file/upload | < 500ms（5MB 文件） | locust 压测 | Blocker | ☐ ✅ ❌ |
| PERF-API-FILE-02 | kb-file | POST /file/merge | < 1s | locust 压测 | Blocker | ☐ ✅ ❌ |
| PERF-API-FILE-03 | kb-file | GET /file/list | < 100ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-FILE-04 | kb-file | GET /file/{id}/download | < 200ms（预签名 URL 生成） | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-FILE-05 | kb-file | GET /file/{id}/content | < 200ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-KNOW-01 | kb-knowledge | GET /doc/list | < 100ms | locust 压测 | Blocker | ☐ ✅ ❌ |
| PERF-API-KNOW-02 | kb-knowledge | POST /doc | < 200ms | locust 压测 | Blocker | ☐ ✅ ❌ |
| PERF-API-KNOW-03 | kb-knowledge | PUT /doc/{id} | < 200ms | locust 压测 | Blocker | ☐ ✅ ❌ |
| PERF-API-KNOW-04 | kb-knowledge | GET /search | < 300ms（全文检索） | locust 压测 | Blocker | ☐ ✅ ❌ |
| PERF-API-KNOW-05 | kb-knowledge | GET /folder/tree | < 200ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-KNOW-06 | kb-knowledge | POST /share | < 100ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-KNOW-07 | kb-knowledge | GET /share/verify/{code} | < 100ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-KNOW-08 | kb-knowledge | GET /tag/list | < 100ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-KNOW-09 | kb-knowledge | GET /space/list | < 100ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-KNOW-10 | kb-knowledge | GET /trash/list | < 100ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-OPS-01 | kb-ops | GET /ops/dashboard | < 500ms（聚合查询） | locust 压测 | Blocker | ☐ ✅ ❌ |
| PERF-API-OPS-02 | kb-ops | GET /ops/host/list | < 100ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-OPS-03 | kb-ops | GET /ops/service/list | < 100ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-OPS-04 | kb-ops | GET /log/list | < 200ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-API-OPS-05 | kb-ops | POST /ops/conflict/detect | < 2s（扫描任务） | locust 压测 | Major | ☐ ✅ ❌ |

### 2.2 接口性能优化检查

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-OPT-01 | 无 N+1 查询 | 列表接口不循环查询关联数据 | 代码审查 + 开启 MyBatis 日志 | Blocker | ☐ ✅ ❌ |
| PERF-OPT-02 | 批量查询替代循环 | 关联数据用 IN 查询或 JOIN | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-OPT-03 | 只查询必要字段 | 禁止 SELECT *，用 Projection | 代码审查 + SQL 日志 | Major | ☐ ✅ ❌ |
| PERF-OPT-04 | 分页查询合理 | 列表接口必须分页，size ≤ 100 | 代码审查 | Blocker | ☐ ✅ ❌ |
| PERF-OPT-05 | 深分页优化 | limit 100000+ 用游标分页 | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-OPT-06 | 异步处理耗时操作 | 文件解析/通知用 @Async | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-OPT-07 | 缓存热点数据 | 用户信息/配置 缓存 Redis | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-OPT-08 | 接口幂等性 | 重复请求不重复处理 | 测试用例 L2-IDEM-001 | Major | ☐ ✅ ❌ |
| PERF-OPT-09 | 数据库连接复用 | 使用连接池（HikariCP） | 检查配置 | Blocker | ☐ ✅ ❌ |
| PERF-OPT-10 | HTTP 连接复用 | RestTemplate/OkHttp 连接池 | 检查配置 | Major | ☐ ✅ ❌ |

---

## 三、数据库性能

### 3.1 慢查询检查

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-DB-01 | 慢查询日志开启 | `slow_query_log=ON`，阈值 1s | `SHOW VARIABLES LIKE 'slow_query%'` | Blocker | ☐ ✅ ❌ |
| PERF-DB-02 | 慢查询数量 | < 10 条/h | pt-query-digest 分析 | Major | ☐ ✅ ❌ |
| PERF-DB-03 | 慢查询优化 | 每条慢查询有优化方案或评估结论 | 慢查询优化记录 | Major | ☐ ✅ ❌ |
| PERF-DB-04 | EXPLAIN 全表扫描 | 0 条 SQL type=ALL | `EXPLAIN SELECT ...` | Blocker | ☐ ✅ ❌ |
| PERF-DB-05 | EXPLAIN Using filesort | 0 条 SQL Using filesort（排序未走索引） | EXPLAIN 检查 | Major | ☐ ✅ ❌ |
| PERF-DB-06 | EXPLAIN Using temporary | 0 条 SQL Using temporary（临时表） | EXPLAIN 检查 | Major | ☐ ✅ ❌ |
| PERF-DB-07 | 扫描行数 | rows < 10000（小表除外） | EXPLAIN 检查 | Major | ☐ ✅ ❌ |

### 3.2 索引性能

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-IDX-01 | 核心查询命中索引 | WHERE/ORDER BY/GROUP BY 字段有索引 | EXPLAIN type=ref/eq_ref/range | Blocker | ☐ ✅ ❌ |
| PERF-IDX-02 | 联合索引最左前缀 | 查询条件符合联合索引最左前缀 | EXPLAIN 检查 | Major | ☐ ✅ ❌ |
| PERF-IDX-03 | 覆盖索引 | 高频查询用覆盖索引避免回表 | EXPLAIN Extra=Using index | Major | ☐ ✅ ❌ |
| PERF-IDX-04 | 索引选择性 | 选择性 > 30% 的字段才适合建索引 | `SELECT COUNT(DISTINCT col)/COUNT(*) FROM table` | Minor | ☐ ✅ ❌ |
| PERF-IDX-05 | 冗余索引 | 0 冗余索引 | pt-duplicate-key-checker | Minor | ☐ ✅ ❌ |
| PERF-IDX-06 | 未使用索引 | 0 未使用索引 | pt-index-usage | Minor | ☐ ✅ ❌ |

### 3.3 表结构与数据量

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-TBL-01 | 单表数据量 | < 1000 万（超过需分表） | `SELECT COUNT(*) FROM table` | Major | ☐ ✅ ❌ |
| PERF-TBL-02 | 单表大小 | < 10GB | `SHOW TABLE STATUS` | Major | ☐ ✅ ❌ |
| PERF-TBL-03 | 字段类型合理 | 字段类型最小化（tinyint 优于 int） | 表结构审查 | Minor | ☐ ✅ ❌ |
| PERF-TBL-04 | 大字段分离 | text/blob 分离到扩展表 | 表结构审查 | Major | ☐ ✅ ❌ |
| PERF-TBL-05 | 逻辑删除字段索引 | deleted 字段参与联合索引 | 索引审查 | Minor | ☐ ✅ ❌ |
| PERF-TBL-06 | 定期归档 | 历史数据定期归档（如日志表） | 归档脚本 + cron | Major | ☐ ✅ ❌ |

### 3.4 数据库连接池

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-POOL-01 | 连接池大小合理 | maxPoolSize 10-50（根据业务） | 检查 HikariCP 配置 | Major | ☐ ✅ ❌ |
| PERF-POOL-02 | 连接池监控 | 活跃连接数 < maxPoolSize * 80% | 监控大盘 | Major | ☐ ✅ ❌ |
| PERF-POOL-03 | 连接泄漏检测 | leakDetectionThreshold 配置 | 检查配置 | Major | ☐ ✅ ❌ |
| PERF-POOL-04 | 连接获取耗时 | < 10ms | 监控大盘 | Major | ☐ ✅ ❌ |
| PERF-POOL-05 | 连接等待数 | 等待线程数 < 5 | 监控大盘 | Major | ☐ ✅ ❌ |

---

## 四、缓存性能

### 4.1 Redis 缓存

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-REDIS-01 | 缓存命中率 | > 80% | `INFO stats` keyspace_hits/(hits+misses) | Major | ☐ ✅ ❌ |
| PERF-REDIS-02 | 缓存过期策略 | 热点数据 TTL 合理 | 检查代码 TTL 设置 | Major | ☐ ✅ ❌ |
| PERF-REDIS-03 | 缓存穿透防护 | 不存在的 key 缓存空值或布隆过滤器 | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-REDIS-04 | 缓存雪崩防护 | TTL 加随机偏移 | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-REDIS-05 | 缓存击穿防护 | 热点 key 用互斥锁或永不过期 | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-REDIS-06 | 大 key 检查 | 单 key < 10KB | `redis-cli --bigkeys` | Major | ☐ ✅ ❌ |
| PERF-REDIS-07 | 热 key 检查 | 热点 key 有降级方案 | `redis-cli --hotkeys` | Major | ☐ ✅ ❌ |
| PERF-REDIS-08 | 内存使用率 | < 70% | `INFO memory` | Major | ☐ ✅ ❌ |
| PERF-REDIS-09 | 慢查询 | < 10 条/h，无 KEYS 命令 | `SLOWLOG GET 10` | Blocker | ☐ ✅ ❌ |
| PERF-REDIS-10 | 连接数 | < maxclients * 50% | `INFO clients` | Major | ☐ ✅ ❌ |

### 4.2 本地缓存

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-LOCAL-01 | 本地缓存容量 | < 1000 条 | 代码审查 | Minor | ☐ ✅ ❌ |
| PERF-LOCAL-02 | 本地缓存过期 | 配置 TTL 或 LRU 淘汰 | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-LOCAL-03 | 本地缓存一致性 | 配置变更时同步失效 | 代码审查 | Major | ☐ ✅ ❌ |

---

## 五、JVM 性能

### 5.1 堆内存

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-JVM-01 | 堆内存配置 | -Xms = -Xmx（避免动态调整） | 检查 Dockerfile / 启动脚本 | Major | ☐ ✅ ❌ |
| PERF-JVM-02 | 堆内存大小 | 4GB 服务器配置 -Xmx2g~3g | 检查启动参数 | Major | ☐ ✅ ❌ |
| PERF-JVM-03 | 堆内存使用率 | < 70% | 监控大盘 | Major | ☐ ✅ ❌ |
| PERF-JVM-04 | 堆内存趋势 | 无持续单调上升（内存泄漏） | 监控大盘 1h 趋势 | Blocker | ☐ ✅ ❌ |
| PERF-JVM-05 | GC 后内存回落 | Minor GC 后 heap 回落 > 50% | GC 日志 | Major | ☐ ✅ ❌ |

### 5.2 GC 性能

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-GC-01 | GC 算法 | JDK 21 用 G1 或 ZGC | 检查启动参数 | Major | ☐ ✅ ❌ |
| PERF-GC-02 | Minor GC 频率 | < 10 次/min | GC 日志 | Major | ☐ ✅ ❌ |
| PERF-GC-03 | Minor GC 耗时 | < 50ms | GC 日志 | Major | ☐ ✅ ❌ |
| PERF-GC-04 | Full GC 频率 | 0 次/h | GC 日志 | Blocker | ☐ ✅ ❌ |
| PERF-GC-05 | Full GC 耗时 | < 1s | GC 日志 | Major | ☐ ✅ ❌ |
| PERF-GC-06 | GC 总耗时占比 | < 5% | GC 日志分析 | Major | ☐ ✅ ❌ |
| PERF-GC-07 | GC 日志开启 | -Xlog:gc* | 检查启动参数 | Major | ☐ ✅ ❌ |

### 5.3 线程性能

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-THREAD-01 | 线程数 | < 500 | jstack / 监控 | Major | ☐ ✅ ❌ |
| PERF-THREAD-02 | 死锁检测 | 0 死锁 | jstack | Blocker | ☐ ✅ ❌ |
| PERF-THREAD-03 | 线程阻塞 | BLOCKED/WAITING 线程 < 20% | jstack | Major | ☐ ✅ ❌ |
| PERF-THREAD-04 | 线程池配置 | 核心线程数 + 队列长度合理 | 检查配置 | Major | ☐ ✅ ❌ |
| PERF-THREAD-05 | 线程池拒绝策略 | 合理选择 AbortPolicy/CallerRunsPolicy | 检查配置 | Major | ☐ ✅ ❌ |
| PERF-THREAD-06 | 线程池监控 | 活跃线程数/队列大小 监控 | 监控大盘 | Major | ☐ ✅ ❌ |

---

## 六、并发与线程

### 6.1 并发安全

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-CONC-01 | 共享变量同步 | 共享可变状态用 synchronized/Lock/Atomic | 代码审查 | Blocker | ☐ ✅ ❌ |
| PERF-CONC-02 | 集合线程安全 | 多线程用 ConcurrentHashMap/CopyOnWriteArrayList | 代码审查 | Blocker | ☐ ✅ ❌ |
| PERF-CONC-03 | ThreadLocal 清理 | try-finally remove() | 代码审查 | Blocker | ☐ ✅ ❌ |
| PERF-CONC-04 | 并发测试通过 | 20 并发无错误，无数据竞争 | 测试用例 L2-CONC-001~005 | Blocker | ☐ ✅ ❌ |
| PERF-CONC-05 | 幂等性测试 | 重复提交返回一致结果 | 测试用例 L2-IDEM-001 | Major | ☐ ✅ ❌ |

### 6.2 锁优化

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-LOCK-01 | 锁粒度最小化 | 锁定最小范围，不锁整个方法 | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-LOCK-02 | 锁超时 | tryLock(timeout) 避免死锁 | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-LOCK-03 | 乐观锁优先 | 乐观锁（@Version）优先于悲观锁 | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-LOCK-04 | 锁顺序一致 | 多锁按固定顺序获取，避免死锁 | 代码审查 | Blocker | ☐ ✅ ❌ |

---

## 七、前端性能

### 7.1 页面加载性能

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-FE-01 | 首屏加载时间 FCP | < 1.5s | Lighthouse / Chrome DevTools | Blocker | ☐ ✅ ❌ |
| PERF-FE-02 | 可交互时间 TTI | < 3s | Lighthouse | Blocker | ☐ ✅ ❌ |
| PERF-FE-03 | 最大内容绘制 LCP | < 2.5s | Lighthouse | Major | ☐ ✅ ❌ |
| PERF-FE-04 | 累积布局偏移 CLS | < 0.1 | Lighthouse | Major | ☐ ✅ ❌ |
| PERF-FE-05 | 首字节时间 TTFB | < 500ms | Chrome DevTools | Major | ☐ ✅ ❌ |
| PERF-FE-06 | Lighthouse 性能分 | ≥ 85 | Lighthouse | Major | ☐ ✅ ❌ |

### 7.2 资源优化

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-FE-07 | JS bundle 大小 | < 500KB（gzip 后） | webpack-bundle-analyzer | Major | ☐ ✅ ❌ |
| PERF-FE-08 | CSS 大小 | < 100KB（gzip 后） | webpack-bundle-analyzer | Major | ☐ ✅ ❌ |
| PERF-FE-09 | 图片优化 | WebP/AVIF + lazy load | 检查图片资源 | Major | ☐ ✅ ❌ |
| PERF-FE-10 | 静态资源压缩 | gzip/brotli 开启 | `curl -I -H "Accept-Encoding: gzip"` | Blocker | ☐ ✅ ❌ |
| PERF-FE-11 | 静态资源缓存 | Cache-Control max-age=31536000 | `curl -I` 检查响应头 | Major | ☐ ✅ ❌ |
| PERF-FE-12 | HTTP/2 启用 | Nginx 启用 HTTP/2 | `curl -I --http2` | Major | ☐ ✅ ❌ |
| PERF-FE-13 | CDN 加速 | 静态资源走 CDN（如适用） | 检查域名解析 | Minor | ☐ ✅ ❌ |
| PERF-FE-14 | 代码分割 | 路由懒加载 + 组件按需加载 | webpack-bundle-analyzer | Major | ☐ ✅ ❌ |
| PERF-FE-15 | Tree Shaking | 无未使用的第三方库依赖 | 检查 import 方式 | Minor | ☐ ✅ ❌ |

### 7.3 运行时性能

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-FE-16 | 防抖节流 | 搜索/resize/scroll 事件防抖节流 | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-FE-17 | 大列表虚拟滚动 | > 100 条用虚拟滚动 | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-FE-18 | 避免不必要的响应式 | 大数据用 shallowRef/markRaw | 代码审查 | Minor | ☐ ✅ ❌ |
| PERF-FE-19 | v-for key 唯一 | :key 唯一，不用 index | 代码审查 | Blocker | ☐ ✅ ❌ |
| PERF-FE-20 | 计算属性合理 | 复杂计算用 computed 缓存 | 代码审查 | Minor | ☐ ✅ ❌ |

---

## 八、Nginx 性能

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-NGINX-01 | worker_processes | auto 或 CPU 核心数 | 检查 nginx.conf | Major | ☐ ✅ ❌ |
| PERF-NGINX-02 | worker_connections | ≥ 10240 | 检查 nginx.conf | Major | ☐ ✅ ❌ |
| PERF-NGINX-03 | keepalive_timeout | 65s（合理值） | 检查 nginx.conf | Minor | ☐ ✅ ❌ |
| PERF-NGINX-04 | gzip 开启 | gzip on + gzip_types | `curl -I -H "Accept-Encoding: gzip"` | Major | ☐ ✅ ❌ |
| PERF-NGINX-05 | 静态资源缓存 | expires 30d | 检查 location 配置 | Major | ☐ ✅ ❌ |
| PERF-NGINX-06 | upstream keepalive | keepalive 32 | 检查 upstream 配置 | Major | ☐ ✅ ❌ |
| PERF-NGINX-07 | sendfile 开启 | sendfile on | 检查 nginx.conf | Minor | ☐ ✅ ❌ |
| PERF-NGINX-08 | tcp_nopush 开启 | tcp_nopush on | 检查 nginx.conf | Minor | ☐ ✅ ❌ |
| PERF-NGINX-09 | access_log 缓冲 | buffer=32k flush=5s | 检查 nginx.conf | Minor | ☐ ✅ ❌ |
| PERF-NGINX-10 | 限流配置 | limit_req_zone 合理 | 检查 nginx.conf | Major | ☐ ✅ ❌ |
| PERF-NGINX-11 | 双层 Nginx 链路优化 | 腾讯云2号→mykng 之间 keepalive | 检查 upstream 配置 | Major | ☐ ✅ ❌ |
| PERF-NGINX-12 | 静态资源 alias 性能 | /kb/s/ alias 直接返回，不反代 | 检查 location /kb/s/ 配置 | Major | ☐ ✅ ❌ |

---

## 九、大文件与批处理

### 9.1 文件上传下载

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-FILE-01 | 大文件分片上传 | > 10MB 文件分片上传 | 测试用例 | Blocker | ☐ ✅ ❌ |
| PERF-FILE-02 | 分片大小合理 | 5MB/片（可配置） | 检查配置 | Major | ☐ ✅ ❌ |
| PERF-FILE-03 | 断点续传 | 支持断点续传 | 测试 | Major | ☐ ✅ ❌ |
| PERF-FILE-04 | 下载走预签名 URL | 直接从 MinIO 下载，不经后端 | 检查 FileController | Blocker | ☐ ✅ ❌ |
| PERF-FILE-05 | 上传并发限制 | 单用户同时上传 ≤ 3 个文件 | 检查前端逻辑 | Minor | ☐ ✅ ❌ |

### 9.2 文件解析

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-PARSE-01 | 异步解析 | 文件合并后异步解析，不阻塞上传 | 检查 FileService | Blocker | ☐ ✅ ❌ |
| PERF-PARSE-02 | 解析超时控制 | 单文件解析超时 5min | 检查解析任务 | Major | ☐ ✅ ❌ |
| PERF-PARSE-03 | 解析失败重试 | 失败自动重试 3 次 | 检查解析任务 | Major | ☐ ✅ ❌ |
| PERF-PARSE-04 | 大文件解析优化 | > 50MB 文件流式解析 | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-PARSE-05 | 解析任务队列 | 用消息队列削峰（如适用） | 检查架构 | Minor | ☐ ✅ ❌ |

### 9.3 批量操作

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-BATCH-01 | 批量插入 | saveBatch 替代循环 save | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-BATCH-02 | 批量大小合理 | 1000 条/批 | 检查配置 | Major | ☐ ✅ ❌ |
| PERF-BATCH-03 | 批量更新 | updateBatchById | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-BATCH-04 | 数据导入分批 | 大数据导入分批提交 | 代码审查 | Major | ☐ ✅ ❌ |
| PERF-BATCH-05 | 事务大小控制 | 单事务 < 1000 条 | 代码审查 | Major | ☐ ✅ ❌ |

---

## 十、压力测试

### 10.1 单接口压测

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-STRESS-01 | 登录接口压测 | 100 QPS，P99 < 500ms，错误率 < 0.1% | locust 压测 | Blocker | ☐ ✅ ❌ |
| PERF-STRESS-02 | 文档列表压测 | 100 QPS，P99 < 200ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-STRESS-03 | 搜索接口压测 | 50 QPS，P99 < 500ms | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-STRESS-04 | 文件上传压测 | 10 并发，P99 < 1s | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-STRESS-05 | 文档创建压测 | 50 QPS，P99 < 500ms | locust 压测 | Major | ☐ ✅ ❌ |

### 10.2 混合场景压测

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-MIX-01 | 业务比例压测 | 模拟真实业务比例（读:写=8:2） | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-MIX-02 | 1.5 倍 QPS 压测 | 1.5 倍预期 QPS，持续 30min，无错误 | locust 压测 | Blocker | ☐ ✅ ❌ |
| PERF-MIX-03 | 2 倍 QPS 压测 | 2 倍预期 QPS，错误率 < 1%，可降级 | locust 压测 | Major | ☐ ✅ ❌ |
| PERF-MIX-04 | 持续压测 1h | 1h 持续压测，无内存泄漏 | locust 压测 | Blocker | ☐ ✅ ❌ |
| PERF-MIX-05 | 峰值压测 | 模拟峰值流量（如早上 9 点），无雪崩 | locust 压测 | Major | ☐ ✅ ❌ |

### 10.3 稳定性测试

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-STAB-01 | 7×24h 稳定性 | 持续运行 7 天，无 OOM，无重启 | 长稳测试 | Major | ☐ ✅ ❌ |
| PERF-STAB-02 | 内存增长 | 7 天内存增长 < 10% | 监控大盘 | Major | ☐ ✅ ❌ |
| PERF-STAB-03 | Full GC 次数 | 7 天 Full GC < 5 次 | GC 日志 | Major | ☐ ✅ ❌ |
| PERF-STAB-04 | 连接泄漏 | 7 天数据库/Redis 连接数稳定 | 监控大盘 | Blocker | ☐ ✅ ❌ |
| PERF-STAB-05 | 线程泄漏 | 7 天线程数稳定 | jstack | Major | ☐ ✅ ❌ |

### 10.4 压测执行清单

| 编号 | 步骤 | 命令/操作 | 状态 |
|------|------|----------|------|
| PERF-EXEC-01 | 准备压测环境 | STAGING 环境数据量与 PROD 一致 | ☐ ✅ ❌ |
| PERF-EXEC-02 | 准备压测脚本 | locust 脚本编写 + 调试 | ☐ ✅ ❌ |
| PERF-EXEC-03 | 基线压测 | 逐步加压，找到性能拐点 | ☐ ✅ ❌ |
| PERF-EXEC-04 | 容量压测 | 1.5 倍 / 2 倍 QPS 压测 | ☐ ✅ ❌ |
| PERF-EXEC-05 | 稳定性压测 | 1h 持续压测 | ☐ ✅ ❌ |
| PERF-EXEC-06 | 压测报告 | 输出压测报告，含 QPS/RT/错误率/资源使用 | ☐ ✅ ❌ |
| PERF-EXEC-07 | 性能优化 | 针对瓶颈优化，重新压测验证 | ☐ ✅ ❌ |

---

## 十一、容量规划

### 11.1 当前容量

| 编号 | 资源 | 当前使用 | 峰值使用 | 容量上限 | 使用率 | 状态 |
|------|------|---------|---------|---------|--------|------|
| PERF-CAP-01 | 服务器 CPU | ___ 核 | ___ 核 | ___ 核 | ___% | ☐ ✅ ❌ |
| PERF-CAP-02 | 服务器内存 | ___ GB | ___ GB | ___ GB | ___% | ☐ ✅ ❌ |
| PERF-CAP-03 | 服务器磁盘 | ___ GB | ___ GB | ___ GB | ___% | ☐ ✅ ❌ |
| PERF-CAP-04 | MySQL 数据量 | ___ GB | ___ GB | ___ GB | ___% | ☐ ✅ ❌ |
| PERF-CAP-05 | MinIO 存储量 | ___ GB | ___ GB | ___ GB | ___% | ☐ ✅ ❌ |
| PERF-CAP-06 | MongoDB 数据量 | ___ GB | ___ GB | ___ GB | ___% | ☐ ✅ ❌ |
| PERF-CAP-07 | Redis 内存 | ___ MB | ___ MB | ___ MB | ___% | ☐ ✅ ❌ |
| PERF-CAP-08 | 网络带宽 | ___ Mbps | ___ Mbps | ___ Mbps | ___% | ☐ ✅ ❌ |

### 11.2 容量预测

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| PERF-PRED-01 | 月度增长率 | 各资源月增长 < 10% | 监控趋势分析 | Major | ☐ ✅ ❌ |
| PERF-PRED-02 | 容量预警 | 资源使用率 > 70% 预警 | 监控告警 | Major | ☐ ✅ ❌ |
| PERF-PRED-03 | 扩容预案 | 容量不足时有扩容方案 | 扩容预案文档 | Major | ☐ ✅ ❌ |
| PERF-PRED-04 | 容量评估周期 | 每月评估 1 次 | 容量评估报告 | Minor | ☐ ✅ ❌ |

---

## 附录 A：性能等级定义

| 等级 | 含义 | 处理方式 |
|------|------|---------|
| **Blocker** | 关键性能问题，影响用户体验或服务稳定 | 必须优化，否则禁止发布 |
| **Major** | 重要性能问题，建议优化 | 评估后决定是否必须优化 |
| **Minor** | 次要性能优化，可后续改进 | 记录 TODO，后续优化 |

---

## 附录 B：性能测试工具速查

| 工具 | 用途 | 命令示例 | 安装方式 |
|------|------|---------|---------|
| **locust** | Python 接口压测 | `locust -f locustfile.py --host=https://kb.marschat.online` | `pip install locust` |
| **JMeter** | GUI/CLI 压测 | `jmeter -n -t test.jmx -l result.jtl` | 下载安装 |
| **wrk** | HTTP 压测 | `wrk -t12 -c400 -d30s https://kb.marschat.online/kb/api/user/profile` | `apt install wrk` |
| **ab** | Apache Bench | `ab -n 1000 -c 100 https://kb.marschat.online/kb/api/user/profile` | `apt install apache2-utils` |
| **Lighthouse** | 前端性能审计 | Chrome DevTools → Lighthouse | Chrome 内置 |
| **Chrome DevTools** | 前端性能分析 | F12 → Performance / Network | Chrome 内置 |
| **arthas** | Java 在线诊断 | `thread` / `dashboard` / `trace` | `curl -O arthas.jar` |
| **jstack** | 线程栈分析 | `jstack <pid>` | JDK 内置 |
| **jmap** | 堆内存分析 | `jmap -heap <pid>` | JDK 内置 |
| **GCViewer** | GC 日志分析 | `java -jar gcviewer.jar gc.log` | 下载 jar |
| **pt-query-digest** | MySQL 慢查询分析 | `pt-query-digest slow.log` | `apt install percona-toolkit` |
| **EXPLAIN** | SQL 执行计划 | `EXPLAIN SELECT ...` | MySQL 内置 |
| **redis-cli --bigkeys** | Redis 大 key 分析 | `redis-cli --bigkeys` | Redis 内置 |
| **redis-cli --hotkeys** | Redis 热 key 分析 | `redis-cli --hotkeys`（需 LFU） | Redis 内置 |

---

## 附录 C：常见性能反模式

| 反模式 | 正确做法 |
|--------|---------|
| `SELECT *` | 明确字段 |
| N+1 查询 | JOIN 或批量查询 |
| 循环 save | saveBatch |
| 同步调用外部接口 | 异步 @Async |
| 大事务 | 事务范围最小化 |
| 无分页查询 | 必须分页 |
| 深分页 LIMIT 100000, 20 | 游标分页 WHERE id > last_id |
| `LIKE '%abc'` | `LIKE 'abc%'` 或全文检索 |
| 缓存无过期 | 配置 TTL |
| 缓存无穿透防护 | 缓存空值或布隆过滤器 |
| 大 key 缓存 | 拆分小 key |
| ThreadLocal 未清理 | try-finally remove() |
| 字符串拼接 | StringBuilder |
| 频繁创建对象 | 对象池/复用 |
| 锁整个方法 | 锁最小范围 |
| 乐观锁未用 | @Version 优先 |
| 前端全量加载 | 路由懒加载 + 代码分割 |
| 前端无防抖节流 | debounce/throttle |
| 大列表无虚拟滚动 | vue-virtual-scroller |
| 图片未优化 | WebP + lazy load |
| 静态资源未压缩 | gzip/brotli |
| HTTP/1.1 | HTTP/2 |
| 无连接池 | HikariCP + OkHttp 连接池 |

---

## 附录 D：压测报告模板

```markdown
# 压测报告 - YYYY-MM-DD

## 1. 测试环境
- 环境：STAGING / PROD
- 服务器：CPU ___核 / 内存 ___GB / 磁盘 ___GB
- 数据量：MySQL ___GB / MinIO ___GB / Redis ___MB

## 2. 测试场景
| 场景 | 接口 | QPS | 并发数 | 持续时间 |
|------|------|-----|--------|---------|
| 单接口压测-登录 | POST /auth/login | 100 | 100 | 5min |
| 混合场景 | 见 locust 脚本 | 200 | 200 | 30min |

## 3. 测试结果
| 场景 | QPS | P50 | P95 | P99 | 错误率 | 资源峰值 |
|------|-----|-----|-----|-----|--------|---------|
| 单接口-登录 | 100 | 50ms | 100ms | 200ms | 0.05% | CPU 60% / 内存 70% |

## 4. 性能瓶颈
- 瓶颈1：xxx 接口 RT P99 > 500ms，原因为 xxx，优化建议 xxx
- 瓶颈2：xxx

## 5. 结论
- [ ] 满足 SLA（P99 < 200ms，错误率 < 0.1%）
- [ ] 1.5 倍 QPS 持续 30min 无错误
- [ ] 1h 持续压测无内存泄漏
- [ ] 可以上线 / 需优化后重新压测
```

---

## 附录 E：变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|---------|------|
| v1.0 | 2026-06-28 | 初版创建，覆盖 11 大性能维度 | 测试组 |
| v1.1 | 2026-06-28 | 对齐 SOP V1.1 附录 E，补充各模块接口性能指标、双层 Nginx 链路优化、压测报告模板、容量规划 | 测试组 |
