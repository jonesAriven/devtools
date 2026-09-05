# 测试 评审发现

> 评审角色：测试（tester）。只做评审、不改代码。
> 评审对象：`tests/test_full.py`（185 行 / 48 断言 / 脚本式 / PASS 48 FAIL 0）。
> 运行环境：`C:\Users\13871\.workbuddy\binaries\python\envs\default\Scripts\python.exe`
> 运行现状：已实跑 `python tests/test_full.py` → `==== 结果: PASS 48 / FAIL 0` ✅

## 现状一句话

`tests/` 目录**当前只有 `test_full.py` 一份冒烟脚本**。ANALYSIS-2026-07-27.md 第 6 节"测试矩阵（103 项全绿）"描述的三个文件：

- `tests/test_round1_unit.py`（49 项：配置优先级 / 6 类抽取器 / GBK / L1/L2/Router 单元）
- `tests/test_round2_e2e.py`（36 项：真实 uvicorn 起服务 / 预览 txt·pdf·docx·xlsx·二进制 / 索引外拒绝 / 注入防御）
- `tests/test_round3_edge.py`（18 项：大文件跳过 / exclude_dirs / UTF-16 / 8 层嵌套 / 空文件 / 幂等 / 并发 160 / rebuild 拒绝）

**在当前仓库已全部不存在**（git 历史无这些文件，`tests/` 仅有 `test_full.py`，见下方核对）。即上一轮号称覆盖的「单元 + 真实 E2E + 边界」三大类已整体退化为一份 happy-path 冒烟脚本。**这是本轮最严重的测试风险：上一轮验证过的并发/边界/配置优先级/双后端对比全无回归守护。**

```bash
# 核对命令与结果
$ ls tests/
__pycache__/  test_full.py        # 仅此一个测试文件
# git log 中无 test_round1_unit.py / test_round2_e2e.py / test_round3_edge.py 的任何痕迹
```

---

## P0 功能断链（测试无法覆盖导致的风险）

### P0-1 真实索引管线（indexer + 后端 build）从未被测试
- **问题**：`test_full.py` 全程用 `l1.bulk_upsert(合成行)` 造数据（`test_full.py:42-46`），从没调用过 `WalkBackend.build()` / `UsnBackend.build()`，也没调用 `omnifind/core/indexer.py` 的 `build_filename_index` / `build_fulltext_index`。整个"扫盘→落库"的真实代码路径零覆盖。一旦索引器回归（如抽取失败计数错、排除目录漏过滤），测试全绿但线上坏。
- **源码**：`omnifind/core/indexer.py:25-76`、`omnifind/layers/l1_filename/index.py:246-285`（WalkBackend.build）、`usn_backend.py:448-478`（UsnBackend.build）
- **缺什么测试**：对 `make_backend(cfg, l1).build()` 与 `build_fulltext_index(cfg, l2, limit=...)` 的真实跑通断言。
- **怎么写**：
  ```python
  # 用临时语料 + 临时 cfg（scan_roots=[corpus], exclude_dirs=[]）
  from omnifind.core.indexer import build_filename_index, build_fulltext_index
  n = build_filename_index(cfg, l1)
  assert n == 7                     # 与合成造数据口径一致
  m = build_fulltext_index(cfg, l2)
  assert m >= 5                     # 可抽取类型都被索引
  # 对比 bulk_upsert 路径产出的 count 一致性
  ```

### P0-2 L1 USN 后端 vs walk 后端 对比测试缺失（双后端路径差异）
- **问题**：ANALYSIS 反复强调"双后端可插拔"，但测试从未实例化任一 backend。两条后端路径差异完全无断言：
  1. **USN 的 size 恒为 0**（`usn_backend.py:425` 注释 `size USN 不带`），而 walk 后端带真实 size → `sort:size` 与 UI 大小列对 USN 索引无意义（ANALYSIS 列为 P0 产品缺陷），无任何测试锁定此事实。
  2. **排除目录过滤行为**：walk 在 `os.walk` 里原地 `dirnames[:]=...`（`index.py:259`）过滤；USN 在 `build()` 里对路径逐段 `seg in exclude` 过滤（`usn_backend.py:460-463`）。两路径实现不同，需一个共享契约测试保证行为一致。
  3. **隐藏/系统文件**：USN 只过滤 SYSTEM 属性（`usn_backend.py:412`），实现与 walk 不同，回归无守护。
