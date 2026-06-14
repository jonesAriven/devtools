# MiMo Token 优化指南（基于官方文档）

> 创建时间：2026-06-14
> 更新时间：2026-06-14（基于 OpenClaw 官方文档修订）
> 用途：降低 MiMo 模型在 OpenClaw 中的 Token 消耗

## ⚠️ 重要说明

本文档基于 OpenClaw 官方文档编写，所有配置参数均有官方依据。

---

## 一、Token 消耗的核心机制

### 官方文档明确说明

> "Everything the model receives counts toward the context limit"

**每次请求的 Token 组成：**

| 组成部分 | 说明 |
|---------|------|
| **System prompt** | 工具列表、技能元数据、工作区文件（AGENTS.md、SOUL.md、MEMORY.md 等） |
| **Conversation history** | 所有用户和助手消息 |
| **Tool calls and results** | 工具调用和返回结果 |
| **Attachments** | 图片、音频、文件 |
| **Compaction summaries** | 压缩摘要 |
| **Provider wrappers** | 提供商包装头（不可见但计入） |

### 关键限制参数（官方）

```yaml
agents:
  defaults:
    bootstrapMaxChars: 20000        # 单个注入文件最大字符数
    bootstrapTotalMaxChars: 60000   # 总注入上限
    contextLimits:
      toolResultMaxChars: 16000     # 工具结果最大字符数（<100K上下文）
      # 100K+上下文：32000
      # 200K+上下文：64000
      memoryGetMaxChars: 10000      # memory_get 最大字符数
      memoryGetDefaultLines: 200    # memory_get 默认行数
```

---

## 二、官方支持的配置参数

### 1. 压缩配置（compaction）

**官方支持的模式：**

```yaml
compaction:
  mode: "default" | "safeguard"
  enabled: true
  reserveTokens: 16384           # 保留 token 数
  keepRecentTokens: 20000        # 保留最近 token 数
  reserveTokensFloor: 20000      # 保留 token 下限
  maxActiveTranscriptBytes: "20mb"  # 活跃转录文件大小限制
  truncateAfterCompaction: true  # 压缩后截断
  midTurnPrecheck:
    enabled: false               # 工具循环预检
```

**压缩触发条件：**

1. **溢出恢复**：模型返回上下文溢出错误时
2. **阈值维护**：`contextTokens > contextWindow - reserveTokens`

### 2. 上下文限制（contextLimits）

```yaml
agents:
  defaults:
    contextLimits:
      toolResultMaxChars: 16000    # 工具结果最大字符数
      memoryGetMaxChars: 10000     # memory_get 最大字符数
      memoryGetDefaultLines: 200   # memory_get 默认行数
      postCompactionMaxChars: 8000 # 压缩后最大字符数
```

### 3. 启动注入限制（bootstrap）

```yaml
agents:
  defaults:
    bootstrapMaxChars: 20000       # 单个文件最大字符数
    bootstrapTotalMaxChars: 60000  # 总注入上限
```

### 4. 图片处理

```yaml
agents:
  defaults:
    imageMaxDimensionPx: 1200      # 图片最大维度（降低可减少视觉 token）
```

---

## 三、Token 消耗高的真正原因

### 原因 1：系统提示词累积

**官方说明：**

> "Workspace + bootstrap files (AGENTS.md, SOUL.md, TOOLS.md, IDENTITY.md, USER.md, HEARTBEAT.md, BOOTSTRAP.md when new, plus MEMORY.md when present)"

**当前估算：**
- AGENTS.md: ~3000 字符
- SOUL.md: ~500 字符
- MEMORY.md: ~15000 字符（**这是大头！**）
- TOOLS.md: ~8000 字符
- 其他配置文件: ~2000 字符
- **总计：~28500 字符 ≈ 7000-10000 tokens**

**优化方案：**
```bash
# 检查各文件大小
wc -c /root/.openclaw/workspace/AGENTS.md
wc -c /root/.openclaw/workspace/SOUL.md
wc -c /root/.openclaw/workspace/MEMORY.md
wc -c /root/.openclaw/workspace/TOOLS.md
```

**建议：**
- MEMORY.md 保持在 5000 字符以内
- 定期清理过时信息
- 将大段内容移到单独文件，按需加载

### 原因 2：工具调用结果累积

**官方说明：**

> "Tool calls and tool results" 都会计入上下文

**问题：**
- 每次 `exec`、`read`、`web_search` 的返回结果都会保留
- 工具输出通常很大（命令输出、文件内容）
- 随着对话进行，累积越来越多

**优化方案：**
```yaml
# 限制工具结果大小
agents:
  defaults:
    contextLimits:
      toolResultMaxChars: 8000  # 从 16000 降到 8000
```

**实践建议：**
- 使用 `read` 时设置 `limit` 参数
- 避免输出过长的命令结果
- 大文件分块读取

### 原因 3：对话历史累积

**官方说明：**

> "Conversation history (user + assistant messages)" 计入上下文

**问题：**
- 如果 `compaction.mode` 设置不当，历史会累积
- 每轮对话都会增加输入 token

