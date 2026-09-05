# UI 设计师 评审发现

> 评审对象：OmniFind 前端单页应用 `omnifind/web/static/index.html`（HTML+CSS+JS 内联），对照 `omnifind/web/server.py`、`omnifind/core/router.py`、`config.yaml`。
> 评审角色：UI 设计师（视觉层级 / 对比度可读性 / 交互反馈 / 响应式 / 可访问性 / 亮暗主题一致性）。
> 结论性质：仅评审，未修改任何文件。带「⚠️ 需浏览器实测确认」的项为代码静态推断，建议起服务（`python -m omnifind serve`，venv=`C:\Users\13871\.workbuddy\binaries\python\envs\default\Scripts\python.exe`）验证。
> 历史参照：`docs/ANALYSIS-2026-07-27.md` 设计师章节（早于 8/30 重构）所提「USN 大小列为空」「L3 未启用无说明」已在当前代码修复，不再重复。

---

## P0 功能断链

本轮代码评审**未发现硬性「功能断链」（P0）**——核心搜索/预览/打开/定位链路、`/api/search` 与 `/api/preview` 返回结构、亮暗主题切换均可用。最接近严重级别的问题已归入 P1。

---

## P1 错误行为

- [index.html:2107 + 2057] **Ctrl+C 被全局劫持为「复制路径」，阻断正常文本复制。**
  `document.addEventListener('keydown')` 中 `if (e.ctrlKey && e.key === 'c' && activeIndex >= 0 && !isSearchFocused)` 会在「有选中结果且焦点不在搜索框」时强制复制文件路径。
  → 问题：当用户把焦点移到设置弹窗文本框、或在预览面板中选中了代码文本再按 Ctrl+C，期望复制的是自己选中的文字，却被悄悄换成文件路径，造成复制内容丢失/错乱。
  → 建议：仅当 `#q` 聚焦且未选中输入框内文本、或明确在结果列表上下文中才拦截；否则 `return` 交还浏览器默认复制。或改为仅响应右键菜单/显式按钮，不劫持全局 Ctrl+C。
  → ⚠️ 需浏览器实测确认：焦点落在设置 `<textarea>` 且 `activeIndex>=0` 时按 Ctrl+C 的实测表现。

- [index.html:557-562 vs 578-582；1024-1025；1061-1065] **搜索模式「侧栏单选」与实际生效模式不一致（状态分歧）。**
  侧栏「搜索条件」用 `role=radiogroup` 的标签切换 `currentLayer` 并重新搜索（applyLayerFilter）；而顶部语法 chip `filename:`/`content:`/`all:`/`?` 只是把前缀前插进输入框（addSyntax），**并不更新 `currentLayer` / `currentLayerFilter` / 单选 UI**。后端 `router.py:128-142` 又会按前缀解析出真实模式，于是出现：侧栏单选仍高亮「智能（auto）」，但 `list-header` 的 `modeBadge` 显示「文件名/全文/语义」，结果计数口径也按真实模式。此外 `loadUISettings` 第 1061-1065 行把 `currentLayer` 设为 `defaultMode`，却把 `currentLayerFilter` 重置为 `'auto'`——若用户在设置里把默认模式设为「文件名」，刷新后单选高亮「智能」、实际却按「文件名」搜。
  → 建议：语法 chip 触发后调用 `applyLayerFilter(对应层)` 同步单选 UI；或 chip 仅作提示、点击时直接切换单选。加载设置时让 `currentLayerFilter = currentLayer`。
  → ⚠️ 需浏览器实测确认：依次点击 `filename:` chip → 回车，观察侧栏单选与 modeBadge 是否分歧。

- [index.html:562 + 1578-1585] **`sort:` 语法 chip 改变真实排序，但 `#sortSelect` 下拉不联动。**
  `addSyntax('sort:time ')` 把 `sort:time` 前插进查询框，后端 `router.py:95-114` 解析并生效；但侧栏「排序」下拉仍停在「相关度」。两处排序状态互相矛盾。
  → 建议：chip 写入后同步 `currentSort` 并设置 `#sortSelect.value`；或弃用 `sort:` chip，仅保留下拉作为唯一排序入口。