- **源码**：`usn_backend.py:448-478` vs `index.py:246-285`；`make_backend` 选择逻辑 `index.py:292-314`。
- **缺什么测试**：同一份 `scan_roots=[corpus]` + 同一 `exclude_dirs`，分别用 `WalkBackend` 与（在 Windows SYSTEM 下）`UsnBackend` build，断言两者产出的 `(path, name, is_dir)` 集合在"路径规范化后"一致、`exclude_dirs` 都被剔除、walk 的 `size>0` 而 USN `size==0` 被明确记录。
- **怎么写**：抽象一个 `assert_backend_contract(backend, cfg, l1, expect_size_zero)`，walk 路径 CI 可常年跑；USN 路径用 `skipif(not probe_usn_available())` 只在 Windows SYSTEM 环境跑，避免 CI 永远 skip 导致"假绿"。

### P0-3 L3 语义层已可用，但 router / web 全程注入 l3=None → L3 分支全未测
- **问题**：本机模型存在（`test_full.py` L3 段 `PASS l3 chunks>0`），但 Router 测试与 Web 测试都**强制 `l3=None`**（`test_full.py:128-129, 159`）。于是 `router.py` 里 `counts["l3"]`、`ext_facets` 并入 L3、`semantic_disabled` 信号、`all` 模式三层聚合的真实路径**从未被验证**。一旦 L3 计数/聚合逻辑回归（如 `total` 漏加 l3、facet 漏桶），测试仍全绿。
- **源码**：`router.py:286-372`（L3 采集与计数）、`router.py:353-364`（l3 置 0 / disabled）、`server.py:46-53`（l3 实例化）。
- **缺什么测试**：用真实 `OnnxEmbedder`+`SemanticIndex` 构造含 L3 的 `QueryRouter`，断言 `all:检索` 的 `counts["l3"]>0`、`total == l1+l2+l3`、`ext_facets` 含 L3 分桶；`?问句` 语义模式返回 `mode=="semantic"` 且 hits 非空（而非 disabled）。
- **怎么写**：
  ```python
  router = QueryRouter(l1=l1b, l2=l2b, l3=sem)   # 复用 L3 段已建好的 sem
  resp = router.search("all:检索")
  assert resp.counts["l3"] > 0
  assert resp.counts["total"] == resp.counts["l1"]+resp.counts["l2"]+resp.counts["l3"]
  ```

### P0-4 并发 160 请求零错误 无测试守门（ANALYSIS 声称的指标已失）
- **问题**：ANALYSIS 第 3 节写"WAL + threading.Lock，并发实测 160 请求零错误（234 req/s）"，但 `test_full.py` 只有 `router 20x search < 10s`（`test_full.py:149-152`）——**20 次串行、进程内、非 HTTP、非并发**。Lock/WAL 的并发安全性一旦被改坏，测试无法发现。
- **源码**：`server.py:93-125`（search 无锁但依赖底层 WAL）、`index.py:37-44`（WAL + busy_timeout + Lock）。
- **缺什么测试**：真实起 uvicorn（独立端口 + 独立 data_dir，参见 ANALYSIS Round2），用 `concurrent.futures.ThreadPoolExecutor` 发 160 个 `/api/search` 并发请求，断言全部 200 且 `has_more` 字段正确、无 500。
- **怎么写**：
  ```python
  import threading, requests
  errs=[]
  def fire():
      r=requests.get(url, params={"q":"检索"})
      if r.status_code!=200: errs.append(r.status_code)
  with ThreadPoolExecutor(max_workers=32) as ex:
      list(ex.map(lambda _: fire(), range(160)))
  assert not errs, errs
  ```

---

## P1 错误行为

