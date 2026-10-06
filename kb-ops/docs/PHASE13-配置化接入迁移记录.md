# kb-ops 配置化接入迁移记录（Phase 13 试点）

> **定位**：本文件是 kb-ops 从「手写接入胶水」迁移到「三处配置 + 一行装配」的落地记录与验证证据。
> **上游**：`marschat-components/docs/PHASE13-CONFIG-DRIVEN-ONBOARDING.md`（设计与全平台口径）
> **日期**：2026-10-05 ｜ 状态：**代码完成 + 本地验证通过，待发版部署与真浏览器回归**

---

## 1. 为什么选 kb-ops 做试点

| 判据 | kb-ops 的情况 |
|---|---|
| 接入形态 | 纯 SSO 应用（`showLocalLogin: false`），无本地密码体系 → 迁移面最窄 |
| 凭据模式 | 浏览器持有的就是中心 OIDC token → `credential-mode: passthrough` 与现行为**完全一致** |
| 风险 | 无账密/邮箱码通道 → 不需要触碰「中心不可达 fail-closed」等敏感路径 |
| 收益 | 后端手写 `AdminProxyController` 266 行 → 一份 60 行 yml；前端 4 个适配层 263 行 → 1 个装配文件 |

---

## 2. 改动清单

### 2.1 后端

| 动作 | 文件 | 说明 |
|---|---|---|
| **删除** | `src/main/java/com/kb/ops/controller/AdminProxyController.java` | 266 行手写白名单 + 凭据透传 |
| **新增** | `src/main/resources/bff-whitelist.yml` | 9 条规则，语义逐条对齐旧 `isAllowed()` |
| 修改 | `src/main/resources/application.yml` | 新增 `marschat.bff.*`（`enabled` / `client-id` / `auth-center-base` / `path-prefix: /admin` / `credential-mode: passthrough`） |
| 修改 | `pom.xml` | `auth-core` 2.1.6 → **2.2.0** |
| **修复** | `config/SecurityConfig.java` | 补**显式 401 entry point**（见 §4.1） |

### 2.2 前端

| 动作 | 文件 | 说明 |
|---|---|---|
| **删除** | `src/utils/sso.ts`（135 行）· `src/utils/permissions.ts`（91 行）· `src/utils/token.ts`（37 行） | 三个「兼容转发壳」，实现全在公共包 |
| **新增** | `src/marschat.ts` | 一行装配 `createMarschatApp({ ...APP_OPTIONS, router, menus, autoRoutes: false })` |
| 重写 | `src/config.ts` | 由 app-kit 纯函数 `resolveAppConfig` 派生；成为 config + SSO client + 令牌读写的**统一出口** |
| 重写 | `src/main.ts` | 53 行手工接线 → 只负责创建应用与挂载顺序 |
| 修改 | `src/router/index.ts` | 守卫注册上移到装配层；`permCode`/`getToken` 改从 `@/config` 取 |
| 修改 | `src/utils/request.ts` | 引用改 `@/config`；**修复 `hooks` 参数失效**（见 §4.2） |
| 修改 | `stores/user.ts` · `views/login/LoginView.vue` · `views/sso/SsoCallbackView.vue` · `views/users/UsersView.vue` · `layouts/MainLayout.vue` | 引用点重定向到 `@/config` |
| 修改 | `package.json` | 新增 `@marschat/app-kit: ^0.1.2` |

**未改动**：所有 `views/*` 业务页、`api/*`、`menus.ts`、`MainLayout` 的模板与样式。
路由结构、登录页 UX、用户管理页页签**保持原样** —— 本次只迁移「管道」，不动业务。

### 2.3 关键设计：为什么 `config.ts` 不 import `@/router`

`config.ts` 被 router / stores / api / views 广泛引用；若它依赖 router，会形成
`router → MainLayout → stores → api → request → config → router` 的**循环依赖**
（模块初始化顺序不确定，`permCode` 可能拿到 undefined）。
需要 router 的装配单独放 `src/marschat.ts`，且只有 `main.ts` 引用它。

---

## 3. 验证证据（2026-10-05 本机实测）