---

## P2 体验缺陷

### 对比度 / 可读性（WCAG AA 4.5:1 目标）
- [index.html:310 + 311] **暗色主题次要文字对比度不足。** `.hit-path` 用 `#5a5f68`（对 `#12141a` 约 **2.8:1**）、`.hit-meta` 用 `#4a4f58`（约 **2.2:1**），远低于 4.5:1。文件路径与「大小/时间/相关度」属于有效信息，弱对比在强光/弱光环境难读。
  → 建议：`.hit-path` 提至 `#8a8f98` 以上、`.hit-meta` 提至 `#6b7280` 以上（预览区 snippet 已是 `#8a8f98`，可对齐）。
- [index.html:373-376 + 399] **暗色占位/空态/加载文本对比度过低。** `.preview-placeholder` 用 `#3a3e47`（约 **1.7:1**），「选择左侧结果查看内容预览」「快捷键提示」等引导文案近乎不可见；`.empty-state .text`/`list-loading .msg` 用 `#5a5f68`（约 2.8:1）。
  → 建议：占位文案提到 `#6b7280`~`#8a8f98`。
- [index.html:157 + 152] **亮色主题两处缺少 light 覆盖，对比度失败。** 顶部 `.status`（L1/L2/L3 计数，可点击）与 `.hint` 纯标签「语法:」在全局为 `#9ca3af`，亮色白底下约 **2.4:1**，未达 AA；而 `.hint code` 芯片本身有 light 覆盖（line 28），仅裸露标签遗漏。
  → 建议：补 `body.light-theme .status { color:#6b7280; }` 与 `body.light-theme .hint { color:#6b7280; }`。

### 可访问性（键盘 / 焦点 / aria）
- [index.html:1602 + 2057-2105] **结果列表项不在 Tab 序列、缺 listbox/option 语义。** `.hit` 是 `div + onclick`，无 `tabindex`、无 `role`，仅当搜索框聚焦时方向键可用（`isSearchFocused` 限制，2090-2097）。焦点移出搜索框后无法用键盘再进入结果区。
  → 建议：结果列表加 `role="listbox"`、`.hit` 加 `role="option"` 与 roving `tabindex`，使结果可被 Tab 进入并用方向键浏览。
- [index.html:577-582 + 1533/1540/1548] **radiogroup 未实现 roving tabindex。** 5 个 `role=radio` 全部 `tabindex=0`，破坏单选用 Tab 进入一组、方向键切换的规范模式（应仅选中项可 Tab）。
  → 建议：仅 `aria-checked=true` 的项 `tabindex=0`，其余 `-1`，并支持方向键切换。
- [index.html:701 + 1910-1917] **设置弹窗缺对话框语义与焦点管理。** `.modal-overlay` 无 `role="dialog"`/`aria-modal="true"`，打开时未把焦点移入、关闭后未归还、也无焦点陷阱；屏幕阅读器用户易迷失。
  → 建议：加 `role="dialog" aria-modal="true" aria-labelledby`，打开聚焦首个可聚焦控件，Esc/遮罩关闭后焦点回触发按钮，Tab 循环限制在弹窗内。
- [index.html:620] **搜索框无关联 `<label>`。** 仅有 `placeholder`，屏幕阅读器无稳定可访问名（autofocus 可接受，但建议加 `aria-label="搜索"` 或视觉隐藏 label）。

### 响应式 / 布局
- [index.html:546-553 + 99-101] **移动端顶栏可能横向溢出。** `.header-top` 未 `flex-wrap`，且 `h1`、`sub` 均 `white-space:nowrap`，在 <~480px 窄屏（手机）下「🔍 OmniFind v0.2 + 文件名+全文+语义 + 两个按钮」易超出视口，产生横向滚动条。
  → 建议：`.header-top` 加 `flex-wrap:wrap`；窄屏下 `.sub` 可隐藏或换行。
  → ⚠️ 需浏览器实测确认：用 DevTools 设 360px/390px 视口观察顶栏是否溢出。
