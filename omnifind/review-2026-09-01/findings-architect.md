# 架构师 评审发现

> 评审范围：11 个指定的核心源文件（router / indexer / config / l1 index + usn_backend / l2 index / l3 builder + embedder + index + watcher / indexer_service）+ web/server.py 重建入口 + __main__.py 构建入口 + docs/ANALYSIS-2026-07-27.md 基线。
> 口径说明：基线测试 `tests/test_full.py` 按任务说明为 48/0（本轮未执行，评审只改文档不改代码）；ANALYSIS 文档为 7/27 版本，8/30 有重构，下文对其中已修复项做了核对标注，未修复项按当前代码重新确认。
> 严重度：P0=功能断链（设计上就产出错误/空/损坏结果）；P1=错误行为（明确 bug，但非必崩）；P2=体验缺陷；P3=优化建议。

---

## P0 功能断链

- [web/server.py:254,263,280] **rebuild 先删后建，期间该层搜索结果为空且客户端无感知**。
  `_run_build` 对 `l1`/`l2`/`l3` 都是先 `clear()`/`drop()` 再 `build_*`，而 build 是长任务（全盘可达分钟~小时级）。在窗口期内该层返回空/部分结果，但 `/api/search` 与 `SearchResponse` 都没有"正在重建"标志，前端只显示"0 条/少量结果"，用户误以为索引丢了或搜不到。
  → 建议：① 双缓冲——构建到临时 db/表，完成后原子 rename/swap；② 至少在 `SearchResponse.counts` 与 `/api/status` 暴露 `indexing{layer:bool}`，前端给出"索引重建中，结果为旧快照/部分"提示。

- [core/indexer.py:60 + layers/l2_fulltext/index.py:95-112 + layers/l2_fulltext/index.py 全文缺失 remove + service/indexer_service.py:74 + __main__.py:32-34] **L2 全文索引无法清除已删除/重命名的文件，且"全量重建"语义在两条入口不一致 → 索引永久失真、无法自纠正**。
  `FullTextIndex` 只有 `clear()`，**没有 `remove_document(path)`**；`upsert_document` 只做 INSERT/ON CONFLICT UPDATE（index.py:101）。文件改名 → 新 path 插入、旧 path 行与对应 fts 行**永久残留**；文件删除 → 无任何路径删它。同时 `build_filename_index`/`build_fulltext_index` 自身不清旧（它们依赖调用方 clear），而 `--full`（indexer_service.py:74）与 CLI `omnifind index`（__main__.py:32-34）都**不调 clear**，只有 web 重建（server.py:254/263）才 clear。于是"--full"实为 merge 而非重建，删除的文件依旧在索引里。
  → 建议：① 补 `FullTextIndex.remove_document(path)`（删 `files` 行 + 对应 `fts` 行，建外键或触发器保证一致性）；② 让 build 支持"先 clear 再建"开关，`--full` 默认走 clear；③ 重命名场景在 L1 watch 拿到旧路径时用 `remove_document` 同步 L2。

- [layers/l3_semantic/builder.py:21-25,28-58 + __main__.py:35-47] **L3 安全阀门只在 web 层实现，builder 与 CLI 缺失 → 默认配置下 `python -m omnifind index` 对全盘向量化（爆库）**。
  builder.py 的 docstring（8-9 行）声称有"未配置 semantic_dirs 且 semantic_full_disk=False 时要求用户显式圈定"的阀门，但 **`build_semantic_index` 函数体里完全没有该检查**；`resolve_semantic_roots`（builder.py:21-25）在 `semantic_dirs` 为空时直接返回 `scan_roots`（所有盘）。阀门仅存在于 web/server.py:273-276。CLI `__main__.py:47` 直接调用 `build_semantic_index(cfg, sem)`，默认配置即把整盘可读文档灌进 LanceDB，违反"避免全盘向量化爆库"的核心设计约束。
  → 建议：把阀门下移到 `build_semantic_index` 内部（进入前若 `not semantic_dirs and not semantic_full_disk` 则 `raise`/返回 0 并告警），web 与 CLI 共用同一道闸。