**优化方案：**
```yaml
compaction:
  mode: "safeguard"  # 使用安全压缩模式
  reserveTokens: 16384
  keepRecentTokens: 15000  # 只保留最近 15000 tokens
```

### 原因 4：缓存未命中

**官方说明：**

> "Cache reads are significantly cheaper than input tokens, while cache writes are billed at a higher multiplier"

**优化方案：**
```yaml
agents:
  defaults:
    heartbeat:
      every: "55m"  # 保持缓存 warm（假设 TTL 1h）
    models:
      "mimo/mimo-v2.5":
        params:
          cacheRetention: "long"
```

---

## 四、官方推荐的优化方法

### 方法 1：使用 `/compact` 命令

**官方说明：**

> "Use `/compact` to summarize long sessions"

在对话中输入 `/compact` 可以手动触发压缩。

### 方法 2：调整压缩参数

```yaml
compaction:
  mode: "safeguard"
  reserveTokens: 16384
  keepRecentTokens: 15000
  reserveTokensFloor: 10000
```

### 方法 3：限制工具输出

```yaml
agents:
  defaults:
    contextLimits:
      toolResultMaxChars: 8000
```

### 方法 4：精简系统提示词

**官方说明：**

> "Keep skill descriptions short (skill list is injected into the prompt)"

**建议：**
- 删除 MEMORY.md 中的过时信息
- 压缩 AGENTS.md 中的示例代码
- 将大段说明移到单独文件

### 方法 5：降低图片分辨率

```yaml
agents:
  defaults:
    imageMaxDimensionPx: 800  # 从 1200 降到 800
```

### 方法 6：使用小模型处理简单任务

**官方说明：**

> "Prefer smaller models for verbose, exploratory work"

对于简单任务，可以切换到 DeepSeek 等小模型。

---

## 五、监控 Token 消耗

### 官方命令

```bash
# 查看当前会话状态
/status

# 查看每次回复的 token 消耗
/usage full

# 查看成本摘要
/usage cost

# 查看上下文详情
/context detail
```

### 关键指标

| 指标 | 说明 |
|------|------|
| `context.used` | 当前上下文使用量 |
| `context.window` | 模型上下文窗口 |
| `input_tokens` | 输入 token 数 |
| `output_tokens` | 输出 token 数 |
| `cacheRead` | 缓存读取 token 数 |
| `cacheWrite` | 缓存写入 token 数 |

---

## 六、常见误区（基于官方文档纠正）

### ❌ 误区 1：`compaction.mode: manual`

**官方说明：** 只支持 `default` 和 `safeguard`，没有 `manual` 模式。

### ❌ 误区 2：`inferenceParams.temperature/top_p/thinkingMode`

**官方说明：** 配置文档中没有这些参数路径。这些可能是：
- 自定义配置
- 模型提供商的参数
- 或者是通过其他方式传递的

### ❌ 误区 3："MiMo 模型本身消耗高"

**官方说明：** Token 消耗取决于注入的内容，不是模型本身。同样的配置下，不同模型的消耗差异不会太大。

### ❌ 误区 4："安装技能可以自动优化 Token"

**官方说明：** 没有提到任何可以自动优化 Token 的技能。优化需要手动调整配置。

---

## 七、推荐配置

### 基于官方文档的优化配置

```yaml
agents:
  defaults:
    # 压缩配置
    compaction:
      mode: "safeguard"
      reserveTokens: 16384
      keepRecentTokens: 15000
      reserveTokensFloor: 10000
      truncateAfterCompaction: true
    
    # 上下文限制
    contextLimits:
      toolResultMaxChars: 8000
      memoryGetMaxChars: 8000
      memoryGetDefaultLines: 100
    
    # 启动注入限制
    bootstrapMaxChars: 15000
    bootstrapTotalMaxChars: 45000
    
    # 图片处理
    imageMaxDimensionPx: 800
    
    # 缓存优化
    heartbeat:
      every: "55m"
    models:
      "mimo/mimo-v2.5":
        params:
          cacheRetention: "long"
```

---

## 八、快速检查清单

- [ ] 检查 `compaction.mode` 是否为 `safeguard`
- [ ] 检查 `bootstrapTotalMaxChars` 是否过大（建议 45000）
- [ ] 检查 MEMORY.md 大小（建议 <5000 字符）
- [ ] 检查 `toolResultMaxChars` 是否过大（建议 8000）
- [ ] 使用 `/context detail` 查看上下文组成
- [ ] 使用 `/usage full` 监控每次回复的 token 消耗
- [ ] 定期使用 `/compact` 手动压缩

---

## 九、参考文档

- [Token use and costs](/reference/token-use)
- [Session management + compaction](/reference/session-management-compaction)
- [API usage and costs](/reference/api-usage-costs)
- [Prompt caching](/reference/prompt-caching)

---

## 变更记录

| 日期 | 变更内容 | 操作人 |
|------|---------|--------|
| 2026-06-14 | 初始版本（基于官方文档） | 小桉 |
