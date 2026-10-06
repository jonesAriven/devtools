# activecode 配置化接入迁移记录（Phase 13 · 第 3 个应用 · 边界案例）

> **定位**：本文件是 activecode 的迁移落地记录。**它同时是「配置化接入」适用边界的实证** ——
> 后端完全可迁（且是本轮唯一需要**首次引入 auth-core** 的应用），**前端则完全不适用 `@marschat/app-kit`**。
> **上游**：`marschat-components/docs/PHASE13-CONFIG-DRIVEN-ONBOARDING.md`
> **前两个应用**：`kb-ops/` · `infra-monitor/` 的 `docs/PHASE13-配置化接入迁移记录.md`
> **日期**：2026-10-06 ｜ 状态：**后端代码完成 + 本地验证通过；前端无法迁移（见 §4）**

---

## 1. 与已迁两个应用的本质差异

| 维度 | kb-ops | infra-monitor | **activecode** |
|---|---|---|---|
| 鉴权框架 | Spring Security | Spring Security | **无 Security，自有 MVC 拦截器** |
| 是否已引 auth-core | 是（2.1.6） | 是（2.1.6） | **否 —— 本轮首次引入** |
| 凭据来源 | `Authorization` 头 | 头 + `getUserPrincipal()` | **HttpSession 属性**（`ssoUser`/`loginUser`） |
| 自有重复实现 | 无 | 无 | **有 `MenuRegistryReporter` / `PermissionInterceptor` / `AuthzProperties`** |
| 前端形态 | Vue3 SPA（构建链） | Vue3 SPA（构建链） | **纯静态页（无构建链）** |
| 前端适配层 | 263 行 | 264 行 | **`sso.js` 418 行 + `members.html` 手写** |
| 中心地址 | `auth-center:8085`（容器） | `127.0.0.1:8085`（host） | **`192.168.31.105:8085`（跨主机 LAN）** |
| context-path | `/kb-ops` | `/infra` | **无**（控制器映射完整路径） |

> 结论：**同一个公共 BFF 装配，要在三种截然不同的应用形态上都成立** —— 本轮把这三种都跑通了（后端）。

---

## 2. 后端改动清单（已完成）

| 动作 | 文件 | 说明 |
|---|---|---|
| **删除** | `controller/AdminProxyController.java` | **297 行** |
| **删除** | `service/CenterSessionStore.java` | **71 行** |
| **删除** | `config/LocalAccountReporter.java` | **104 行** |
| **新增** | `config/MarschatBffConfig.java` | **约 90 行**：自定义 `BffCredentialResolver`（HttpSession）+ `BffAccountSource`（`admin_user` 表） |
| **新增** | `resources/bff-whitelist.yml` | 5 条规则 |
| 修改 | `controller/AuthController.java` · `config/PermissionInterceptor.java` | `CenterSessionStore` 的 import 改指 `com.marschat.auth.bff`（**实现未变，归属地上移**）；清理已删类的 javadoc 引用 |
| 修改 | `resources/application.yml` | 新增 `spring.autoconfigure.exclude`（**4 条**）+ `marschat.bff.*`；移除 `marschat.account.report.*` |
| 修改 | `pom.xml` | **首次引入** `com.marschat:auth-core:2.2.0` |

**净减：约 380 行手写 Java**（297 + 71 + 104 − 90）。

### 2.1 必须自定义 `BffCredentialResolver`（本轮的架构要点）

auth-core 内置三种凭据模式（`passthrough` / `session-store` / `auto`），
后两者都用 **`request.getUserPrincipal()`** —— 那是 **Spring Security 语义**。

activecode **没有引入 Spring Security**（鉴权走自有 MVC 拦截器），用户名存在 **HttpSession 属性**里，
`getUserPrincipal()` 恒为 `null` ⇒ **三种内置模式全部失效**。

