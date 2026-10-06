# infra-monitor 配置化接入迁移记录（Phase 13 · 第 2 个应用）

> **定位**：本文件是 infra-monitor 从「手写接入胶水」迁移到「三处配置 + 一行装配」的落地记录与验证证据。
> **上游**：`marschat-components/docs/PHASE13-CONFIG-DRIVEN-ONBOARDING.md`（设计与全平台口径）
> **前一个试点**：`kb-ops/docs/PHASE13-配置化接入迁移记录.md`
> **日期**：2026-10-06 ｜ 状态：**代码完成 + 本地验证通过，待发版部署与真浏览器回归**

---

## 1. 为什么第 2 个选 infra-monitor

kb-ops 是纯 SSO（`passthrough`），只验证了最简单的凭据形态。infra-monitor 覆盖 kb-ops **没验证到的能力**：

| 维度 | kb-ops | infra-monitor |
|---|---|---|
| 凭据模式 | `passthrough` | **`auto`（双会话）** —— SSO 透传 + 账密/邮箱码回落中心会话表 |
| 中心会话存储 | 无 | **`CenterSessionStore`**（从手写 71 行 → auth-core 公共实现） |
| 账号上报 | 无本地账号 | **`BffAccountSource` SPI**（取代手写 95 行 `LocalAccountReporter`） |
| 部署形态 | 容器 | **host 网络**（中心走 `127.0.0.1:8085`） |
| 反代层数 | `/ops-api/` → `/kb-ops/` | `/infra/api/` → `/infra/`（**剥一层 `/api`**，坑 #26） |
| 白名单宽度 | 9 条（含映射/菜单授权） | **5 条**（未挂那些面板，故意更窄） |

> 结论：infra 才是「公共 BFF 装配能否覆盖复杂形态」的真正试金石。

---

## 2. 改动清单

### 2.1 后端（`infra-monitor-server`）

| 动作 | 文件 | 说明 |
|---|---|---|
| **删除** | `controller/AdminProxyController.java` | **295 行** 手写白名单 + 凭据解析 |
| **删除** | `service/CenterSessionStore.java` | **71 行**（与 activecode 那份除 package 行外逐字节相同） |
| **删除** | `config/LocalAccountReporter.java` | **95 行** 账号上报 |
| **新增** | `config/MarschatBffConfig.java` | **约 50 行**：只提供两个扩展 Bean（`BffLocalTokenClassifier` 包 `JwtUtil`、`BffAccountSource` 读 `infra.admin.username`） |
| **新增** | `resources/bff-whitelist.yml` | 5 条规则，语义逐条对齐旧 `isAllowed()` |
| 修改 | `controller/AuthController.java` | `CenterSessionStore` 的 import 改指 `com.marschat.auth.bff.CenterSessionStore`（**实现未变，只是归属地上移**） |
| 修改 | `resources/application.yml` | 新增 `marschat.bff.*`；**移除** `marschat.account.report.*`（已并入 BFF 的 `report-secret` + `account-report-enabled`） |
| 修改 | `pom.xml` | `auth-core` 2.1.6 → **2.2.0** |

**净减：约 410 行手写 Java**（295 + 71 + 95 − 50）。

> 注意 `AuthController` **保留不动**（除 import）：账密 / 邮箱码的 BFF 转发是本应用的业务逻辑，
> 不属于「公共接入面」。这正是迁移的边界 —— **只迁管道，不迁业务**。

### 2.2 前端（`infra-monitor-web`）

| 动作 | 文件 | 说明 |
|---|---|---|
| **删除** | `src/utils/sso.ts`（126 行）· `src/utils/permissions.ts`（102 行）· `src/utils/token.ts`（36 行） | 三个「兼容转发壳」，实现全在公共包 |
| **新增** | `src/marschat.ts` | 一行装配 `createMarschatApp({ ...APP_OPTIONS, router, menus, autoRoutes: false })` |
| 重写 | `src/config.ts` | 由 app-kit 纯函数 `resolveAppConfig` 派生；成为 config + SSO client + 令牌读写的**统一出口** |
| 重写 | `src/main.ts` | 53 行手工接线 → 只负责创建应用与挂载顺序 |
| 修改 | `src/router/index.ts` | 守卫注册上移到装配层；`permCode`/`getToken` 改从 `@/config` 取 |
| 修改 | `src/utils/request.ts` | 引用改 `@/config`；本地 `currentSpaPath()` 上移到 config |
| 修改 | `stores/user.ts` · `views/{login,sso,users}/*.vue` · `layouts/MainLayout.vue` | 引用点重定向到 `@/config` |
| **修改** | `tsconfig.json` | **移除两条指向 monorepo 源码的路径别名**（见 §4.1，修复 vue-tsc 崩溃） |
| 修改 | `package.json` | 新增 `@marschat/app-kit: ^0.1.2`；新增 `typecheck` 脚本 |

