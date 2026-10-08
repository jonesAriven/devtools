# portal 配置化接入迁移记录（Phase 13 · 第 6 个应用 · 收官）

> **定位**：portal 从「手写接入胶水」迁移到「三处配置 + 一行装配」的落地记录，含**三道闸**与**凭据链路**两次修复。
> **上游**：`marschat-components/docs/PHASE13-CONFIG-DRIVEN-ONBOARDING.md`（设计与全平台口径）
> **日期**：2026-10-08 ｜ 状态：**已上线**（#839 portal-server / #840 portal-web）+ 登出清理待上线（T-ENG-8）
> **提交**：`e6b478f7 feat(portal): 前后端迁移到配置化接入 —— 三道闸齐备、迁移前后无收窄`
> 　　　　`57ddb04f fix(portal): 账密/邮箱码管理员恢复用户管理页 + 三应用统一 app-kit 0.1.4`

---

## 1. 为什么 portal 放在最后一个

| 判据 | portal 的情况 |
|---|---|
| 会话形态 | **双模**（账密 + 邮箱码 + SSO 并存），三种凭据来源都要能进管理面 |
| 后端 | 有**自有 MVC 拦截器**体系（`JwtInterceptor`），不是 Spring Security |
| 历史包袱 | 曾自行代理 `/api/admin/**`（`proxyAdminCenter`），与 BFF 路径**直接撞车** |
| 权限 | 管理面是**三道闸**（本地角色 + SSO 会话 + 中心权限点），迁移中一度被削弱 |

> 结论：portal 是**覆盖面最宽**的一个，前 5 个应用跑通后才敢动它。

---

## 2. 改动清单（实测 `git show --numstat`）

### 2.1 后端 `e6b478f7`

| 文件 | + | − | 说明 |
|---|---|---|---|
| `resources/bff-whitelist.yml` | **200** | 0 | 15 条规则，替代手写白名单判定 |
| `config/MarschatPortalBffConfig.java` | 159 | 0 | 自定义凭据解析器（`marschatPortalBffCredentialResolver`） |
| `config/PortalAdminGateInterceptor.java` | 209 | 0 | **三道闸**（见 §3） |
| `controller/SsoController.java` | 15 | **69** | 删手写 BFF 代理 + 兼容壳 |
| `config/WebMvcConfig.java` | 8 | 0 | 注册闸门拦截器，`order(1)` 排在 `JwtInterceptor` 之后 |
| `resources/application.yml` | 41 | 9 | 新增 `marschat.bff.*` |
| `docs/verify/VerifyBff.java` | 315 | 0 | **58 条静态断言**（50 白名单 + 8 闸门契约） |
| `docs/verify/portal_admin_p0.sh` | 257 | 0 | P0 验收脚本（`GATE_MODE=dual-gate-with-sso-check`） |

### 2.2 前端 `e6b478f7`

| 文件 | + | − | 说明 |
|---|---|---|---|
| `src/marschat.ts` | **66** | 0 | 一行装配 |
| `src/utils/permissions.ts` | 0 | **93** | 手写权限适配层，整文件删除 |
| `src/utils/sso.ts` → `src/config/session.ts` | 51 | 56 | 兼容壳改写为会话模块（能力平移装配层） |
| `src/config/runtime.ts` | 79 | 17 | 运行时配置收口（零依赖，供 `marschat.ts` 引用） |
| `src/stores/user.ts` | 23 | 3 | 令牌/会话存取改走装配层 |
| `src/main.ts` | 15 | 15 | 手工接线 → 只负责创建与挂载 |
| 其余（`api/request.ts` · `router/index.ts` · `LoginView.vue` · `UsersView.vue` · `AdminConsoleView.vue`） | 小计 +20 | −7 | 引用点重定向 |

### 2.3 凭据链路修复 `57ddb04f`

| 文件 | + | − | 说明 |
|---|---|---|---|
| `service/AuthCenterService.java` | **95** | — | 新增 `loginAccessTokens` 池 + `storeLoginAccessToken` / `resolveAccessToken` / `clearUserCredentials` / `hasSsoSession` |
| `controller/AuthController.java` | 9 | 0 | 账密登录后 `storeLoginAccessToken(...)` |
| `controller/SsoController.java` | 4 | 0 | SSO 登录后 `storeLoginAccessToken(...)` |
| `config/MarschatPortalBffConfig.java` | 12 | — | `resolveCenterToken` 改用 `resolveAccessToken(portalUserId)` |
| `resources/bff-whitelist.yml` | 23 | 23 | 边界 ② 注释纠正（见 §4.2） |

