# 开发者 评审发现

> 评审角色：开发者（逐文件精读，聚焦「测试未覆盖路径」：边界条件、错误处理、资源泄漏、并发安全、硬编码、异常吞掉）
> 评审范围：core / extractors / layers.l1 / layers.l2 / layers.l3 / service / web 共 17 个核心实现文件
> 基线：tests/test_full.py 48/0（已通过，本角色不修改代码，仅产出 findings）

---

## P0 功能断链

### P0-1 L3 语义检索吞掉所有异常，语义层"看起来像死了一样"
- [omnifind/core/router.py:300-301] `try: l3_raw = self.l3.search(...) except Exception: pass`
- 现象：当 L3 模型加载超时 / onnxruntime 报错 / 向量库损坏时，`semantic` 单模式与 `all` 模式下语义检索**静默返回空结果**，且 `counts["l3"]=0`、`semantic_disabled` 也不会置位（`semantic_disabled` 仅在 `self.l3 is None` 时设置，见 :370-371）。前端无法区分"真的没命中"与"语义层崩了"，用户会以为语义搜索是坏的。
- 测试覆盖：test_full 不涉及 L3 失败路径，此分支完全未测。
- 建议：捕获具体异常（或至少 `logging.exception`），在 `counts` 中写入 `l3_error` 标记并让前端给出"语义层暂时不可用"提示；单 `semantic` 模式失败时降级为明确错误而非空列表。

### P0-2 L3 语义构建无单文件体积上限，配合"全盘语义"配置必然 OOM / 构建中止
- [omnifind/layers/l3_semantic/builder.py:38-58] `build_semantic_index` 对命中 `semantic_exts` 的文件**直接 `extract_text(Path(p))`**，没有像 L2 那样的体积闸门。
- 对照：[omnifind/core/indexer.py:30] `max_bytes = cfg.max_fulltext_mb * 1024 * 1024` 与 :53 `if st.st_size > max_bytes: continue`（L2 有保护，L3 没有）。
- 致命组合：本仓库 `config.local.yaml:63-64` 为 `semantic_dirs: []` + `semantic_full_disk: true`，`resolve_semantic_roots` 会回退到 `scan_roots`（C:\ D:\ G:\ 三块全盘）。于是构建会尝试**读取并对整个三块盘的可读文档做向量化**，任一超大文件（如几百 MB 的 PDF/日志）在 `extract_text`/`encode` 阶段直接吃光内存，进程 OOM 被杀死，语义索引永远建不完。
- 建议：在 `build_semantic_index` 增加 `max_semantic_mb`（复用 `cfg.max_fulltext_mb` 或新增项）按 `st.st_size` 跳过；并加每 N 篇的进度日志（L2 有 `REPORT_INTERVAL`，L3 没有）。

### P0-3 USN 后端把整卷 MFT 全量载入内存，大容量卷直接 OOM
- [omnifind/layers/l1_filename/usn_backend.py:381-383] `scan_volume` 先把**全部** USN 记录收进 `records: dict[int, dict]`，再二次遍历拼路径。
- 现象：这是 Windows 下 `l1_backend=usn`（auto 模式下 Windows 首选）构建 L1 的主路径。系统卷动辄数百万文件，该 dict 持有每个文件的 name 字符串 + 元数据，内存可达数 GB；本机无法验证（非 Windows/SYSTEM），属于测试盲区，但上线即撞内存墙。
- 建议：改为流式拼路径（用 parent 指针递归解析，配合小容量 path_cache），或分批 yield，避免一次性全量驻留；至少对单卷记录数做上限保护。

---

## P1 错误行为

### P1-1 配置文件默认 data_dir 落在 `%ProgramData%`，非管理员开箱即失败
- [omnifind/core/config.py:36-41] `default_data_dir()` 在 Windows 返回 `%ProgramData%\omnifind`；[config.py:157] 又把它当作系统级覆盖配置目录。
- 现象：普通用户（非 SYSTEM / 非管理员）运行 `ensure_dirs` 建目录会抛 `PermissionError`，Web/服务起不来。本仓库 `config.local.yaml` 并未设置 `data_dir`，所以默认即走 ProgramData —— 开发机（当前用户 `13871`）直接跑会断链。
- 建议：默认 data_dir 在开发态退化为项目内 `./data` 或用户目录 `~/omnifind`，仅打包态/服务态才用 ProgramData；或在 `ensure_dirs` 失败时给出可读的修复提示（去哪改 `data_dir`）。