解法：提供自定义 `BffCredentialResolver` Bean（`@ConditionalOnMissingBean` 保证用户 Bean 优先）。
语义与旧 `resolveCenterToken` 逐字一致：`ssoUser`（SSO/邮箱码径）→ `loginUser.username`（账密径）→ `CenterSessionStore.getAccessToken`。

> **这正是把「凭据解析」做成 SPI 而非硬编码的价值**：公共库不必假设所有应用都用 Spring Security。
> 若当初把它写成 `if/else` 三种模式，activecode 就只能绕过公共库、继续维护自己的副本。

### 2.2 必须排除 4 个自动装配（首次引入 auth-core 的代价）

引入 auth-core 后，其 6 个自动装配中 **4 个会冲突**，必须显式排除：

| 自动装配 | 不排除的后果 | 处置 |
|---|---|---|
| `AuthJwtAutoConfig` | 装配 `TokenProvider`，要求 `marschat.auth.secret` ≥32B；本应用无此配置 → **启动失败** | ❌ 排除 |
| `AuthWebAutoConfig` | 注册 `@MarsUser` 参数解析器（本应用无此用法） | ❌ 排除 |
| `AuthzAutoConfig` | 创建**第二个权限检查器**，与自有 `PermissionInterceptor` 重复拦截 | ❌ 排除 |
| `MenuReportAutoConfig` | 本应用**已有自有 `MenuRegistryReporter`** → **重复上报**菜单与权限点 | ❌ 排除 |
| `AuthFeignAutoConfig` | 条件为类路径存在 `feign.RequestInterceptor` —— 本应用无 OpenFeign | ✅ 保留（**不激活**，已用 `dependency:tree` 核实） |
| `MarschatBffAutoConfig` | —— | ✅ 保留（**目标**） |

**覆盖率已核验**（见 §3）：6 个自动装配全部有明确处置，无遗漏。

---

## 3. 验证证据（2026-10-06 本机实测）

| # | 验证项 | 命令 | 结果 |
|---|---|---|---|
| 1 | 后端编译 | `mvn compile` | **EXIT=0** |
| 2 | 后端打包 | `mvn package` | **EXIT=0**；jar 含 `BOOT-INF/lib/auth-core-2.2.0.jar` + `BOOT-INF/classes/bff-whitelist.yml` |
| 3 | **白名单等价性** | `docs/verify/VerifyBff.java` | **29 / 29 通过** |
| 4 | **自动装配覆盖率** | 逐个比对 `AutoConfiguration.imports` | 6 个全部有处置：4 排除 + 2 保留（其中 Feign 条件不成立） |
| 5 | Feign 条件核实 | `mvn dependency:tree -Dincludes=...openfeign` | **无 openfeign** → `AuthFeignAutoConfig` 不激活 ✅ |

### 3.1 白名单等价性

```
[加载] 规则数=5
  - ANY /admin/clients/{clientId}/** [client-in-path]
  - GET /admin/users [client-scope]
  - GET|PUT /admin/users/{id}/client-roles [client-scope]
  - GET|PUT /admin/users/{id}/menu-overrides [client-scope]
  - GET /admin/roles
activecode 白名单迁移等价性：29 通过 / 0 失败（共 29 项）
```

⚠️ **端点保护方式变了但语义未变**：旧 `AdminProxyController` 与新 `MarschatBffAdminProxyController`
都映射 `/activecode/api/admin/**`，而自有 `AuthInterceptor` 的 `addPathPatterns` 含
`/activecode/api/**` ⇒ **未登录请求仍在进入代理方法之前被拦下**。
（⚠️ 本应用无 Spring Security，保护**完全依赖该拦截器**；迁移前后一致，但这条链路必须在回归中确认。）

---

## 4. 🔴 前端**无法**用 `@marschat/app-kit` 迁移（本轮的边界发现）

### 4.1 事实

