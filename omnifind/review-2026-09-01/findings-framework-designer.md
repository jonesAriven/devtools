# 框架设计师 评审发现

> 评审角色：框架设计师（模块边界 / 抽象质量 / 依赖方向 / 可测试性 / 命名一致性 / 配置系统）
> 范围：omnifind 包全部 `__init__.py`、`core/{config,router,indexer}.py`、`extractors/*`、`layers/*`、`web/server.py`、`service/indexer_service.py`
> 方法：静态通读 + 依赖方向梳理（未改代码）

## 依赖方向速览（先给图，便于定位下面的断链）

```
                配置系统           路由
          core/config.py ──▶ core/router.py (QueryRouter)
                │
  应用/组合根 ──┤  core/indexer.py ──▶ layers.l1_filename / layers.l2_fulltext  ✗ 越界(下详)
  (__main__ /   │                        │
   web / service)               extractors/ (__init__ 注册表)
                │                        │
                └──▶ layers.l3_semantic/{builder,index,embedder,watcher}

  web/server.py ──▶ core.config / core.router / layers.* / extractors.*
```

问题点：
1. `core`（本应是稳定的底层：config + router）却 **向下依赖** `layers.l1/l2` 与 `extractors`（`core/indexer.py:11-13`）—— 越界，core 不再是"底层"。
2. `web/server.py` 既依赖 `extractors` 的**概念**，又**绕过**它直接重写抽取逻辑（见 P0-1）。
3. `omnifind.core.config` 被 `layers.l1/l2/l3` 以 `from ... import DATA_DIR` 方式在**模块导入期**耦合（见 P1-1/P1-2），形成"导入即读盘"的硬约束。

---

## P0 功能断链

- [web/server.py:340,377-406,462-574] 与 [extractors/__init__.py:77] —— **抽取器注册表抽象被 web 层切断，扩展契约失效**。
  `web/server.py` 的 `/api/preview` 路径**从不调用** `load_all_extractors()`，而是硬编码 `_DOC_EXTS={.docx,.xlsx,.xls}` 并自写 `_extract_document_text`(462-520) / `_extract_pdf_text`(523-574)。后果：
  (1) 在 `extractors/` 注册的新格式（如 `.pptx`）**预览自动失效**，跌入 `_is_text_file` 被判为二进制 → "不支持的格式"，注册表承诺的"加格式零改动"在预览链断裂；
  (2) 索引正文（`extractors/office.py` 用 `\n` / `\t` 拼接）与预览正文（`server.py` 用 `\n\n` / ` | ` 拼接）**同源不同文**，同一文件搜索结果与预览内容不一致。
  → 建议：删除 `server.py` 内 `_extract_document_text`/`_extract_pdf_text`/`_DOC_EXTS`/`_TEXT_EXTS` 这套私有抽取，统一改成 `from omnifind.extractors import extract_text, load_all_extractors`；`lifespan` 启动时调一次 `load_all_extractors()`，预览/打开只走注册表。新增格式只需在 `extractors/` 放一个 `@register` 类。

- [extractors/__init__.py:43-58,77-85] 与 [core/indexer.py:27],[layers/l3_semantic/builder.py:31],[layers/l3_semantic/watcher.py:46] —— **注册表"发现"是隐式契约，断链风险**。
  `_REGISTRY` 初始为空，`extract_text`/`get_extractor` 在 `load_all_extractors()` 被调用前会静默返回 `ExtractResult(ok=False, error="no extractor for ...")`。目前只有 indexer / l3 builder / l3 watcher 三处触发了发现，但**模块对外曝光了顶层 `extract_text` 却要求调用方先做隐藏的"初始化步骤"**。任何新调用方（新 API、测试、CLI 子命令）漏掉这步就会静默无结果。
  → 建议：把发现改成**惰性且自举**——`get_extractor`/`extract_text` 首次被调用时若 `_REGISTRY` 为空自动 `load_all_extractors()` 一次（用模块级 `_discovered` 标志防重复）。这样"导入即用"，契约不再悬空。