**净减：约 264 行前端适配层**。

**未改动**：所有业务页、`api/*`、`menus.ts`、`MainLayout` 的模板与样式、`AuthController` 的登录逻辑。

### 2.3 关键配置（`marschat.bff`）

```yaml
marschat:
  bff:
    enabled: true
    client-id: marschat-inframon
    auth-center-base: http://127.0.0.1:8085   # host 网络 → 中心端口已发布到宿主
    path-prefix: /api/admin
    credential-mode: auto                     # ← 双会话：SSO 透传 / 账密回落会话表
    report-secret: ${MARSCHAT_ACCOUNT_REPORT_SECRET:${MARSCHAT_MENU_REPORT_SECRET:}}
    account-report-enabled: true
```

---

## 3. 验证证据（2026-10-06 本机实测）

| # | 验证项 | 命令 | 结果 |
|---|---|---|---|
| 1 | 前端类型检查 | `vue-tsc --noEmit` | **未崩溃**，5 个错误且**全部为存量**（本次迁移零新增） |
| 2 | 前端构建 | `vite build` | **EXIT=0** |
| 3 | 后端编译 | `mvn compile` | **EXIT=0** |
| 4 | 后端打包 | `mvn package` | **EXIT=0**；jar 含 `BOOT-INF/lib/auth-core-2.2.0.jar` + `BOOT-INF/classes/bff-whitelist.yml` |
| 5 | 依赖解析 | `mvn dependency:tree` | `com.marschat:auth-core:jar:2.2.0:compile` |
| 6 | **白名单等价性** | `docs/verify/VerifyBff.java` | **29 / 29 通过** |
| 7 | 令牌键保留 | app-kit `deriveTokenKeys` | `infra_access_token` / `infra_token_kind` ✅（老用户登录态不丢） |
| 8 | 产物落地 | `grep dist/` | `__MARSCHAT_APP_BASE__` / `sso-callback` / `app-config.json` 均命中 |

### 3.1 白名单等价性（关键差异点）

```
[加载] 规则数=5
  - ANY /admin/clients/{clientId}/** [client-in-path]
  - GET /admin/users [client-scope]
  - GET|PUT /admin/users/{id}/client-roles [client-scope]
  - GET|PUT /admin/users/{id}/menu-overrides [client-scope]
  - GET /admin/roles
infra-monitor 白名单迁移等价性：29 通过 / 0 失败（共 29 项）
```

**故意覆盖与 kb-ops 的差异**：infra 未挂「账号映射 / 菜单授权」面板，故这三条**未登记 → 必须拒绝**：

| 端点 | kb-ops（已挂面板） | infra-monitor（未挂） |
|---|---|---|
| `GET /admin/mappings` | ALLOW | **DENY** ✅ |
| `GET /admin/permissions` | ALLOW | **DENY** ✅ |
| `GET /admin/roles/{id}/permission-codes` | ALLOW | **DENY** ✅ |

这证明「**只登记实际用到的端点**」这条原则被正确执行 —— 白名单没有因为迁移而被无脑放大。

---

## 4. 迁移中发现并修复的问题

### 4.1 🔴 `tsconfig.json` 的源码别名导致 `vue-tsc` **直接崩溃**（本应用长期无法类型检查）

**现象**：`vue-tsc --noEmit` 抛 TypeScript 内部错误：

```
Error: Debug Failure. No error for last overload signature
    at resolveCall (typescript/lib/tsc.js:...)
```

**根因**：`tsconfig.json` 的 `paths` 里有两条别名指向 **monorepo 源码**：

```json
"@marschat/auth-components": ["../../../marschat-components/packages/auth-components/src"],
"@marschat/frontend-common":  ["../../../marschat-components/packages/frontend-common/src"]
```