| 项 | 现状 |
|---|---|
| 构建链 | **无** `package.json` / `vite.config` —— 纯静态页（`index/login/main/members/sso-callback/downloads.html`） |
| 公共组件引入方式 | **内联 UMD**：`marschat-auth-core.umd.js`（31KB） |
| 自研适配层 | **`sso.js` 418 行**（比任何 SPA 的 `sso.ts` 都大） |
| 用户管理页 | **`members.html` 手写**（直接 `fetch`，未用 `UserManagementPanel`） |
| UMD 自报版本 | `version = "0.8.7"` —— **与 `VENDORED.md` 的 0.8.8 戳不一致**（`T-LOW-3` 仍未闭合） |

### 4.2 为什么 `@marschat/app-kit` 用不上

`app-kit` 是**给有构建链的 SPA 用的 npm 包**，它假定：
1. 有打包器（ESM 解析 `@marschat/*` 依赖）
2. 用 `vue-router`（`createMarschatApp` 要注册路由、挂守卫）

activecode **两条都不满足**：没有打包器，也没有 vue-router（多页原生跳转）。

### 4.3 因此本应用的前端**仍然是手写的** —— 诚实结论

| 层 | 状态 |
|---|---|
| 后端接入面 | ✅ 已配置化（472 行 → 配置 + 90 行扩展类） |
| 前端接入面 | ❌ **未收敛**（`sso.js` 418 行 + 6 个页面各自处理会话） |

**它不满足「引入功能组件 + 少量配置即可接入」**，而且**不是本次实现有缺陷，是路径本身不存在**。

### 4.4 补上这条路径需要什么（建议，未做）

若要让无构建应用也走「配置化接入」，需要新增一个 **UMD 版装配层**（暂称 `app-kit.umd`）：

| 需要的能力 | 与 SPA 版的差异 |
|---|---|
| `createMarschatUmdApp({ appId, pages })` 挂到 `window` | 无 ESM 导入，需全局暴露 |
| 不依赖 vue-router 的「路由」= 页面级会话守卫（`requireLogin()` / `redirectIfAuthed()`） | 用 `location.href` + 页面清单代替 router |
| 页面级模板注入（登录页 / 回调页 / 用户管理页） | 现为 6 个手写 HTML，需抽成可参数化模板 |
| `members.html` 改用 `UserManagementPanel` 的 UMD 出口 | 组件库已出 UMD，但面板未纳入 UMD 入口（见 `auth-components` 的 `umd.ts`） |

**成本估算**：中等偏大（要新增一个分发形态 + 改造 6 个静态页）。
**建议**：列入 Phase 14 评估，**优先级低于把已有 3 个 SPA 应用铺开** —— 毕竟
「1 个无构建应用手写」的绝对成本，远小于「5 个 SPA 各写一套」。

---

## 5. 待办

| # | 事项 | 说明 |
|---|---|---|
| 1 | **发版部署** | 走 Woodpecker（activecode 独立主机 192.168.31.182），核对 jar 内 auth-core 版本 |
| 2 | **启动验证（关键）** | 本应用首次引入 auth-core + 4 条 exclude，**必须确认容器正常启动**且日志无「重复上报」；若 `AuthzAutoConfig` / `MenuReportAutoConfig` 未被正确排除，会出现重复拦截或重复上报 |
| 3 | **真浏览器回归** | 五通道：SSO 免登 · 账密 · 401 · **接口闸门三态**（自有 `PermissionInterceptor` 未动，需确认未受影响）· SLO；另确认 `members.html` 仍可用 |
| 4 | 账号上报 | 启动日志应有「[账号上报] 成功：N 个本地账号已登记」（来源 `admin_user` 表） |
| 5 | 观察期回滚 | `MARSCHAT_BFF_ENABLED=false` 关代理；⚠️ 前端**未迁移**，故本次不存在「前后端版本耦合」问题 —— 这也是它比 kb-ops/infra 回滚更简单的地方 |
| 6 | 前端路径 | 见 §4.4，建议 Phase 14 评估 UMD 版装配层 |