## P1 错误行为

- [omnifind/core/config.py:200-203] 与 [config.py:44-56] 与 [layers/l1_filename/index.py:19],[layers/l2_fulltext/index.py:16],[layers/l3_semantic/index.py:13] —— **import 即读盘的模块级副作用（ANALYSIS #4 确认，此处定位根因）**。
  `config.py:200` 在模块导入时执行 `_default_cfg = OmniConfig.load()`；`load()` 内 `cfg = cls()`(149) 触发 `scan_roots` 的 `default_factory=default_scan_roots`(44-56)，在 Windows 上直接 `ctypes.windll.kernel32.GetLogicalDrives()`(50) 枚举盘符。于是 `import omnifind.layers.l1_filename.index` 因 `from omnifind.core.config import DATA_DIR`(19) 而**连带触发读盘 + ctypes 系统调用**。结论：layers / config 无法在隔离环境做单测，必须先有真实文件系统与 Windows，可测试性被破坏。
  → 建议：删除 `config.py:198-203` 的模块级 `OmniConfig.load()`；`DATA_DIR` 等改为**由调用方注入**，或在 Index 构造时显式传 `db_path`/`cfg`。`default_scan_roots` 里的 `ctypes` 调用移出模块顶层，仅在 `OmniConfig()` 真正实例化时执行，且允许测试用 `monkeypatch` 覆盖 `OmniConfig` 字段而非整个模块。

- [omnifind/core/config.py:198-203] 与 [layers/l1_filename/index.py:19],[layers/l2_fulltext/index.py:16],[layers/l3_semantic/index.py:13] —— **两份数据源（frozen 全局常量 vs dataclass 属性）并存，易过时**。
  `DATA_DIR/MODELS_DIR/LOGS_DIR` 在 import 时由 `_default_cfg` 冻结一次；而 `OmniConfig` 对象上还有 `db_dir/models_dir/logs_dir` 属性。运行时若 `data_dir` 经任何路径被覆盖（测试 fixture、未来热更新、多实例），frozen 全局不会跟着变，索引会写进"旧路径"而 `cfg` 显示"新路径"。
  → 建议：彻底删除这三个模块常量；所有消费点改为接收 `cfg` 或显式 `db_path`。Index 类默认 `db_path=None` 时由传入 `cfg.data_dir_path` 推导，而非读全局。

- [omnifind/web/server.py:27,244-290,228-238,295-307] —— **`_index_task` 状态字典跨线程读写无同步（数据竞争）**。
  `start_build`(293-307) 仅用 `_rebuild_lock` 保护 `running` 标志的翻转；工作线程 `_run_build`(244-290) 随后写 `progress/total/message/error` 全程**无锁**，而 `/api/index/status`(228-238) 并发读同一字典。多线程下会出现撕裂读 / 状态字段读到半更新值。
  → 建议：用一个 `IndexTaskStatus` dataclass + 专用 `threading.Lock` 包裹所有字段读写；或改用 `queue.Queue` 上报进度。`running` 的原子性应与其它字段共用同一把锁。

- [omnifind/web/server.py:585-605] —— **泄漏抽象：web 层直接戳各 Index 的原始 SQLite 连接**。
  `_verify_path_in_index` 执行 `l2.conn.execute("SELECT 1 FROM files ...")` 与 `l1.conn.execute("SELECT 1 FROM entries ...")`，绕过 Index 提供的封装。一旦某层更换存储（如 L3 已是 LanceDB），该方法即失效；也破坏了"连接属于 Index 内部"的封装。
  → 建议：在 `FilenameIndex`/`FullTextIndex` 上各加 `contains(path: str) -> bool` 方法，`_verify_path_in_index` 改为调用它，web 层不再接触 `conn`。

## P2 体验缺陷