---

## P1 错误行为

- [layers/l1_filename/usn_backend.py:425] **USN 后端 size 恒为 0（"需要时后置补 GetFileAttributesEx"从未实现）**。
  `scan_volume` yield 时写死 `0,  # 大小 USN 不带`，而 `FilenameIndex` schema 有 `size` 列、WalkBackend 会填（index.py:272）。USN 是 Windows auto 模式默认后端，于是生产环境 L1 条目 size 全 0。后果：① `router.py:198-200` 的 `sort:size` 对 L1 结果全部并列 0、排序失效；② 前端大小列空白。`size` 已是 schema 字段却长期失真。
  → 建议：USN build 收尾对命中条目批量 `GetFileAttributesEx`/os.stat 回填 size；或独立后台回填任务（见 P3-Q）。

- [layers/l1_filename/usn_backend.py:619-625] **USN watch 的删除路径绕过 `FilenameIndex._lock` 且未处理 SQLITE_BUSY，与 web 重建并发可崩溃 indexer 进程**。
  watch 里直接 `self.index.conn.execute(...)` + `self.index.conn.commit()`，没有走 `with self.index._lock`，也没有 `try/except`。`filename.db` 是 web 与 indexer_service **两个进程共享**（WAL + busy_timeout=5000），web 重建（server.py:254 `l1.clear()` + build）与 indexer watch 可能同时写；watch 的 `commit()` 若等不到写锁会在 5s 后抛 `OperationalError`，而 `watch()` 内部 `while True` 没有捕获，异常一路冒泡到 indexer_service `main`（main 只 catch KeyboardInterrupt/NotImplementedError），**整个后台索引进程崩溃**，且 USN state 可能停在中间值。
  → 建议：① watch 删除改走 `self.index.remove(path)`（已加锁、已转义）；② 所有写操作统一经加锁的 `FilenameIndex` 方法，禁止外部直触 `conn`；③ 在 watch 循环顶层已 try/except，但更应让单条 commit 失败不致命。

- [layers/l3_semantic/index.py:108,147-152,62,70] **L3 `drop()`/`_connect()`/`_ensure_table()` 不持锁，与持锁的 `search()`/`add_document()` 存在竞态**。
  `search()`（:108 读 `self._table is None` 在锁外，:113 才加锁）、`drop()`（:147-152 完全无锁，且置 `self._table=None`）、`_connect`/`_ensure_table`（:62/:70 无锁改 `self._db`/`self._table`）。rebuild（`l3.drop()`）与并发 `self.l3.search()` 同进程同线程不同——`drop` 把 `_table` 置 None 后，`search` 已过 None 检查又拿到旧对象去 `.search()` 会抛 LanceDB 异常（被 router 的空 `except` 吞成空结果，但丢异常日志且不稳）；`drop` 与 `add_document`（持锁）并发则 `_table`/`_db` 状态撕裂。
  → 建议：将 `drop`/`_connect`/`_ensure_table` 全部纳入 `self._lock`；`search` 的 `_table is None` 判断移到锁内。

- [layers/l3_semantic/watcher.py:69-70 + web/server.py:64] **SemanticWatcher prime 阶段不向量化，若 L3 未经 build 直接起 watcher，则语义库永久为空**。
  `_scan_once(prime=True)` 只记录 mtime（`if prime: continue`）不抽正文，逻辑前提是"构建阶段已完成"。但 web 仅当 `cfg.semantic_dirs` 且 `l3.count()==0` 才自动 build（server.py:64）；否则只起 watcher。若用户没点过 L3 重建、也没配 semantic_dirs，watcher 一直 prime → 全量语义库永远 0 条，且无任何提示。
  → 建议：watcher 首跑若检测到 `l3.count()==0` 且从未 build，则触发一次 build（或至少告警），不要静默空转；并在前端 status 标注"L3 未构建"。

---

## P2 体验缺陷

