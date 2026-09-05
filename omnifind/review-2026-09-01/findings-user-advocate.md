# 使用者 评审发现

> 角色：使用者（user-advocate），以「第一次用的目标用户」走完 安装 → 配置 → 索引 → 搜索 → 预览 → 打开/定位 → 设置面板 → 重建 全流程。
> 评审范围：`__main__.py` / `web/server.py` / `web/static/index.html` / `config.yaml` / `config.local.yaml` / `WINDOWS-DEPLOY.md` / `restart_omnifind.py` / `docs/ANALYSIS-2026-07-27.md` / `core/router.py` / `core/config.py`。
> 环境核对：本机 `omnifind-server.log` 显示 data_dir=`C:\ProgramData\omnifind\data`、L3 语义层已启用、且触发过 L3 全量重建（log 第 7 行 `lancedb/chunks.lance` 已创建）。

---

## P0 功能断链

- [omnifind/web/server.py:585-610] **语义层(L3)专属命中文件无法预览/打开/定位，安全校验只认 L1/L2。**
  `showPreview/openHit/revealHit` 都先调 `_verify_path_in_index`，而该函数只 `SELECT` 了 `l2.files` 与 `l1.entries`（line 588-605），**完全没查 `l3`**。当某文件仅被语义索引（典型触发路径：用户在设置面板把 `.pdf` 从 `fulltext_exts` 去掉，但 `semantic_exts` 仍含 `.pdf` 且 `semantic_full_disk:true`）→ 用户语义搜到结果 → 点预览/打开/定位 → 返回 `403` + “文件不在索引中，无法操作”，整条操作链断掉。
  → 建议：`_verify_path_in_index` 增加 `if l3 is not None: SELECT ... FROM chunks WHERE path=?` 兜底；或统一用「路径存在且位于 scan_roots 内」作为打开/预览的安全判据，而不是「必须在 L1/L2 索引中」。

- [omnifind/web/server.py:206-220 与 core/config.py:157-159] **设置面板保存的配置可能被「静默覆盖、完全不生效」（取决于是否按部署文档建了 ProgramData 配置）。**
  `/api/config` 的 POST 固定写到 `<项目根>/config.local.yaml`（server.py:210 `ROOT/"config.local.yaml"`），但 `OmniConfig.load()` 把 `%ProgramData%\omnifind\config.yaml` 追加为**最高优先级**覆盖（config.py:157-159）。而 `WINDOWS-DEPLOY.md:46` 明确教用户去建这个文件。
  → 后果：用户若在 UI 里改了「扫描根目录/排除目录/全文扩展名/单文件大小」并保存，只要 `C:\ProgramData\omnifind\config.yaml` 存在，改动就**被它覆盖、看似保存成功实则无效**，且重建后用户困惑「为什么没变」。
  → 建议：① UI 保存目标与读取优先级一致——若不存在 ProgramData 配置则写 `config.local.yaml`，若存在则提示「系统级配置正生效，请在设置页提供『写入系统级配置』开关或明确提示编辑路径」；② 至少 `GET /api/config` 返回当前「生效配置来源」，UI 顶部用一行小字标明「当前配置由 C:\ProgramData\omnifind\config.yaml 接管，UI 编辑不生效」，避免静默死路。

---

## P1 错误行为

- [omnifind/web/static/index.html:1060-1065] **「默认搜索模式」设置后，侧栏高亮与实际搜索模式错位。**
  `loadUISettings` 把 `currentLayer = s.defaultMode`（如 `filename`），紧接着又把 `currentLayerFilter = 'auto'`（line 1064），而 `updateLayerFilterUI()` 用 `currentLayerFilter` 决定高亮。结果：搜索实际跑「文件名」模式，但侧栏「搜索模式」高亮停在「智能」，用户看到的状态与行为不一致。
  → 建议：把 line 1064 改为 `currentLayerFilter = currentLayer;`（二者本应同源，可进一步合并为单一变量）。