### P1-1 rebuild 并发拒绝 / 完成态未测
- **问题**：`/api/index/rebuild` 用 `_rebuild_lock` + `start_build` 返回 False → 400（`server.py:310-315`）。上一轮 Round3 的"rebuild 受理/完成/并发拒绝/非法 422"已整体丢失，当前测试无任何 rebuild 用例。
- **源码**：`server.py:310-315`、`server.py:293-307`、`server.py:244-290`。
- **缺什么**：`POST /api/index/rebuild` 触发后轮询 `/api/index/status` 直到 `running==False`；并发两次 rebuild 第二次应 400。
- **怎么写**：TestClient `client.post("/api/index/rebuild", params={"type":"l1"})` → 200；立即再发一次 → 400；轮询 status 直到 `running` 为 false 且 `error==""`。

### P1-2 配置三层优先级（config.yaml < config.local.yaml < 系统级）未测
- **问题**：`OmniConfig.load()` 优先级叠加逻辑（`config.py:147-187`）完全无测试。ANALYSIS Round1 声称覆盖"配置加载/优先级"，现缺失。若某层覆盖顺序写反（如 local 没盖过 default），无守护。
- **源码**：`config.py:147-187`（`candidate_paths` 顺序 + 逐层 `setattr`）。
- **缺什么**：临时写 `config.yaml`（port=8899）、`config.local.yaml`（port=9000）、系统级 `data_dir/config.yaml`（port=9100），断言最终 `port==9100`；删系统级后回落 9000；删 local 后回落 8899；空列表 `fulltext_exts: []` 不覆盖默认。
- **怎么写**：用 `tmp_path` + monkeypatch `ROOT` 与 `default_data_dir` 返回值，逐层构造文件后 `OmniConfig.load()` 断言。

### P1-3 预览路径校验的穿越/符号链接逃逸未测
- **问题**：`_verify_path_in_index`（`server.py:577-610`）仅测了 `/etc/passwd` 被 403（`test_full.py:171-172`）。缺：Windows 盘符穿越、`..` 归一化后逃逸、符号链接指向已索引文件但实际越权、路径存在但不在索引（应 403 而非 500）等。
- **源码**：`server.py:577-610`、`server.py:608-610`（不存在分支）。
- **缺什么**：`path` 传一个 `resolve()` 后落在索引外（如 `corpus/../outside.txt`）→ 403；传一个指向索引内文件的软链 → 行为需明确（建议至少断言不越权出索引）；`path` 指向已删文件 → 403（"文件不存在"）。
- **怎么写**：在临时语料外建一个文件，传其绝对路径给 `/api/preview` → 断言 403；建 symlink 指向语料内文件 → 断言预览内容正确或被拒（按安全策略定）。

### P1-4 offset 越界回落行为未测
- **问题**：`server.py:109-112` 注释明确"offset 越界必须返回空，不能回落到第一页"，但无断言。上一轮 Round2 的"分页"覆盖已失。
- **源码**：`server.py:106-112`。
- **缺什么**：`offset` 超过结果总数时 `hits==[]` 且 `has_more==False`，不得回落。
- **怎么写**：`client.get("/api/search", params={"q":"检索","offset":9999})` → `hits==[]`。

### P1-5 router 层无查询长度闸
- **问题**：web 层 `Query(max_length=2000)`（`server.py:94`）挡了超长查询，但 `router.search` 本身对超长/超高频词无保护，只有 `COUNT_CAP=5000` 早停（`router.py:24`）。若有人绕过 API 直接调 router（语义层/脚本/内部调用），超长正则或超长词可能拖慢。测试未覆盖 router 在极端输入下的行为与计时。
- **源码**：`router.py:203-387`、`router.py:24`。
- **缺什么**：`router.search("a"*100000)` 应在合理时间返回（受 COUNT_CAP 约束）而非卡死；`router.search("re:"+超长非法正则)` 计数为 0 且不抛。
- **怎么写**：`t=time(); router.search("x"*200000); assert time.time()-t < 5`。