- [service/indexer_service.py:69-98 全程] **后台索引服务只维护 L1，从不构建/增量维护 L2 与 L3**。
  `indexer_service` 只 `build_filename_index` + `backend.watch()`（L1）。全局**没有任何 L2 watcher**（grep 仅 `l2.clear`/`build_fulltext_index` 出现在 web 重建与 CLI），语义层 watcher 也只在 web 进程。于是后台常驻服务跑起来后，L2 全文索引只有"手动点重建"才更新，建完即逐步过时，用户感知为"搜不到刚改的内容"。
  → 建议：把 L2/L3 的增量维护也纳入后台服务（复用 `SemanticWatcher` 思路给 L2 做一个轻量 mtime 轮询 watcher），或至少让 indexer_service 在启动/定时兜底时补齐 L2。

- [core/router.py:374 + 344-350] **正则模式 `total` 口径与分层面板数字对不齐**。
  `filename_regex` 模式 `_active_layers` 仅 `{"l1"}`（router.py:394），故 `total = counts["l1"]`（:374）；但 :344-350 又额外算了 `counts["l2"]`。前端若展示 l2 计数而 total 不含它，会出现"l2 有 N 条但共 N 条(仅 l1)"的视觉矛盾。
  → 建议：正则模式下要么也把 l2 计入 active/total，要么不计算 l2 计数，保证 total == 各 activate 层求和恒等式。

- [core/router.py:353-371 + web/server.py] **L3 不可用时 auto 模式搜索无明确前端信号**（部分已做）。
  `router` 在 `self.l3 is None` 时置 `counts["semantic_disabled"]=True`（:370），但仅"semantic/all"模式触发；若 L3 因模型加载失败被置 None，`auto` 模式只静默不出 L3 结果，前端不一定提示。建议 status 与 auto 响应都带 `l3_available`，前端常驻灰显"语义层未启用"。

- [core/config.py:200-203] **`OmniConfig.load()` 模块级副作用，生成 `DATA_DIR`/`MODELS_DIR`/`LOGS_DIR` 模块常量**。
  任意 `import omnifind.core.config` 即读盘、即定死数据目录；各层（index.py:19、l2:16、l3:13）直接用这些常量。多配置/测试场景要 monkeypatch 多处命名空间，且 `data_dir` 改了这些常量不刷新。属架构耦合问题（ANALYSIS 也已记 #4）。
  → 建议：配置对象依赖注入到各层，DB 路径运行时由 `cfg.db_dir` 解析，去掉模块级常量。

---

## P3 优化建议

- [layers/l2_fulltext/index.py:95-112 + core/indexer.py:60] **L2 每文档一次 commit 的性能瓶颈**（任务点名重点）。
  `upsert_document` 每篇执行 INSERT files + SELECT id + DELETE fts + INSERT fts + `commit()`（:112），由 `build_fulltext_index` 每篇调一次（indexer.py:60）→ N 篇 = N 次事务提交。WAL + `synchronous=NORMAL` 已缓解 fsync，但事务开销与 autocheckpoint 频率仍是全盘索引 I/O 瓶颈（ANALYSIS 已记 architect #1）。
  → 建议：新增 `bulk_upsert_documents(rows)`，build 侧累积每 500~1000 篇提交一次；单条 `upsert_document` 保留供增量更新。

- [layers/l1_filename/index.py:94,115] **L1 子串搜索 `LIKE %x%` 全表扫**。
  百万级 ~100ms 可接受，千万级会恶化（ANALYSIS 已记 architect #2）。
  → 建议：文件名入独立 FTS5（trigram/unicode61）或建 `name` 倒排，替代前缀无关的 `%x%` 全扫。

- [layers/l3_semantic/index.py:99-101,142-143] **L3 删除旧 chunk 用字符串拼 SQL + `try/except pass`**。
  `add_document` 内 `_table.delete(f"path = '{path.replace(chr(39), chr(39)*2}'")`（:99）删除失败被静默吞掉 → 旧 chunk 残留、新 chunk 插入，**重复膨胀**（同 path 多份 chunk）。
  → 建议：用参数化 `delete("path = ?", [path])`；删除失败要上报而非 pass，避免静默重复。