- [index.html:122] **搜索历史下拉 `right:68px` 硬编码。** 依赖「搜索」按钮固定宽度；按钮进入 `loading` 态（spinner，文字透明）时宽度可能变化，下拉右侧留白错位。
  → 建议：用 `right` 跟随按钮实际宽度或改为 `width:100%`。

### 一致性
- [index.html:948-964 + 1217] **关键词高亮未剥离 `ext:`/`sort:` 前缀。** `extractKeywords` 仅识别 `filename/content/all/re/name` 前缀，`ext:`/`sort:` chip 触发后，高亮会把字面量「ext:.py」「sort:time」当关键词尝试匹配标题，产生无意义高亮。
  → 建议：`extractKeywords` 正则同步剔除 `ext:`/`sort:` 前缀（与 `router.py` 口径一致）。

---

## P3 优化建议

- [index.html:182-194] **死代码：`.filter-tags.segmented` 整段样式在 HTML 中无使用**（侧栏用的是 `.sidebar-layers`）。可删除，或若计划改分段控件样式再启用。
- [index.html:92 + 502-505] **`.score-bar`/`.score-fill` 仅 light 覆盖存在，HTML 未使用**；与之类似的死样式统一清理，降低维护噪声。
- [index.html:1107-1109] **`auto` 主题不监听系统变化。** `applyTheme('auto')` 仅在调用时读一次 `prefers-color-scheme`；用户中途切换系统深浅不会实时更新。建议加 `matchMedia('(prefers-color-scheme: light)').addEventListener('change', ...)`。
- [index.html:1296 + 1563] **`doSearch(opts)` 的 `opts.resetFilter` 参数从不被读取**（applyLayerFilter 传入但函数体内忽略）。删除或真正实现。
- [index.html:1494] **`updateResultSummary(_c, hasMore)` 第二参数未使用**。清理签名。
- [index.html:157-158] **`.status` 标 `cursor:pointer` 但 `:hover` 无视觉反馈**（hover 色与原色相同）。加下划线/变色提示可点。
- [index.html:1766-1812] **预览错误/占位大量内联 `style`**。建议抽成 `.preview-error`/`.preview-dir` 等类，便于主题统一与复用。
- 架构建议：语法 chip（`filename:`/`content:`/`all:`/`?`/`ext:`/`sort:`）与侧栏「搜索模式单选 + 排序下拉 + 类型标签」功能重叠且状态分叉（见 P1）。建议统一为单一状态源：chip 仅作「快捷写入并同步单选 UI」，避免双入口状态不一致。

---

## 亮点

- **亮暗主题完成度高**：暗/亮/跟随系统三选项齐备；light-theme 覆盖广泛，且专门修复了暗色高亮 `#ffe066` 在白底不可见的问题（line 80-83、86、329-330），亮色改用深琥珀 `#92400e`，对比度达标。
- **搜索请求防串台**：`searchReqId` 自增 + `AbortController` 中止旧请求（1292-1364），快速连点/慢回包不会旧结果覆盖新结果。
- **分页精准**：server 端「多取 1 条」判断 `has_more`（server.py:106-114），前端「加载更多」不会再请求越界首屏。
- **高亮语义清晰**：FTS5 snippet 私有区标记 U+E000/1 转 `<span class="hl">` 叠加关键词高亮（967-981），真高亮而非裸方括号。
- **焦点可见性**：`:focus-visible` 全局蓝色描边（13），键盘用户可辨当前焦点。
- **键盘可达设计**：语法 chip / 分段控件 / 类型标签均 `tabindex=0` + `Enter/Space` 触发（keyAct 1242），方向键选择结果（2057-2105）。
- **预览信息完整**：来源标注（file_read/docx_extract/pdf_fitz）、截断提示、编码与大小信息齐全（1796-1807），错误态（目录/二进制/超大）有专门文案而非裸报错。
- **结果计数口径自洽**：`updateResultSummary` 直接用后端 `{total,l1,l2,l3}`（1493-1504），消除了「智能 30 < 文件名 528」式口径矛盾；类型标签数字来自 `ext_facets`（1508-1526），与点击后「共 N 条」一致。