- [omnifind/web/server.py:58-65 与 index.html:1255-1293] **首跑/「服务自启构建」全程无任何可见反馈。**
  `serve` 启动且 `l1.count()==0` 时 `lifespan` 在后台 `start_build("all")`（server.py:59-65，本机 scan_roots 为 C:\/D:\/G:\，walk 后端可能跑数十分钟），但 `loadStatus()` 只在页面加载时调一次（index.html:2125），**不轮询 `/api/index/status`**。用户打开页面看到 `L1 0 · L2 0 · L3 0`，输入关键词得到 0 结果，界面上没有任何「正在首次构建索引」的提示，极易误判为「搜不到=坏了」。设置面板的进度条也只在手动「重建」时才动。
  → 建议：进入页面若 `_index_task.running` 为真（或 l1/l2 计数仍为 0 且任务在跑），顶部状态条或空态区直接显示「首次索引构建中…」并轮询 `/api/index/status` 刷新计数与进度；自动构建也复用 `pollIndexStatus` 的进度条逻辑。

- [docs/WINDOWS-DEPLOY.md:60 与 core/config.py:91] **部署文档让普通用户 `serve` 用 `l1_backend: usn`，但 USN 需 SYSTEM/管理员。**
  文档示例 `l1_backend: usn`（line 60），而 `serve` 由普通用户运行（文档 step 6），USN journal 读取需要提权，非管理员下大概率失败/落回 walk。文档又自称「不用重启啥东西」，用户不易察觉降级。
  → 建议：部署文档普通用户段改 `l1_backend: auto`（由 `probe_usn_available` 自动决定），仅 SYSTEM 索引服务才显式 `usn`；并在 Web 状态条露出「当前 L1 后端：usn/walk」。

- [restart_omnifind.py:23-24] **重启脚本硬编码解释器与项目路径，与本机实际 venv 不匹配，直接跑会失败。**
  `PYTHON = r"D:\huliang\software\Python\Python313\python.exe"`、`PROJECT_ROOT = r"D:\huliang\java\ideaworkspace\devtools\omnifind"` 为写死路径；而本轮运行环境 venv 为 `C:\Users\13871\.workbuddy\binaries\python\envs\default\Scripts\python.exe`。若该路径不存在或该裸 Python 没装 fastapi/uvicorn/jieba，重启即报「系统找不到指定的文件」或 import 失败，且脚本无自检提示。
  → 建议：从环境变量/`py -0`/同目录 venv 解析解释器；或首行打印「将用 X 启动，依赖缺失请先 pip install -r requirements.txt」，失败即明确报错而非静默退出。

---

## P2 体验缺陷

- [omnifind/web/static/index.html:756-797] **设置面板无法配置语义层与关键运行项。**
  「搜索配置」页只有 扫描根目录/排除目录/全文扩展名/单文件大小；但 `semantic_dirs`、`semantic_full_disk`、`l1_backend`、`port` 只能通过手改 YAML（且如 P0-2 还可能不生效）。语义搜索是产品卖点，却没有任何 UI 入口。
  → 建议：增加「语义目录」「全盘向量化开关」「L1 后端」「端口」字段（需进 `_CONFIG_SCHEMA` 白名单，server.py:166-178）。

- [omnifind/web/static/index.html:557-562] **语法提示遗漏 `re:`/`name:` 等可用前缀，正则模式无入口。**
  顶部 chips 只展示了 `filename:/content:/?/all:/ext:/sort:`，而 `core/router.py:122-132` 还支持 `re:`、`name:`、`filename-re:`、`name-re:`。用户无法从界面发现正则搜索。
  → 建议：补充 `re:` chip（点击插入 `re:`）并加一行「正则：re:pattern」说明。

- [omnifind/config.local.yaml:63-64] **`semantic_full_disk: true` + `semantic_dirs: []` 默认对全盘 C:\/D:\/G:\ 向量化，是「爆库」级 footgun。**
  `core/config.py` 注释明确「默认 false，避免向量库爆炸」，但本机 dev 配置却开了 true。结合 `WINDOWS-DEPLOY.md` 把该文件当作范本，极易被复制。log 第 7 行已证实触发了全量 lancedb 创建。
  → 建议：默认改 `false`；若开启，UI/启动日志明确提示「将对 N 个根目录全部文档做向量化，预计耗时 X、占用 Y」。

