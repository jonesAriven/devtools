# kb-web 配置化接入迁移记录（Phase 13 · 第 4 个应用）

> **定位**：kb-web 从「手写接入胶水」迁移到「三处配置 + 一行装配」的落地记录。
> **上游**：`marschat-components/docs/PHASE13-CONFIG-DRIVEN-ONBOARDING.md`（设计与全平台口径）
> **日期**：2026-10-08 ｜ 状态：**已上线**（Woodpecker 流水线 #838 SUCCESS）
> **提交**：`21319eb1 feat(kb-web): 前端迁移到 @marschat/app-kit —— 删 258 行手写胶水`

---

## 1. 接入形态（形态决定装配参数）

| 维度 | kb-web 的实际情况 | 对装配的约束 |
|---|---|---|
| 会话模式 | **双模**（SSO + 账密并存） | `watchSession` 不能写死 `true`（见 §3.3） |
| 路由 | 嵌套 `MainLayout` + **匿名路由** `/share/:code` | `autoRoutes: false`，保留既有路由表 |
| 菜单 | `createKbMenus(ctx)` 依赖 pinia | 只能在 `setup()` 里求值，**不能**在装配参数里传 |
| 后端 | 本轮**未改**（Phase 12 已完成 BFF 接入） | 本轮纯前端迁移 |

---

## 2. 改动清单（实测：`git show --numstat 21319eb1 -- mykng/kb-web/src`）

### 2.1 删除的手写胶水 —— 合计 **258 行**

| 文件 | 删除 |
|---|---|
| `src/utils/sso.ts` | **130** |
| `src/utils/permissions.ts` | **92** |
| `src/utils/token.ts` | **36** |

### 2.2 新增 / 改写

| 文件 | + | − | 说明 |
|---|---|---|---|
| `src/marschat.ts` | 54 | 0 | **一行装配**：`createMarschatApp({ ...APP_OPTIONS, router, runtime, autoRoutes: false, watchSession: false })` |
| `src/config.ts` | 202 | 30 | 由 app-kit 纯函数派生，成为 config + SSO client + 令牌读写的**统一出口** |
| `src/main.ts` | 28 | 18 | 手工接线 → 只负责创建应用与挂载顺序 |
| `src/router/index.ts` | 7 | 6 | 守卫注册上移装配层；`permCode`/`getToken` 改从 `@/config` 取 |
| `src/stores/user.ts` | 7 | 6 | 同上 |
| `src/api/index.ts` · `layouts/MainLayout.vue` · `views/login/LoginView.vue` · `views/settings/UsersView.vue` · `views/sso/SsoCallbackView.vue` · `utils/errorReporter.ts` | 小计 +28 | −24 | 引用点重定向到 `@/config` |

**未改动**：所有业务页模板与样式、路由结构、登录页 UX、用户管理页页签 —— 只迁管道，不动业务。

---

## 3. 四个必须显式传的参数（传错都是**静默故障**）

### 3.1 `tokenKeyPrefix: 'kb_'` —— 不传则登录态丢失

不传时装配层按应用名派生出 `kbweb_*` 前缀，与既有 `kb_*` 不一致 ⇒ **已登录用户被判未登录**。
这不是编译错误、也不是运行时异常，只是"刷新后掉登录"。

### 3.2 `homePath: '/dashboard'` —— 不传则低频路径白屏

`homePath` 的类型是 `string`（**不是字面量联合类型**），所以公共包改默认值时 **vue-tsc 零报错**。
判据只有一个：**该路由是否存在于本应用路由表**（kb-web 有 `/dashboard`，cosmic / portal 没有 ⇒ 必须传 `'/'`）。

### 3.3 `watchSession: false` —— 双模应用的血泪参数

账密 / 邮箱码会话浏览器侧**没有 IdP 会话**，会话监视器探针恒返回 `authenticated:false`
⇒ 2026-09-15 的"整页刷新 3 秒即被踢回 `?slo=1`"事故。双模应用正确写法是
`watchSession: () => isOidcToken()`（kb-web 此处按既有语义取 `false`，SSO 路径由会话渠道标记单独驱动）。

### 3.4 `autoRoutes: false` —— 保留既有路由

`autoRoutes: true` 会由装配层生成路由，覆盖掉 `/share/:code` 这类**匿名分享路由**与嵌套 `MainLayout`。

---

## 4. 关键设计：`config.ts` 不能 import `@/router`

`config.ts` 被 router / stores / api / views 广泛引用；一旦它依赖 router，就形成
`router → MainLayout → stores → api → request → config → router` 的**循环依赖**
（模块初始化顺序不确定，`permCode` 可能拿到 `undefined`）。

**约定**：需要 router 的装配只放 `src/marschat.ts`，且**只有 `main.ts` 引用它**。

---

## 5. 验证证据

| # | 验证项 | 方式 | 结果 |
|---|---|---|---|
| 1 | 前端构建 | Woodpecker #838 | ✅ SUCCESS |
| 2 | 产物一致性 | 服务端入口 chunk 与构建产物比对 | ✅ 一致（部署后核验，禁用 HTTP 缓存） |
| 3 | 手写胶水删除量 | `git show --numstat` | ✅ 258 行（92 + 130 + 36） |
| 4 | 组件版本 | `package.json` | ✅ `@marschat/app-kit: ^0.1.4`（与 cosmic / portal 统一） |

> ⚠️ 上线核验的两个硬要求（否则会误判）：
> ① 浏览器必须 `Network.setCacheDisabled`（常驻 profile 会跑历史 bundle）；
> ② 视口 ≥ 1440×900（否则落到移动端断点，桌面侧边栏 DOM 根本不挂载）。

---

## 6. 遗留

| # | 项 | 说明 |
|---|---|---|
| 1 | 匿名分享路由 `/share/:code` | 本轮靠 `autoRoutes: false` 保住；装配层暂无"匿名路由"概念，建议 Phase 14 在 app-kit 显式支持 |
| 2 | `src/views/LoginView.vue` 既有类型告警 1 处 | `brand.gradient` 为 `string[]` 但组件要求 `[string, string]`；**存量、非本次引入**，CI 不跑 `vue-tsc` 故不阻塞 |