- [core/indexer.py:39-76] 与 [layers/l3_semantic/builder.py:38-57] 与 [layers/l3_semantic/watcher.py:54-99] —— **"遍历根目录→按扩展名过滤→get_extractor→extract_text→upsert" 三段几乎相同的爬取循环被复制 3 次**。
  索引 L2、构建 L3、L3 增量监听各写一遍，logic drift 风险高（例如三处对 `extract_text` 失败的处理、`exclude_dirs` 过滤细节如不一致就出 bug）。
  → 建议：抽一个中性的 `omnifind/core/crawler.py: iter_documents(roots, exts, exclude)`，返回 `(path, name, mtime, text, title)` 生成器；三处只写"拿到文档后做什么"。放在 `core` 而非 `layers`，因为它只依赖 `extractors` + `config`，不依赖具体层。

- [core/indexer.py:11-13] —— **`core` 向下依赖 `layers` 与 `extractors`，边界倒置**。
  按项目结构 `core=config/router/indexer` 应是最底层稳定件，但 `core/indexer` import 了 `layers.l1_filename`、`layers.l2_fulltext`、`extractors`。同时构建入口也**不对称**：L1/L2 的 `build_*` 是 `core/indexer.py` 的模块函数，L3 的 `build_semantic_index` 却在 `layers/l3_semantic/builder.py`。
  → 建议：把"编排索引"的 `indexer.py` 上移为 `omnifind/pipeline.py`（组合根层），让 `core` 只留 config + router（真正无依赖的底层）。构建函数统一收口到 `pipeline` 或各自层的 `builder`，命名一致（`build_filename_index`/`build_fulltext_index`/`build_semantic_index` 同源）。

- [core/config.py:61-63] 与 [extractors/office.py:7-61] —— **`DEFAULT_SEMANTIC_EXTS` 声明了 `.doc/.xls/.ppt` 但注册表没有对应抽取器**。
  配置"声称"支持旧版 Office 格式，但 `extractors/office.py` 只注册了 `.docx/.xlsx/.pptx`；`build_fulltext_index`(core/indexer.py:45) 与 `builder.py:43` 因 `get_extractor(ext) is None` 静默跳过这些文件，用户看到"配了却搜不到"。
  → 建议：二选一——(a) 从 `DEFAULT_SEMANTIC_EXTS` 移除 `.doc/.xls/.ppt`；(b) 补 `legacy office` 抽取器（或用 `textract`/反编译）。至少先在配置注释标明"仅 docx/xlsx/pptx"。

- [extractors/__init__.py:52] —— **`register` 的 PRIORITY 平局用 `>=` 判定，最后导入者覆盖前者（last-import-wins）**。
  `if existing is None or extractor_cls.PRIORITY >= type(existing).PRIORITY`：同一扩展名、同一 PRIORITY 的两个抽取器，谁在 `pkgutil` 遍历中靠后谁胜出，顺序依赖文件系统枚举序，跨机器可能不确定。
  → 建议：平局时改为显式决策（如按注册顺序首胜，或要求 PRIORITY 唯一并在 `register` 内对冲突报 `ValueError`），避免隐式顺序依赖。

- [core/config.py:171-186] —— **`OmniConfig.load()` 缺少类型校验**。
  仅对"空列表"和"bool 当 int"做了保护；若 yaml 把 `scan_roots: "C:\\"`（字符串）或 `port: "8899"`（字符串）写错，`setattr` 会原样写入，`for root in cfg.scan_roots` 直接 `TypeError`，或 uvicorn 收到字符串端口。
  → 建议：加载后用 `dataclasses.fields` 做一次 `isinstance` 兜底校验，失败给出字段级报错而非把坏值带进运行时。

- [layers/l1_filename/usn_backend.py:436] 与 [layers/l1_filename/index.py:305] —— **`usn_backend` ↔ `index` 的潜在导入环**。
  `usn_backend.py:436` 在**模块顶层** `from omnifind.layers.l1_filename.index import FilenameBackend`（包了 try/except 兜底为 `object`）；`index.py:305` 的 `make_backend` 再 lazy import `usn_backend`。当前因 usn 的导入发生在 index 已完整加载之后而**侥幸不环**，但属于脆弱结构：一旦有人在 `index.py` 顶层 import `usn_backend` 就成真环。
  → 建议：把 `FilenameBackend` ABC 抽到独立的 `layers/l1_filename/contracts.py`，`usn_backend` 与 `walk` 都从 `contracts` 继承；两边 backend 导入保持 lazy，彻底解除环。