→ vue-tsc 去**类型检查组件库的源码**（连带其已知的 Element Plus 类型债）→ TS 崩溃。

**定位过程（关键在「用 HEAD 版本对照」+ 二分）**：

| 实验 | 结果 |
|---|---|
| 我的改动 + 完整依赖树 | 崩溃 |
| **HEAD 原版 + 完整依赖树** | **同样崩溃** → 与我的改动无关 |
| kb-ops（同 TS 5.9.3 / vue-tsc 2.2.12，**无源码别名**） | 正常 27 错误，不崩 → 环境无问题 |
| 移除两条源码别名后 | **不崩溃**，5 个错误 |
| **只加回 `auth-components` 别名** | **崩溃** ← 触发源在 auth-components 源码 |
| portal（**同样有这两条别名**） | 不崩溃，19 错误 |

**结论（精确表述）**：别名是**必要条件但非充分条件**。
根因是「vue-tsc 经别名把 **auth-components 的源码**拉进类型检查，其中某处表达式让 TS 5.9.3 内部崩溃」；
是否真的崩，取决于该应用**还额外拉进了哪些文件**（portal 拉了同样的源码却不崩）。
→ 精确到文件级的触发点**待查**（登记为 `T-LOW-18`），但这不影响应用侧的正确修复：**移除源码别名**。

**为什么长期没被发现**：`package.json` 的 `build` 脚本只有 `vite build`（**不做类型检查**），
且没人单独跑过 `vue-tsc`。

**修复**：移除两条源码别名。三个 `@marschat/*` 统一从 `node_modules` 解析（与 kb-ops 同口径）。
**语义上也更正确** —— 应用是**消费者**，类型检查应对着「实际依赖并会打包进去的产物」，
而不是开发机的源码快照（否则本地通过、线上跑的是另一套类型）。

**附带**：新增 `"typecheck": "vue-tsc --noEmit"` 脚本，让这个能力可被调用。
⚠️ 暂**未**接入 `build`（当前有 5 个存量错误，接入会挂流水线）→ 见 §5 待办。

**与 kb-ops 的对比很有说明性**：kb-ops 的 tsconfig 没有源码别名，所以它**能**类型检查（27 个存量错误）；
infra 有别名，所以它**完全不能**。**同一套组件、两种接入姿势 → 两种质量水位** ——
这正是「接入面未收敛」的代价。

> ⚠️ **portal 也有这两条别名**（`devtools/portal/tsconfig.json`），只是当前恰好没触发崩溃。
> 建议 portal 迁移时一并移除（T-LOW-18），别等它某天开始崩。

### 4.2 其他

- 无新增缺陷。infra 的 `utils/request.ts` 是**自研 axios 实例**（不是 `createRequest`），
  故 **T-LOW-16（`hooks` 参数被忽略）不适用**；其 401 分流逻辑本就是对的。
- infra 的 `SecurityConfig` **已有**显式 401 entry point（2026-09-14 就修过），**T-LOW-17 不适用**。

---

## 5. 待办（未完成，勿误判为已上线）

| # | 事项 | 说明 |
|---|---|---|
| 1 | **发版部署** | 代码仅在本地；走 Woodpecker（infra 前端 + 后端），**核对线上 chunk hash 与 jar 内 auth-core 版本**（坑 #24） |
| 2 | **真浏览器回归** | 五通道：SSO 免登 · **账密登录后访问用户管理页**（验证 `auto` 模式的会话表回落）· 401 静默续期 · 接口闸门三态 · SLO 联动 |
| 3 | 观察期 | `MARSCHAT_BFF_ENABLED=false` 是回滚开关；⚠️ **前端后端必须同版本回滚**（前端已无手写代理，只回后端会让用户管理页 404） |
| 4 | **修掉 5 个存量类型错误** | `MainLayout.vue` 模板 4 处（`ICONS[...]` 索引可能 undefined）+ `LoginView.vue` 1 处（`brand.gradient` 需为 `[string, string]` 元组）。修完可把 `typecheck` 接入 `build` 门禁 |
| 5 | 账号上报回归 | 启动日志应有「[账号上报] 成功：1 个本地账号已登记」；确认中心 `app_account_mapping` 有 `infra.admin.username` |