### P1-6 错误注入：不可解编码 / 超大文件 / 抽取失败未测
- **问题**：`build_fulltext_index` 的跳过分支（`indexer.py:53-59`：过大跳过、抽取失败跳过）无测试。语料全是 UTF-8 小文件。GBK / UTF-16 / 空文件 / >`max_fulltext_mb` 文件的处理全靠未测代码。
- **源码**：`indexer.py:44-59`、`extractors/*`。
- **缺什么**：构造 GBK/UTF-16 文件断言能被正确抽到 L2；构造 >50MB 文件断言被 `skipped_too_large`；构造抽取器报错的脏文件断言不崩溃且计数到 `skipped_extract_fail`。
- **怎么写**：`p.write_bytes("中文".encode("gbk"))` → `l2.search` 命中；`p.write_bytes(b"x"*(60*1024*1024))` → build 后 l2 不增。

---

## P2 体验缺陷

### P2-1 USN 索引 size 恒 0（无测试锁定事实/推动补采）
- **问题**：`usn_backend.py:425` 注释明说 USN 不带 size（全存 0），导致 `sort:size` 无意义、UI 大小列空（ANALYSIS P0 产品缺陷 + 设计师视角"建议空时显示 —"）。测试既没锁定"USN size==0"，也没测"walk size>0"，回归时该缺陷会被悄悄改坏或改好都无人知。
- **源码**：`usn_backend.py:425`、`index.py:272`（walk 带 size）。
- **怎么写**：在 P0-2 的 `assert_backend_contract` 里显式 `assert all(h.size==0 for h in usn_hits)` 且 `assert all(h.size>0 for h in walk_hits)`；并补一条 TODO 测试：size 补采任务（`GetFileAttributesEx` 回填）落地后此断言应翻转。

### P2-2 排序实际顺序未断言（仅检查 mode 字符串）
- **问题**：`test_full.py:146-147` 只断言 `router sort parse` → `mode=="auto"`，没验证 `sort:time_desc` / `size_asc` / `size_desc` 真的产出正确顺序。排序逻辑 `router.py:152-201` 是纯函数但零单测。
- **源码**：`router.py:152-201`、`router.py:193-200`。
- **怎么写**：构造 3 个 mtime/size 不同的命中，分别 `router.search("x sort:time_desc")` 断言 hits 按 mtime 降序；`sort:size_asc` 按 size 升序。

### P2-3 预览编码回退链（utf-8/gbk/gb2312/latin-1）与二进制分支未测
- **问题**：`server.py:429-443` 编码回退链、二进制跳过（`server.py:411-416`）、PDF/docx/xlsx 分支（`server.py:376-406`）只在已丢失的 Round2 E2E 覆盖。当前测试只验了 UTF-8 txt 预览（`test_full.py:169-170`）。GBK 文件预览、二进制文件返回 `source:"binary_skip"` 均无断言。
- **源码**：`server.py:345-446`。
- **怎么写**：GBK 编码文件 → 预览 `encoding=="gbk"` 且内容正确；`.png` → `source=="binary_skip"`；超大文件 → `source=="too_large"`。

### P2-4 config 写入持久化未测
- **问题**：`update_config` 会把变更写 `config.local.yaml`（`server.py:206-223`）并热更新 `semantic_min_score` 到 `l3.min_score`（`server.py:202-203`），但无测试验证"写入后文件真改 + 下次 load 生效"。
- **源码**：`server.py:181-224`。
- **怎么写**：`client.post("/api/config", json={"max_fulltext_mb":10})` → 200；读 `config.local.yaml` 断言含 `max_fulltext_mb: 10`；重新 `OmniConfig.load()` 断言生效。

---

## P3 优化建议

### P3-1 脚本式测试应迁移为 pytest
- `test_full.py` 用全局 `PASS/FAIL` 列表 + `print` + `sys.exit(1)`（`test_full.py:17-22,180-185`），非 pytest。无 fixture、无用例隔离、无 `xfail`、无 CI 集成，且失败定位靠肉眼翻输出。
- **怎么改**：拆为 `tests/test_l1.py` / `test_l2.py` / `test_l3.py` / `test_router.py` / `test_web.py`，用 `tmp_path` fixture 造语料、`autouse` fixture 负责 `rmtree`；每条一个 `def test_*` + `assert`。