## P3 优化建议

- [layers/l1_filename/index.py:22 NameHit] / [layers/l2_fulltext/index.py:48 FtsHit] / [layers/l3_semantic/index.py:16 SemanticHit] / [core/router.py:32 UnifiedHit] —— **命中结果类命名不一致**（Name/Fts/Semantic/Unified 混用）。建议统一为 `*Hit`（或 `*Result`），前端/路由处理时心智更一致。

- [core/router.py:157-189] —— **跨层打分权重（100/90/80/60 + 50 + 70）硬编码在 `_sort_hits`**。相关性调参逻辑泄漏进路由核心，且三层量纲不同（l1 名次分、l2 bm25、l3 余弦）混在一处。建议抽到 `core/scorer.py` 或下沉到各层 `hit.score` 归一化，router 只负责聚合。

- [extractors/__init__.py:48] —— **`register` 在注册期即 `extractor_cls()` 实例化**（尽管抽取器无状态）。无功能 bug，但实例化副作用提前、且对"未来有状态的抽取器"不友好。建议改为懒实例化（首次 `get_extractor` 时建实例并缓存）。

- [web/server.py:24,54,100,131,149,246,585] —— **`_state` 全局字典 vs `QueryRouter` 依赖注入不一致**。router 是注入的，但其依赖 `cfg/l1/l2/l3/watcher` 全塞进模块全局 `_state`，端点到处 `_state["..."]`。建议引入 `AppState` dataclass（`cfg, l1, l2, l3, router, watcher, index_task`），在 `lifespan` 构造并显式传给依赖（或在 FastAPI `app.state` 上挂单一实例），便于单测用 fake 替换。

## 亮点

- **抽取器注册表模式本身设计干净**（`extractors/__init__.py:24-86`）：`BaseExtractor` ABC + `@register` + `PRIORITY` + `load_all_extractors` 自动发现，新格式"加类即生效"的方向是对的——问题只在被 web 层绕过（见 P0-1），而非模式本身。
- **`FilenameIndex` 存储与后端契约分离**（`layers/l1_filename/index.py:228 FilenameBackend` ABC + `WalkBackend`/`UsnBackend`）：USN/walk 可插拔，`make_backend` 做平台探测回退，是良好的策略模式实践。
- **配置三层优先级概念正确**（`config.py:147-187`）：打包默认 < 本地覆盖 < 系统级，且损坏配置降级为默认不拖垮启动——设计意图合理，仅"import 即读盘"的实现损害了可测试性（见 P1-1）。
- **并发处理稳妥**：各 Index 均 `WAL + busy_timeout=5000 + threading.Lock + check_same_thread=False`（`l1 index.py:42-44` 等同构），实测高并发零错，是扎实的基类做法。
- **`QueryRouter` 统一查询语法 + `SearchResponse` 统一结构**（`core/router.py:1-55`）：前端无需感知层来源，`auto` 模式三层混排与分面计数口径一致，抽象层次清晰。

---

### 一句话总结（可执行路线）
本轮"适度重构"的杠杆点就一个：**让"抽取"与"配置"两件事各只有一个真相源**。
1. 删除 `web/server.py` 内私有抽取，全部走 `extractors` 注册表（P0-1）；
2. 注册表改惰性自举，去掉隐式初始化契约（P0-2）；
3. 删除 `config.py` 模块级 `OmniConfig.load()` 与 `DATA_DIR` 全局，Index 改为注入 `cfg/db_path`（P1-1/P1-2）；
4. 给 `_index_task` 加锁、给 Index 加 `contains()`（P1-3/P1-4）。
这 4 步不动业务行为，只收口抽象与依赖，即能同时修掉可测试性、泄漏抽象与扩展断链三处核心问题。
