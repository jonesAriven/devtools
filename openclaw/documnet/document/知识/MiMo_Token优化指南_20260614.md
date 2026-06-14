# MiMo Token 优化指南

> 创建时间：2026-06-14
> 用途：降低 MiMo 模型在 OpenClaw 中的 Token 消耗

## 一、Token 消耗高的核心原因

### 1. 上下文累积（最主要原因）

当前配置 `compaction.mode: manual`，意味着：
- **所有对话历史都会保留**，不会自动压缩
- 每次请求都会携带完整的历史对话
- 随着对话轮次增加，输入 Token 持续累积

**对比：**
- `compaction.mode: auto` → 自动压缩旧消息，保持上下文长度适中
- `compaction.mode: manual` → 完整保留，适合需要完整上下文的场景

### 2. thinkingMode 的影响

| thinkingMode | Token 消耗 | 适用场景 |
|--------------|-----------|---------|
| minimal | 低 | 简单问答、快速查询 |
| low | 中低 | 一般任务 |
| **medium**（当前） | **中** | **复杂问题、逻辑推理** |
| high | 高 | 数学证明、复杂分析 |

**当前 thinkingMode=medium 会：**
- 生成内部推理链（消耗额外 token）
- 增加输出 token 数量
- 提高准确性，但增加成本

### 3. 系统提示词开销

每次请求都会携带：
- `AGENTS.md`（工作规范）
- `SOUL.md`（人格设定）
- `MEMORY.md`（记忆文件）
- `TOOLS.md`（工具规范）
- 其他配置文件

**估算：** 系统提示词约 5k-15k token（取决于文件大小）

### 4. 工具调用结果

每次工具调用（exec、read、web_search 等）的返回结果都会：
- 作为上下文保留
- 在后续请求中重复发送
- 工具输出通常很大（命令输出、文件内容等）

---

## 二、优化方案

### 方案 1：调整 thinkingMode（效果显著）

**根据任务类型动态调整：**

```yaml
# 简单任务（省 token）
thinkingMode: minimal

# 日常任务（平衡）
thinkingMode: low

# 复杂任务（当前）
thinkingMode: medium

# 高精度任务
thinkingMode: high
```

**建议：**
- 日常对话设为 `low` 或 `minimal`
- 复杂任务时手动切换到 `medium` 或 `high`
- 可以通过会话命令 `/thinking low` 临时调整

### 方案 2：开启自动压缩（效果最显著）

```yaml
compaction:
  mode: auto
```

**效果：**
- 自动压缩旧消息，保留最近 N 轮对话
- 防止上下文无限累积
- 预计可降低 50-70% 的输入 Token

**代价：**
- 可能丢失早期上下文
- 不适合需要完整历史的任务

### 方案 3：精简系统提示词

**检查当前系统提示词大小：**
```bash
wc -c /root/.openclaw/workspace/AGENTS.md
wc -c /root/.openclaw/workspace/SOUL.md
wc -c /root/.openclaw/workspace/MEMORY.md
wc -c /root/.openclaw/workspace/TOOLS.md
```

**优化建议：**
- 删除不必要的示例代码
- 压缩重复内容
- 将大段说明移到单独文件，按需加载

### 方案 4：清理工具输出

**问题：** 工具调用结果会累积在上下文中

**解决：**
- 避免输出过长的命令结果
- 使用 `read` 时限制行数（`limit` 参数）
- 大文件分块读取，不要一次读取全部

### 方案 5：使用会话隔离

对于独立任务，使用子会话：
```yaml
# 子会话不会继承父会话的完整历史
sessions_spawn:
  task: "具体任务"
  context: "isolated"
```

---

## 三、成本对比估算

假设一个 20 轮对话的场景：

| 配置 | 输入 Token（估算） | 输出 Token（估算） | 相对成本 |
|------|-------------------|-------------------|---------|
| compaction=manual, thinking=medium | 50k-100k | 5k-10k | 100%（基准） |
| compaction=auto, thinking=medium | 15k-30k | 5k-10k | 30-50% |
| compaction=manual, thinking=minimal | 50k-100k | 2k-5k | 60-80% |
| compaction=auto, thinking=minimal | 15k-30k | 2k-5k | **20-40%** |

---

## 四、推荐配置

### 场景 A：日常对话（省 token）

```yaml
compaction:
  mode: auto
inferenceParams:
  temperature: 0.1
  top_p: 0.8
  thinkingMode: minimal
```

### 场景 B：复杂任务（保准确性）

```yaml
compaction:
  mode: manual
inferenceParams:
  temperature: 0.1
  top_p: 0.8
  thinkingMode: medium
```

### 场景 C：混合模式（推荐）

保持 `compaction.mode: manual`，但：
1. 定期手动清理会话（`/reset` 或 `/new`）
2. 根据任务类型调整 thinkingMode
3. 避免输出过长的工具结果

---

## 五、监控 Token 消耗

### 查看当前会话的 Token 使用

```bash
# 通过 OpenClaw CLI
openclaw status

# 或在会话中
/status
```

### 关注指标

| 指标 | 含义 |
|------|------|
| Input tokens | 输入 token 数（受上下文长度影响） |
| Output tokens | 输出 token 数（受 thinkingMode 影响） |
| Cache hit rate | 缓存命中率（越高越省） |
| Cost | 总成本 |

---

## 六、常见误区

### ❌ 误区 1："MiMo 模型本身消耗高"

**真相：** MiMo 和 DeepSeek 的 token 消耗差异主要来自**配置策略**，不是模型本身。同样的配置下，消耗差异不会太大。

### ❌ 误区 2："安装 XXX 技能可以自动优化"

**真相：** OpenClaw 没有"智能路由"或"上下文裁剪"的自动技能。优化需要手动调整配置。

### ❌ 误区 3："thinkingMode 越高越好"

**真相：** thinkingMode 越高，token 消耗越大。对于简单任务，`minimal` 就足够了。

### ❌ 误区 4："compaction=manual 更准确"

**真相：** 对于大多数任务，`auto` 模式已经足够。只有需要精确引用早期对话时，才需要 `manual`。

---

## 七、快速检查清单

- [ ] 检查 `compaction.mode` 是否为 `manual`（如果是，考虑改为 `auto`）
- [ ] 检查 `thinkingMode` 是否为 `medium`（如果任务简单，改为 `minimal`）
- [ ] 检查系统提示词文件大小（AGENTS.md、MEMORY.md 等）
- [ ] 检查是否有过长的工具输出累积在上下文中
- [ ] 定期使用 `/reset` 或 `/new` 清理会话

---

## 八、参考配置

### 当前配置（2026-06-14）

```yaml
inferenceParams:
  temperature: 0.1
  top_p: 0.8
  thinkingMode: medium

compaction:
  mode: manual

toolDefaults:
  maxRetries: 2
```

### 优化后配置（推荐）

```yaml
inferenceParams:
  temperature: 0.1
  top_p: 0.8
  thinkingMode: minimal  # 或 low

compaction:
  mode: auto  # 关键改动

toolDefaults:
  maxRetries: 2
```

---

## 变更记录

| 日期 | 变更内容 | 操作人 |
|------|---------|--------|
| 2026-06-14 | 初始版本 | 小桉 |