- [omnifind/__main__.py:70-81 与 89-93] **CLI `search`/`status` 在索引为空时无任何引导。**
  首次用户跑 `python -m omnifind search "报告"` 直接得到「模式:auto · 命中 0」，`status` 也只打印计数，都不提示「尚未建索引，请先 `python -m omnifind index`」。空态无引导。
  → 建议：count 为 0 时追加一行提示：「索引为空，运行 `python -m omnifind index` 或 `python -m omnifind serve` 后会自动构建」。

- [omnifind/web/server.py:341-371 与 index.html:1001] **USN 索引文件 size 恒为 0 → 大小列留白、`sort:size` 无意义（ANALYSIS 已记，使用者侧仍可见）。**
  `formatSize` 在 `bytes<=0` 时返回空串（index.html:1001），USN 不采 size（ANALYSIS #1）。用户看到大小列大片空白且按大小排序无效。
  → 建议：size 为 0/缺失时显示 `—`；并在后台对 USN 命中文件批量 `GetFileAttributesEx` 补采 size（ANALYSIS 建议）。

- [omnifind/web/static/index.html:1235] **`addSyntax('ext:.py ')` 自带尾空格，前插后产生双空格。**
  `ext:` chip 传入 `'ext:.py '`（line 561），`SYNTAX_PREFIX_RE` 命中走前插分支又补一个空格 → 查询变成 `ext:.py  report`。功能不影响（`router.py` 用 `\bext:(\S+)` 取词），但输入框出现难看双空格。
  → 建议：chip 传入不带尾空格的 `'ext:.py'`，统一由前插逻辑加空格。

- [omnifind/web/server.py:228-238 与 index.html:1276] **顶部状态条不自动刷新，计数会陈旧。**
  仅加载时取一次；重建完成后靠 `pollIndexStatus` 收尾时 `loadStatus()` 刷新，但日常索引变化（L3 watcher 增量）不会反映到状态条。
  → 建议：状态条以低频（如 10s）轮询 `/api/status`，或在重建/索引变更后主动刷新。

---

## P3 优化建议

- [index.html:853-857] 「默认搜索模式」下拉缺少 `semantic`/`all` 选项，与侧栏五模式不对齐。
- [index.html:1378-1393] 空结果区已较好，但 `semantic` 模式空结果时即使 `l3_available` 为真也可补一句「试试降低语义阈值或改用 content:」。
- [index.html:1606-1608] 结果行内操作按钮仅 hover 可见，触屏/窄屏（≤768px 已上下堆叠）下难以点到；移动场景建议常驻或长按。
- [__main__.py:42-52] `cmd_index` 的 L3 跳过提示很好，但 L1/L2 构建无进度百分比（仅 `build_*` 内部可能打印），CLI 体验可加 `tqdm` 式进度。
- [index.html:1949-1954] 「每页显示数量」从 localStorage 读，但下拉初始 HTML `selected` 是 200，刷新后若用户曾改 50 需等 `loadConfigIntoForm` 才纠正，首帧会闪；可在 `loadUISettings` 同步设置 `select.value`。

---

## 亮点

- **安全边界清晰**：预览/打开/定位强制「文件须在索引内」且绑定 127.0.0.1（server.py:577-610 思路正确，仅漏 L3 见 P0-1）；`/api/search` 出错只回笼统文案、详情写日志（server.py:116-125），防信息泄漏。
- **搜索语法对 power user 友好且 auto 模式零门槛**：`filename:/content:/?/all:/re:/ext:/sort:` 统一路由（router.py），前缀优先级处理正确（router.py:219-230）。
- **分面计数一致性修复到位**：文件类型按钮上的数字 == 点击后「共 N 条」（router.py:303-381 + index.html:1508-1526），消除了「智能30<文件名528」类矛盾。
- **空态/错误态文案较诚实**：重建期间提示（index.html:751）、语义未启用提示（index.html:1390-1392）、预览失败/二进制/超大各自有专门文案（index.html:1764-1793）。
- **外观与可达性完善**：暗/亮/跟随系统三主题、字号与密度调节、语法 chip 键盘可达（keyAct）、Ctrl+F/↑↓/Enter/Ctrl+E/Ctrl+C 快捷键齐全。
- **重建流程双保险**：`start_build` 用 `_rebuild_lock` 防并发（server.py:293-307），非法 type 返回 422，进度轮询与完成/失败 toast 闭环（index.html:2001-2049）。