### P1-2 Office 抽取器异常路径不关句柄（资源泄漏）
- [omnifind/extractors/office.py:32,40] `XlsxExtractor` 用 `wb = openpyxl.load_workbook(...)` 后仅在正常末尾 `wb.close()`；若 `iter_rows` 中途抛异常（损坏/加密 xlsx），`wb.close()` 不执行，只读 zip 句柄泄漏。
- [omnifind/extractors/office.py:51] `PptxExtractor` 的 `prs = Presentation(str(path))` 全程无 `close()`。
- 建议：统一用 `with`（`openpyxl.load_workbook(...) as wb:`；`python-pptx` 也支持 `prs.close()`），保证异常路径释放。

### P1-3 `web/server.py` 直接裸调 `conn.execute` 绕过索引锁
- [omnifind/web/server.py:590] `l2.conn.execute(...)`、[server.py:599] `l1.conn.execute(...)`（在 `_verify_path_in_index` 中）绕过了 `FilenameIndex`/`FullTextIndex` 的 `self._lock`。
- 现象：后台 `_run_build` 正在 `l2.clear()`/批量 `bulk_upsert`（持锁写）时，并发的 `/api/preview`、`/api/file/info` 直接读 conn，高争用下抛 "database is locked"；而该异常的捕获是 `except Exception: pass`（[server.py:595]、[server.py:604]），导致已索引文件被误判为"不在索引"返回 403。
- 建议：复用索引对象提供的只读接口（如新增 `FilenameIndex.exists(path)` / `FullTextIndex.exists(path)` 方法，内部加锁），不要在 web 层直接碰 `conn`。

### P1-4 `usn_backend.watch()` 初始化开卷在 try 之外，单卷不可用即拖垮整个服务进程
- [omnifind/layers/l1_filename/usn_backend.py:535] `handles = {letter: _open_volume(letter) for letter in volumes}` 位于 `try:`（:536）之前；若任一卷 `_open_volume` 抛 `OSError`（权限/脱机），异常直接逃出 `watch()`。
- 承接：[omnifind/service/indexer_service.py:87-98] `backend.watch()` 只捕获 `KeyboardInterrupt` / `NotImplementedError`，其它异常一路冒泡到 `main()` 未捕获 → 服务进程崩溃。
- 建议：把 `handles` 构建放进 try；单个卷失败应跳过该卷并记 warning，而非整体退出；watch 外层应包一层 broad `except Exception` 并自转重试（与 L3 watcher 的 `except Exception: logger.exception` 对齐）。

### P1-5 `indexer_service` 中 `build_filename_index` 失败（显式 usn 模式）会让服务崩溃
- [omnifind/service/indexer_service.py:74] `build_filename_index(cfg, l1)` 在 `try:` 块内，但其 `finally` 只 `l1.close()`；当 `UsnBackend.build` 触发 [usn_backend.py:476-477] `raise OSError("USN 扫描全部卷失败")` 时，异常逃出 `main()` 未捕获。
- 场景：`l1_backend=usn`（非 auto）且权限不足时 100% 崩溃；auto 模式因先 `probe_usn_available` 已回退 walk 而幸免。
- 建议：`main()` 对 build 阶段加 `except Exception` 转日志并优雅退出；或显式 `usn` 模式失败时回退 walk 而非抛。

### P1-6 `_is_text_file` 用 `read_bytes()` 整文件读入内存（预览大文件 OOM）
- [omnifind/web/server.py:452] `raw = p.read_bytes()` 读取**整个文件**，随后才 `chunk = raw[:min(8192, len(raw))]` 取前 8KB 判定文本。
- 现象：对任意扩展名不在 `_TEXT_EXTS` 的文件（如 `.bin`/`.dat`/超大无扩展名文件），`preview` 在 [server.py:409] 调用 `_is_text_file` 会先把整文件读进内存；一个 5GB 文件即可撑爆服务进程。文本分支本身有 `fsize > _MAX_PREVIEW_SIZE*10` 预检（:421），但预检发生在 `_is_text_file` **之后**，届时已读完全文。
- 建议：`_is_text_file` 改用 `with p.open("rb") as f: chunk = f.read(8192)` 只读头部；并把它提到 size 预检之后。

---

## P2 体验缺陷

