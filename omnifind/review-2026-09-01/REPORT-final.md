# OmniFind 全量测试与优化 — 交付报告

**日期**: 2026-09-01
**目标**: 对 `D:\huliang\java\ideaworkspace\devtools\omnifind` 全量测试 + 适度重构优化
**授权选型**: 适度重构（不碰架构级新功能）+ 技能组合 A(multi-role-review) + B(code-simplifier) + C(pr-review 子 agent 组) + D(root-cause-global-fix) + L3 语义层实跑全链路验证

---

## 1. 测试基线与环境

| 项 | 结果 |
|---|---|
| 隔离 venv | managed Python 3.13.12 + Nexus 私服装依赖（pytest 9.1.1 / onnxruntime / lancedb 0.37.1 / python-docx 1.2.0 / python-pptx 1.0.2 / xlrd 2.0.2） |
| 回归测试 `tests/test_full.py` | **PASS 48 / FAIL 0**（脚本式，须 `python tests/test_full.py` 直接跑，不能 pytest） |
| L3 语义层实跑 | 真实环境 l3_count=146061，模型 `bge-small-zh-v1.5` 齐全，语义检索全链路可用 |
| 真服务器 E2E | uvicorn 实跑，`/` 返回 100KB HTML；`/api/status\|search\|preview\|config` 全部符合预期 |

---

## 2. 五批根因修复（root-cause-global-fix：一处修复覆盖整类）

### 聚类 A — Web 层绕过封装（锁/注册表契约）
- 新增 `FilenameIndex.contains()` / `FullTextIndex.contains()` / `SemanticIndex.contains()`，web 层校验走锁，禁裸触 `conn`。
- `server.py`：`lifespan` 调 `load_all_extractors()`（修复注册表契约被切断隐患）；删除私有 `_extract_document_text`/`_extract_pdf_text`，`preview()` 统一走 `extract_text` 注册表；`_is_text_file` 只读 8KB 头部防大文件整读 OOM；`_verify_path_in_index` 补查 L3（修复 L3 命中文件预览/打开/定位 403 死路）。

### 聚类 B — L3 语义层健壮性
- `router.py`：L3 检索异常不再静默吞，改为 `logging.exception` + `counts["l3_error"]`。
- `l3/index.py`：`Lock` → `RLock`（可重入，避免 `_connect`/`_ensure_table` 在持锁调用内死锁）；`_connect`/`_ensure_table`/`count`/`drop` 全部包进锁。
- `builder.py`：自包含安全阀（无 semantic_dirs 且非全盘则返回 0）+ 每文件体积闸门。
- `watcher.py`：空库 prime 后一次性 warning。

### 聚类 C — 索引增删改语义失真
- `l2/index.py`：新增 `remove_document()` 持锁删 fts+files。
- `l3/index.py`：新增 `close()`。
- `usn_backend.py`：USN 删除分支由裸 `conn.execute` 改为 `self.index.remove(p)`（持锁、语义一致）。
- `server.py`：新增 `_rebuild_layer` 双缓冲（构建到 `*.rebuild` → `os.replace` 原子替换 → 失败保留旧索引），L1/L2 重建 honest 化。

### 聚类 D — 配置单一真相源
- `config.py`：`OmniConfig.scan_roots` 默认改 `default_factory=list`（不在 import 期枚举盘符）；删除模块级 import 即读盘副作用，改为惰性 + 进程内缓存访问器 `get_default_config()/get_data_dir()/get_models_dir()/get_logs_dir()`。
- 三层 index 全部改 `get_data_dir()` 惰性取路径。

### 聚类 E/F — 资源小补 + 前端一致性
- `office.py`：**修复 docx/pptx 用 `with` 上下文管理器的运行时崩溃**（python-docx 1.x / python-pptx 1.x 的 `Document`/`Presentation` 不支持上下文管理器），改为「读入 BytesIO 即关磁盘句柄」；`XlsExtractor` 用 `try/finally: wb.release_resources()` 释放 xlrd 资源；新增 `XlsExtractor` 注册 `.xls`。
- `index.html`：4 项一致性修复（见 §4）。

---

## 3. 交叉评审（#6 pr-review）发现 → 二次修复

并行启动 3 个交叉评审子 agent（code-reviewer / silent-failure-hunter / api-contract-verifier）审我的 diff，命中 4 个真问题，已全修：