### P3-2 语料不真实
- 当前语料是 5 行小文件（`test_full.py:29-36`），缺 GBK/UTF-16/空文件/深层嵌套(>8 层)/超大文件(>`max_fulltext_mb`)/符号链接/权限受限目录。建议补齐为"真实文件系统样本集"，P0-2/P1-1/P1-6 都可复用。

### P3-3 浏览器端到端完全缺
- 当前 Web 测试只用 `TestClient`（进程内，验 HTTP 形状），不验渲染。`search → 渲染 → 预览 → 筛选/排序 → 设置` 的真实交互流（ANALYSIS Round2 的浏览器层）无覆盖。
- **怎么补**：起真实 uvicorn（独立端口 + 独立 data_dir），用 Playwright 走一遍：输入关键词→结果出现→点开预览→切文件类型筛选→改 sort→打开设置改 `max_fulltext_mb` 并验证持久化。建议作为单独 `tests/e2e_browser/` 慢测试，CI 标记 `slow`。

### P3-4 加 CI 门禁防再次整体退化
- 上一轮 103 项测试整体消失却无人察觉，说明无门禁。建议：pytest + `coverage` 阈值（如 `omnifind/core`、`omnifind/layers/l1_filename` 行覆盖 ≥ 70%）+ 并发/边界用例纳入必跑；PR 卡 coverage 与 `PASS 0 FAIL` 双红线。

### P3-5 修正 ANALYSIS 文档失真
- ANALYSIS-2026-07-27.md 第 6 节仍写"测试矩阵 103 项全绿"且点名三个测试文件，但仓库已无这些文件。建议文档加"当前测试清单"小节或标注"该矩阵已于 2026-09 重构为 test_full.py（48 项），原三文件已废弃"，避免后续评审被误导。

---

## 亮点（保留，勿在重构中破坏）

- **happy path 覆盖扎实**：L1 子串/通配/正则/ext 分组（`test_full.py:48-75`）、L2 中文/英文/特殊字符(`db.query(uid)`)/snippet 高亮标记（`93`）/分组（`96-99`）、Router 全语法解析（filename/content/?/all/re/ext/sort，`130-148`）、Web API 注入 state（`154-178`）均绿。
- **错误处理意识好**：非法正则在 L1（`test_full.py:66-70` `bad regex raises`）与 router（`140-141` `counts.error`）两处都有错误分支断言；超长查询经 web `Query(max_length=2000)` 返回 422（`177-178`）。
- **安全边界有守门**：索引外文件预览拒绝 403（`171-172`）、config 白名单拒绝非法字段 400（`175-176`）——与 ANALYSIS"仅可操作已索引文件"的安全设计一致。
- **隔离设计对**：语料用 `tempfile.mkdtemp` + 末尾 `shutil.rmtree`（`test_full.py:14,184`），不碰生产索引。
- **L3 优雅降级**：模型缺失时 `SKIP` 且 `check("l3 (skipped)", True)`（`122-124`），环境无模型也不会红，适合跨机器跑。
- **后端选择有安全回退**：`make_backend` 对 `auto` 模式做 `probe_usn_available` 探测（`index.py:307-309`），权限不足回退 walk，避免"build 静默 0 条"——这条逻辑本身值得补单测（见 P0-2）。

---

## 下一步补测优先级（给实施方）

1. **先恢复回归守护（防再退化）**：把 P0-1/P0-4/P1-1/P1-2 从零补回 pytest（对应上一轮 Round1/2/3 已验证但丢失的部分）。
2. **双后端契约（P0-2/P2-1）**：抽象 `assert_backend_contract`，walk 常跑，USN `skipif` 仅 Windows SYSTEM 跑。
3. **L3 真路径（P0-3）**：复用已下载模型，给 router/web 加含 L3 的聚合断言。
4. **错误注入（P1-3~P1-6）**：路径穿越 / offset 越界 / 超长查询 / 编码与超大文件。
5. **真实 E2E + 浏览器（P3-3）**：独立端口起服务 + Playwright。