- [layers/l1_filename/usn_backend.py:425 根因] **USN size 回填（对应 P1）的可执行方案**：build 收尾把本批 frn→path 的 size 用 `GetFileAttributesEx` 批量取回，或起一个低优先级后台任务对 `size=0` 的条目逐步回填，避免冷启动阻塞。

- [web/server.py:254/263/280 根因] **rebuild 双缓冲落地（对应 P0）**：构建到 `filename.db.tmp`/`fulltext.db.tmp`/新 LanceDB 表，构建完成后关旧连接、原子 rename/swap、再开连接；切换瞬间搜索短暂不可用但整体不再"先删后空"。

- [core/router.py:192 compute_score + 184] **跨层分数归一化是经验常数**（l1 40~100、l2 0~50、l3 0~70），混排时各层绝对分不可比，auto 模式排序质量靠拍参数。建议后续用各层分位数/离线标定统一量纲，或改成交互式分层展示而非强行合并分数。

---

## 亮点（值得保留的设计）

- **统一三层路由 + auto 混排语法**（router.py）：`filename:/content:/?/all:/re:/ext:/sort:` 约定清晰，前端无需关心来源层；`auto` 模式三层聚合体验好。
- **存储与后端契约分离**（index.py:228 `FilenameBackend` ABC）：USN/walk 可插拔，`make_backend` 按平台+权限探测回退（index.py:292-314），避免"实例化成功但 build 静默 0 条"。
- **USN watch 已修复为 FRN→路径重建精确删除**（usn_backend.py:589-624）：用 `OpenFileById` 还原父目录路径再拼完整路径、精确 `DELETE`，不再按 name 模糊删（ANALYSIS 旧 issue #3 已解决，是本轮重构的加分项）。
- **L2 FTS5 查询稳健**（l2_fulltext/index.py:36-45）：jieba 分词 + 双引号包裹 token，特殊字符（`C++`、`100%`、`(test)`）不炸，且 `OperationalError` 时降级 LIKE（:141-160），鲁棒。
- **计数早停防护**（router.py:24 + index.py:121 + l2:218）：`COUNT_CAP` + `SELECT COUNT(*) FROM (子查询 LIMIT cap+1)` 防超大结果集卡死，DoS 友好。
- **增量 upsert 幂等**（index.py:66 `ON CONFLICT(path) DO UPDATE`、l3 index.py:97 先删旧 chunk 再插）：rebuild 中断可重跑不重复。
- **WAL + busy_timeout 并发基础模型已就位**（index.py:42-44、l2:68-70），为后续双缓冲/多进程读写打下底。
- **配置三层优先级**（config.py:147-187）：默认 < local < ProgramData，SYSTEM 服务与用户进程共享一份配置，设计合理。
- **依赖/模型缺失优雅降级**（router.py:370、__main__.py:62、embedder.py:35）：L3 不可用不影响 L1/L2，不拖垮启动。

---

## 与基线文档(ANALYSIS-2026-07-27)的对照

- 已修复（本轮代码核对）：USN 卷根 `join` 丢分隔符(原 #4)、按 HIDDEN 误杀(原 #5)、build 缺 exclude/全卷失败静默(原 #6)、make_backend 回退(原 #7)、USN watch 按 name 模糊删(原 architect #3)——均已落实。
- 仍待修（原文档已提出，本轮确认未解决）：L2 每文档 commit（原 architect #1，见 P3）、rebuild 先删后建（原 #3，见 P0）、USN 无 size（原 #1，见 P1）、config 模块副作用（原 architect #4，见 P2）、walk watch 仍 NotImplementedError（原 architect #5，index.py:289，本轮未列入 P 级但仍是缺口，建议接 watchdog 增量）。
- 本轮新发现（文档未覆盖）：L2 无 `remove_document` 致删除/重命名残留（P0-B）、L3 阀门只在 web 不在 builder/CLI（P0-C）、USN watch 删除绕过锁且未防 SQLITE_BUSY 崩溃（P1-E）、L3 drop/_connect 不持锁竞态（P1-G）、watcher prime 不向量化致空库（P1-H）、indexer_service 不维护 L2/L3（P2-K）。
