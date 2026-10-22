# ADR-2026-10-22-R16 · T-ENG-19 应用侧闭环 —— cosmic-api GR 多节点 failover

- 状态：✅ 已实施并全链验证
- 日期：2026-10-22（R16 轮）
- 相关：T-ENG-19（R13 P0 环境故障的应用侧根因）· 坑 #49（TROUBLESHOOTING §18）· ADR-2026-10-22-R15

## 背景

R13（10-20 夜）GR 主库从 105 漂移到 182:3307，六应用 SSO 全线 500（`error code [1290] super-read-only`）。
T-ENG-19 登记时列出三块根因/治本项，其中「应用侧 JDBC 多地址」标注为跨仓改造需单独排期。
R14/R15 期间确认：**Java 侧 8 服务早已完成** Connector/J 三地址 failover
（activecode / infra-monitor / kb-ops / portal / kb-file / kb-intelligence / kb-knowledge / auth-center，
`loadBalanceStrategy=random&failOverReadOnly=false`；auth-center 线上日志实证
`com.mysql.cj.jdbc.ha.FailoverConnectionProxy` 在场）。本轮经良哥授权
（「如涉及应用侧改造，你就去改造吧」）补齐**最后一个单地址应用 cosmic-api（Python）**。

## 决策

**cosmic-api DB 层改多候选 failover（cosmic-studio `275d600`，流水线 #128 部署）**：

1. `config.py`：新增 `DB_HOSTS`（逗号分隔 `host:port`）解析；`DB_HOST` 单地址保留作 dev 兼容；
   `validate_config` 同步改为校验 `DB_HOST/DB_HOSTS`。
2. `db.py`：
   - `connect(db, exclude=None)`：按候选逐个 `pymysql.connect`，成功打 `conn._mc_host` 落点标记；
   - `execute`/`executemany`：命中 1290（super-read-only）自动剔除当前落点、换候选重连重试（有界）；
   - `tx`：入口 `SELECT @@super_read_only` 探测，抽到 SECONDARY 直接换节点，保证事务全程在 PRIMARY；
     **事务中途不换节点**（跨节点事务上下文必丢，中途 1290 上抛）；
   - `_acquire`：连接池取出路径也过 `exclude` 过滤 + ping 失效换连接；坏连接 `_discard` 不还池；
   - `_release`：`open=False` 的连接直接丢弃。
3. `docker-compose.yml`：env 增 `DB_HOSTS=192.168.31.105:3306,192.168.31.182:3307,192.168.31.182:3308`。

**为什么不选 ProxySQL / MySQL Router**：那要新增部署栈组件（且 router 容器本身是新单点），
超出「应用侧改造」授权范围；应用层方案对 Python 栈零新增依赖、对 Java 栈零改动
（Connector/J 原生支持），与既有 8 服务语义对齐（random 分布 + failover）。

## 验证（全绿）

- **R16-T 真集群 6/6**（cosmic-api 容器内实跑，`/root/verify/r16/r16_t.py`）：
  T0 基准探测（105=PRIMARY，182:3307/3308=SECONDARY）· T1 读 · **T2 写自愈
  （候选从库打头：182:3307→3308→105 连续切换落 PRIMARY）** · T3 tx 可写探测 ·
  T4 首位不可达节点跳过 · T5 单地址兼容。
- **R16-U 浏览器 9/9**（headless · mykng · 本机零窗口）：账密登录 → 业务页特征词
  （编写库/归档库/质量门禁/业务词库）→ 12s 不漂移 + 会话在 → Console 零错 ·
  零 5xx · 零 CORS · 零加载失败 → 可交互容器 13 个。
- 部署层：双端 tip 核验一致（`275d600`）；流水线 #128 success；容器 env `DB_HOSTS` 生效；
  服务端 cosmic-api 日志 15min 零 1290/异常。

## 实现期修掉的真 bug（复现同类改造必读，已入坑 #49）

1. 重试边界差一：`len(excluded) < len(DB_HOSTS) - 1` 会漏掉最后一个候选 → 改 `< len(DB_HOSTS)`；
2. `_acquire` 池路径不过 exclude → 旧连接（落在被排除节点）被原样复用，重试打回同一从库；
3. `try` 内 return +  finally 外不可达的 `_release` → 成功路径连接静默泄漏（改 finally + open 检查）。

## 遗留（未动，待良哥拍板）

- T-ENG-19 数据库侧两项：① 单主模式失效链（node1 被逐出 ⇒ 写能力静默漂走）；
  ② `single_primary_mode=ON` 是 SET PERSIST 写进数据卷的周期复发雷，应 `RESET PERSIST`
  后落到配置真源。按调试边界铁律（数据库配置修改须先问用户），本轮只做应用侧。