### P2-1 配置了但无抽取器的扩展名被静默跳过，造成"能搜到"的错觉
- [config.local.yaml:23-29] `fulltext_exts` 含 `.doc` / `.ppt` / `.xls` / `.epub`，但 [omnifind/extractors/office.py] 只注册 `.docx`/`.xlsx`/`.pptx`，`.epub` 无任何抽取器。
- 现象：`build_fulltext_index`（indexer.py:45 `if ext not in exts or get_extractor(ext) is None: continue`）直接跳过，用户却以为这些类型可搜；`.xls` 在预览端还能用 `xlrd` 看（server.py:501），但全文/语义完全不可搜，前后不一致。
- 建议：构建结束打印"已配置但无抽取器被忽略的扩展名"清单；或把这些扩展名从默认 `fulltext_exts` 移除以免误导。

### P2-2 `plaintext` 抽取器 latin-1 兜底使"解码失败"分支形同虚设
- [omnifind/extractors/plaintext.py:22-29] 依次试 utf-8/gb18030/utf-16，最后 `latin-1` 兜底；`latin-1` 对任意字节都能解码，故 `return ExtractResult(ok=False, ...)` 几乎不可达。
- 副作用：扩展名属于文本类但实际是二进制（如伪装成 `.py` 的 elf）会被当作 latin-1 乱码建索引。
- 建议：latin-1 兜底前先做一次"是否含 NUL / 控制字符占比"的启发式判定，疑似二进制则 `ok=False` 并给出原因。

### P2-3 L2 `count_match_grouped` 用 f-string 拼 SQL（注入风险/脆弱写法）
- [omnifind/layers/l2_fulltext/index.py:248] `SUM(CASE WHEN LOWER(ext) = '{e}' ...)` 直接插值 `e`。
- 现状：`e` 来自 router 的 `FACET_EXTS` 固定白名单，暂不可被用户操控；但属于不安全模式，一旦有人把用户传入的 ext 喂进来即 SQL 注入。
- 建议：改用参数化占位（`?`）并传参，杜绝字符串拼接。

### P2-4 `SemanticIndex` 增/删用 f-string 拼 path SQL（虽已转义，仍脆弱）
- [omnifind/layers/l3_semantic/index.py:99] `self._table.delete(f"path = '{path.replace(chr(39), chr(39)*2)}'")`；[:143] 同理。
- 现状：`'` 复写转义在 SQLite 字符串字面量下安全，但依赖手工转义、易回归。
- 建议：优先用 LanceDB 参数化删除 API（如 `table.delete(predicate)` 的绑定形式或先 `to_pandas` 取 id 再按主键删）。

### P2-5 后台构建状态 `_index_task` 跨线程无锁读写
- [omnifind/web/server.py:244-307] `_run_build`（daemon 线程）写 `progress/total/message/error`，`/api/index/status`（另一线程）读同一 dict，仅 `running` 标志用了 `_rebuild_lock`。
- 现象：极端情况下状态字段处于半写状态；当前 JSON 序列化通常原子，风险低但属隐患。
- 建议：用 `threading.Lock` 或 `dataclasses` + 不可变快照，避免共享可变状态裸读写。

### P2-6 `open_file`/`reveal_file` 对含空格路径的 explorer 调用
- [omnifind/web/server.py:642] `subprocess.run(["explorer", "/select,", str(resolved)])`：路径含空格时被拆成两个参数，explorer 可能无法正确选中文件。
- 建议：用 `os.startfile(str(resolved))` 统一走系统关联，或用 `subprocess.run(["explorer", f"/select,{resolved}"])` 合成单个参数。

### P2-7 L1/L2 `close()` 存在但 `SemanticIndex` 无 `close()`，Web 生命周期未关闭 L3
- [omnifind/web/server.py:83-84] lifespan `finally` 只 `l1.close()`、`l2.close()`；`l3`（`SemanticIndex`/LanceDB）与 embedder 的 onnx session 从不关闭。
- 现状：长驻服务影响不大，但单测/脚本中频繁 new `SemanticIndex` 会残留 `-wal/-shm` 锁文件与句柄。
- 建议：给 `SemanticIndex` 加 `close()` 并在 web lifespan 调用；LanceDB 显式 `close`（若版本支持）。

### P2-8 `update_config` 改了 `l1_backend` 等运行态字段却不触发后端重建/重启
- [omnifind/web/server.py:198-204] `setattr(cfg, k, v)` 后仅 `semantic_min_score` 同步到实例；`l1_backend`、`scan_roots` 等改动写盘后在下次重启才生效，但接口返回 `ok:True` 让人误以为即时生效。
- 建议：对"需重启生效"的字段在返回里注明 `restart_required: true`，或显式拒绝热改。