| 严重度 | 问题 | 修复 |
|---|---|---|
| **HIGH(运行崩溃)** | `office.py` 用 `with docx.Document()/Presentation()`，但安装版本不支持上下文管理器 → 任何 docx/pptx 预览/索引直接 `TypeError` 崩溃 | 改为读入 `BytesIO` 后关闭磁盘句柄（`server.py` 抽取/索引不再泄漏文件句柄） |
| **HIGH(Win 竞态)** | `_rebuild_layer` 在函数内 `live.close()` 且临时对象句柄未关就 `os.replace` → Windows 重建路径可能 "file in use"/关闭中 DB 竞态 | 移出 `live.close()` 到调用方替换后；`os.replace` 前 `tmp.close()` 释放临时句柄 |
| **HIGH(语义清空)** | L3 重建仍 `l3.drop()` 后 `build`，中途失败即清空语义索引 | L3 也走双缓冲：构建到临时目录，成功才原子替换，失败保留旧索引 |
| MED | `l3.contains()` 异常静默返回 False，可能把合法文件误判 403 | 加 `logging.exception` 后返回 False（fail-closed 但可见） |

API 契约交叉验证结论：**CLEAN** —— `/api/search|status|preview|file/info|config` 请求/响应结构未变，`/api/preview` 403/200 行为保留、L3 仅拓宽覆盖。

---

## 4. 前端 4 项一致性修复（index.html）

1. **Ctrl+C 劫持**：仅在无文本选区时复制路径，有选区放行原生复制（修复预览/结果区复制文字被强制改成路径）。
2. **高亮前缀剥离**：`extractKeywords` 重写为按 token 分词，剥离 `ext:`/`sort:`/`filename:`/`content:`/`all:`/`re:`/`?` 等语法前缀，避免把 `ext:pdf` 当关键词高亮。
3. **侧栏/下拉与查询前缀同步**：新增 `syncControlsToQuery()`，查询含 `filename:/content:/all:/?/sort:/ext:` 时同步侧栏高亮 + 排序下拉 + 类型筛选高亮，消除「查询带前缀但控件停旧值」的错位。
4. **清空回正**：`clearSearch` 重置 `currentLayerFilter` 回当前持久模式。

JS 语法 `node --check` 通过。

---

## 5. 验证结果

- **回归**：`python tests/test_full.py` → **PASS 48 / FAIL 0**（5 批修复 + 二次修复后均维持全绿）。
- **真服务器 E2E**（uvicorn 实跑，端口 8137）：
  - `GET /` → 200，100,890 字节 HTML
  - `/api/status` → 200，真实计数 L1=419841 / L2=16297 / **L3=146061**
  - `/api/search?q=filename:test` → mode=filename；`?q=...test` → mode=semantic（2 条真实 L3 命中，无 `l3_error`）
  - `/api/preview` 在索引文件 → 200；越索引文件 → 403；目录 → 404（符合预期）
  - `/api/config` → 200，白名单逻辑不变

---

## 6. 未做 / 后续建议（非本次范围）

- 未做完整无头浏览器点击实测（Playwright 未在本环境部署）；已用真 HTTP 服务器 E2E 覆盖核心路径。如需要可补 headless 点击回归。
- `watcher.py` 的 `_warned_empty` 在首次 prime 即置位（即便非空），极端空语义场景可能不再告警 —— LOW，可后续微调。
- 无关改动：`../QR_GENERATORBYCCC/res/resource.rc` 8 行差异属兄弟仓库，未触碰。

---

## 7. 改动文件清单（git diff --stat）

```
omnifind/core/config.py                       |  38 ++-
omnifind/core/router.py                       |   7 +-
omnifind/extractors/office.py                 |  58 ++--
omnifind/layers/l1_filename/index.py          |  12 +-
omnifind/layers/l1_filename/usn_backend.py    |   9 +-
omnifind/layers/l2_fulltext/index.py          |  23 +-
omnifind/layers/l3_semantic/index.py          |  77 +++--
omnifind/layers/l3_semantic/builder.py        |  19 +-
omnifind/layers/l3_semantic/watcher.py        |  10 +
omnifind/web/server.py                        | 333 +++++++--------------
omnifind/web/static/index.html                |  87 +++++-
omnifind/tests/test_full.py                   |   2 +-
13 files changed, 383 insertions(+), 300 deletions(-)
```