---

## 3. 三道闸（`PortalAdminGateInterceptor`）

| # | 判据 | 失败码 / 文案 | 行 |
|---|---|---|---|
| ① | 本地 `role ∈ {admin, superadmin}` | **403**「需要管理员权限」 | :107 |
| ② | `hasSsoSession(userId)`（**纯内存**，零网络） | **401**「无统一认证会话，请使用统一认证登录」 | :115 |
| ③ | 中心权限点 `api:admin` | **401**「无法验证统一认证权限，请稍后重试」 | :123 |

- `superadmin` **恒放行**（`PortalPermissionChecker:92-96`），中心侧另有类级 `@PreAuthorize("hasRole('ADMIN')")`（`AdminUserController:22`）。
- 闸门顺序必须在 `JwtInterceptor` **之后**（依赖它注入的 `userId`）。
- 8 条闸门契约已做**变异测试**：`if(true)` 短路、丢弃返回值两个变异体均被断言捕获。

---

## 4. 迁移中发现并修复的真实缺陷

### 4.1 🔴 提权漏洞：删方法顺手删掉了两道闸

迁移时删除旧代理方法，连带把 ①/③ 两道闸一起删了 ⇒ 任意登录用户可进管理面。
修复：`PortalAdminGateInterceptor` 三道闸重建 + 58 条静态断言锁死。

### 4.2 🔴 错误结论被推翻：账密管理员「天生 401」

曾在白名单注释里写下「仅 SSO 可用、账密返 401」。被质疑后**回源核查 auth-center 源码**：

| 证据 | 位置 |
|---|---|
| `LoginResponse` 含 `accessToken` + `refreshToken` | `dto/LoginResponse.java:13-16` |
| 账密登录与 SSO 换票**走同一个** `jwtTokenProvider` | `service/impl/AuthServiceImpl.java:93-95` |
| 过滤器明确接受 legacy(HS256) 分支并要求 `type == "access"` | `security/JwtAuthenticationFilter.java:50-58` |

⇒ **账密登录的中心 access_token 本就能直调 `/admin/**`**，是 portal 自己的凭据池只认 SSO 的 `refreshTokens`。
修复：新增 `loginAccessTokens` 池 + `resolveAccessToken()`（**两个池绝不能合并**：一个存 refresh_token、一个存 access_token）。
真机复验：**账密 superadmin → 200**。

### 4.3 🔴 `path-prefix` 与 context-path 叠加 ⇒ 全 404

配置 `path-prefix: /admin` 时未考虑 portal 的 context-path，拼出错误路径 ⇒ 所有管理接口 404。

### 4.4 🔴 凭据解析器返回用户名而非 token

`resolveCenterToken` 一度返回 username；`javap` 反证该值会**直接进 `Authorization` 头** ⇒ 全部 401。

### 4.5 `setSession` 漏导出（存量缺陷）

`stores/user.ts` 定义了 `setSession` 却**长期未导出**，而邮箱验证码登录路径在调它
⇒ 运行期 `TypeError: userStore.setSession is not a function` ⇒ **邮箱码登录整条链路不可用**。
经 `git show HEAD:portal/src/stores/user.ts` 确认**迁移前即如此**，非本次引入。

---

## 5. 顺序铁律（改 portal 后端必须遵守）

1. **先删**旧 `proxyAdminCenter`，**再开** `marschat.bff.enabled` —— 两者都映射 `/api/admin/**`，同时存在 ⇒ `Ambiguous mapping` **启动崩溃**。
2. 自定义 Bean 必须带 `marschat` 前缀（如 `marschatPortalBffCredentialResolver`）——
   `@ConditionalOnMissingBean` **只按类型匹配**，同名异类型会 `BeanDefinitionOverrideException` ⇒ crash-loop。

---

## 6. 验证证据