---

## P3 优化建议

- [omnifind/extractors/__init__.py:73-74] `extract_text` 的 `except Exception` 吞掉所有异常且无日志，开发期排错困难；建议至少 `logging.debug("extractor %s failed: %s", type(ex).__name__, e)`。
- [omnifind/core/config.py:200] 模块导入即 `OmniConfig.load()`（`_default_cfg = OmniConfig.load()`），把文件 I/O 作为 import 副作用；建议改为惰性 `DATA_DIR` 解析，避免导入即触盘。
- [omnifind/core/router.py:24,37] `COUNT_CAP=5000`、`REPORT_INTERVAL=500`、[usn_backend.py:205] `scan_cap=200000`、watch 轮询 `time.sleep(2)` 等为魔力数字，建议提到 config 或模块常量并加注释。
- [omnifind/core/router.py:84,95] `parse_query` 每次搜索都 `import re`、每次 `re.search`，可提到模块顶层预编译。
- [omnifind/layers/l3_semantic/watcher.py:54-83] 每轮对 `semantic_dirs` 做全量 `os.walk`；若目录巨大且 `interval` 小，CPU 占用偏高，可考虑基于 `os.scandir` 增量 + mtime 索引。
- [omnifind/layers/l3_semantic/embedder.py:50-92] `dim` 属性触发 `_lazy_load` 加载整模型（[web/server.py:50] 启动期即 `emb.dim`），可改为从配置文件/常量读维度，避免启动即加载大模型。
- [omnifind/core/indexer.py:37] `REPORT_INTERVAL = 500` 写死；L3 builder 完全无进度输出（P0-2 已提），建议统一进度上报抽象。

---

## 亮点

- **USN 路径拼接防坑**：[usn_backend.py:394-405] 对卷根返回带尾分隔符的 `C:\`，避免 `os.path.join("C:", "Users")` 退化成 `C:Users` 导致全盘 0 条 —— 防御性代码到位。
- **LIKE 转义严谨**：[l1_filename/index.py:80-89, 75] `_escape_like`/`_build_like` 用 `ESCAPE '\'` 正确处理 `%`/`_`，避免 `a_b` 误中 `axb`；同逻辑也用于 FTS5 失败回退的 LIKE。
- **FTS5 查询安全**：[l2_fulltext/index.py:28-45] 每个 jieba token 双引号包裹，杜绝 `.@-()：` 等触发的 FTS5 语法错与注入。
- **SQLite 并发设计**：L1/L2 均启用 `WAL + synchronous=NORMAL + busy_timeout=5000 + 每实例锁`，读多写少场景稳。
- **L3 幂等**：[l3_semantic/index.py:98-102] `add_document` 先按 path 删旧 chunk 再插入，天然支持重建/增量覆盖。
- **服务韧性**：[service/indexer_service.py:64-98] 信号优雅退出、`NotImplementedError` 后端回退"30 分钟定时全量重建"，Windows `SIGBREAK` 也接住。
- **预览安全与兜底**：[web/server.py:345-446] 先校验文件在索引内、再 `stat` 大小硬顶、多 PDF 库（fitz→pdfplumber→PyPDF2）逐级回退，且不在响应里回吐原始库错误（防信息泄漏）。
- **配置健壮性**：[config.py:147-187] 配置损坏时按默认降级、派生只读字段跳过、空列表视为用默认、bool/int 类型错位告警，不拖垮启动。

---

## 可执行优先级建议（给后续修复）

1. **立刻修（P0）**：P0-1（L3 异常别吞）、P0-2（L3 加体积闸门，且重新审视 `semantic_full_disk:true`+空 `semantic_dirs` 的默认安全性）、P0-3（USN 流式化）。
2. **本迭代修（P1）**：P1-1（data_dir 默认改开发友好）、P1-2（Office 抽取器 `with` 关句柄）、P1-3（web 别裸碰 conn）、P1-4/P1-5（watch/build 异常兜底）、P1-6（`_is_text_file` 只读头部）。
3. **顺手清理（P2/P3）**：P2-2 latin-1 二进制误判、P2-3/P2-4 SQL 参数化、P2-7 `SemanticIndex.close()`、P3 日志与魔力数字。