| # | 验证项 | 命令 | 结果 |
|---|---|---|---|
| 1 | 前端类型检查 | `vue-tsc --noEmit` | 错误数 **28 → 27**（新增的 1 个已消除；剩余 27 个为存量，位于未改动的 view 文件） |
| 2 | 本次改动文件类型检查 | 同上，按文件过滤 | **零类型错误** |
| 3 | 前端构建 | `vite build` | **EXIT=0**，产物正常 |
| 4 | 后端编译 | `mvn compile` | **EXIT=0** |
| 5 | 依赖解析 | `mvn dependency:tree` | `com.marschat:auth-core:jar:**2.2.0**:compile` |
| 6 | 后端打包 | `mvn package` | **EXIT=0**；`target/kb-ops.jar` 含 `BOOT-INF/lib/auth-core-2.2.0.jar` + `BOOT-INF/classes/bff-whitelist.yml` |
| 7 | **白名单等价性** | `docs/verify/VerifyBff.java` | **34 项通过 / 0 失败**（见下） |

### 3.1 白名单等价性验证（关键）

用**已发布的 auth-core 2.2.0** 加载 kb-ops 真实 `bff-whitelist.yml`，
逐条比对旧 `AdminProxyController.isAllowed()` 的判定：

```bash
cd kb-ops
mvn -o dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "target/classes;$(cat target/cp.txt)" docs/verify/VerifyBff.java
```

```
[加载] 规则数=9
  - ANY /admin/clients/{clientId}/** [client-in-path]
  - GET /admin/users [client-scope]
  - GET|PUT /admin/users/{id}/client-roles [client-scope]
  - GET|PUT /admin/users/{id}/menu-overrides [client-scope]
  - GET /admin/roles
  - ANY /admin/mappings
  - ANY /admin/mappings/**
  - GET /admin/permissions [client-scope]
  - GET|PUT /admin/roles/{id}/permission-codes
kb-ops 白名单迁移等价性：34 通过 / 0 失败（共 34 项）
```

覆盖：允许面 16 项（成员/角色/菜单减法/映射/菜单授权/path 化端点）·
越界拒绝 5 项（查询参数越界 · **HPP 参数污染** · 路径段越界）·
平台级写端点拒绝 7 项（建/改/删身份 · 重置口令 · 建角色）·
内网通道与运维端点拒绝 4 项 · 方法不匹配 2 项。

---

## 4. 迁移中发现并修复的两处真实缺陷

### 4.1 kb-ops 缺显式 401 entry point（坑 #2）

**现象**：`SecurityConfig` 未配 `exceptionHandling`，Spring Security 默认走
`Http403ForbiddenEntryPoint` → **未携带 token** 的请求返回 **403**；
而 `JwtAuthenticationFilter` 对**无效 token** 返回 **401**。同一语义两种状态码。

**后果**：前端 401 拦截器只认 401，遇 403 既不续期也不跳登录 → 页面「假死」。

**修复**：补显式 401 entry point，响应体格式与 filter 的 `sendUnauthorized` 一致。
（项目 README Level 4 检查清单 C 组已明列为必检项，此前漏做。）

### 4.2 前端 401 静默续期分流是**死代码**

**现象**：`utils/request.ts` 写作
`createRequest({ hooks: { onError, onUnauthorized } })`，
但 `@marschat/frontend-common@0.3.5` 的 `CreateRequestOptions` **没有 `hooks` 字段**
（已发布产物中 `hooks` 出现 **0 次**，实测 `grep -c hooks dist/*.js` = 0）。

**后果**：两个回调被**静默忽略**，落到默认实现
`onUnauthorized = () => { clearTokens(); location.href = appPath('/login') }`
→ 精心注释的「OIDC 静默重授权」分支**从未执行**，OIDC 会话过期一律硬踢登录页。

**修复**：按正确签名放到顶层 `onError` / `onUnauthorized`。
**这是行为变更**，需在回归中重点验证「token 过期 → 静默续期无感」而非「跳登录页」。

---

## 5. 待办（未完成，勿误判为已上线）

| # | 事项 | 说明 |
|---|---|---|
| 1 | **发版部署** | 代码仅在本地；需走 Woodpecker 流水线（kb-ops 前端 + 后端），**并核对线上 chunk hash 与 jar 内 auth-core 版本**（坑 #24：流水线 SUCCESS ≠ 产物已更新） |
| 2 | **真浏览器回归** | 五通道：SSO 免登（口令输入次数==1）· 401 静默续期（§4.2 行为变更）· 接口闸门三态 · SLO 联动 · 用户管理页可用且无平台级按钮 |
| 3 | 观察期 | `MARSCHAT_BFF_ENABLED=false` 是回滚开关（关掉即回到「无管理代理」状态；注意此时用户管理页会 404，回滚需同步回滚前端） |

> ⚠️ 回滚注意：前端与后端**必须同版本回滚** —— 前端已不再持有手写代理，只回滚后端会让用户管理页 404。