| # | 验证项 | 结果 |
|---|---|---|
| 1 | 后端编译 / 前端构建 | `mvn compile` **EXIT=0**；流水线 **#839 / #840 SUCCESS** |
| 2 | 白名单 + 闸门静态断言 | `VerifyBff.java` **58/58 通过**，变异测试有效 |
| 3 | 运行时加载 | 容器日志 `[BFF 白名单] 已加载 15 条规则`、`[账号上报] 成功：16 个本地账号已登记` |
| 4 | 无凭据访问管理端点 | **401**（安全过滤器先于控制器，不能用 404 判定放行与否） |
| 5 | **真机三态**（superadmin 建号验证后软删） | `role=user` → **403**；`role=admin`(账密) → **401**（闸 ②）；`superadmin`(账密) → **200**；两条日志文案可完全区分 |
| 6 | **真浏览器** | `/portal/` → 自动跳 `/portal/login?reauth=1`；账密登录成功（`portal_token` 存在、`token_kind=legacy`）；`/portal/users` 渲染真实数据（含 admin「超级管理员」）；`CONSOLE=[]` |

> 测试账号 `p13test_user` / `p13test_admin` 已软删（`deleted=1`）。
> ⚠️ 已删用户名是**墓碑**，同名不可重建（Pitfall #20）。

---

## 7. 本次补的两项（T-ENG-7 / T-ENG-8）

### 7.1 T-ENG-7：管理面权限普查（只读，2026-10-08 实测）

| 结论 | 证据 |
|---|---|
| 中心权限点 `api:admin` **存在** | `marschat_auth.sys_permission` id=**2493**（client_id=`marschat-portal`，type=api） |
| 唯一持有者 | 角色 id=**36**（`marschat-portal` / code=`admin`） |
| 唯一被授权用户 | `user_id=1`（`admin` / superadmin）⇒ **非超管的 admin 必然卡在闸 ③** |
| portal 侧活跃 `role=admin` 账号 | 仅 **3 个**：`p9g3adm`(71) · `p9g3adm2`(78) · `p13test_admin`(120)，**全部是测试账号** |
| 这 3 个的中心影子账号 | id 260 / 274 / 498，`deleted=1`，且**无** `marschat-portal` 角色绑定 |

⇒ **现实影响面为零**（无真实业务管理员），但**机制缺口仍在**：将来新建的 `role=admin` 仍会 401。
是否给 `role=admin` 补中心授权，属**生产授权变更**，需人工拍板后执行。

### 7.2 T-ENG-8：登出清服务端凭据（本次实现）

**修复前的双重缺失**：后端 `/api/auth/logout` 是**空实现**；前端 `userStore.logout()` **根本没调它**（`api/auth.ts` 的 `logout()` 是死代码）。
⇒ 用户点了「退出登录」，portal-server 手里那枚可直调 `/admin/**` 的中心 access_token **原封不动**。

补齐后的登出链路：

| 步 | 动作 | 位置 |
|---|---|---|
| ① | 取中心 access_token（账密池优先，其次 refresh 池） | `AuthController#logout` → `resolveAccessToken` |
| ② | 中心 `POST /auth/logout` 拉黑（写 `jwt_blacklist`） | `AuthCenterService#revokeAccessToken` |
| ③ | 清本进程两个凭据池（**无论 ② 成败**） | `clearUserCredentials` |
| ④ | 客户端清 localStorage → SLO 销毁 IdP 会话 | `stores/user.ts#logout` |

**全程 best-effort**：吊销失败只记 WARN，**绝不阻断登出**；并把 `/auth/logout` 加入 401 白名单，
避免 token 恰好过期时 401 分支抢先 `clearSession` + 硬跳登录页，与 SLO 形成竞态。

---

## 8. 遗留

| # | 项 | 状态 |
|---|---|---|
| 1 | `role=admin` 是否补中心 `api:admin` 授权 | 待拍板（T-ENG-7，现实影响为零） |
| 2 | `clearUserCredentials` 的中心 refresh_token 未真正吊销 | 已通过拉黑 access_token 覆盖；如需彻底可再调中心 revoke refresh |
| 3 | `MainLayout` 登出后仍跟着一句 `router.push('/login')` | SLO 会导航离开，该句是冗余；未改（不在本次范围） |
| 4 | `LoginView.vue` 既有类型告警 1 处（`brand.gradient`） | 存量，CI 不跑 `vue-tsc`，不阻塞 |
