# ADR-2026-10-22-L · infra 账密会话误踢根因修复：watchSession 双模判据（R14-L/M · T-ENG-20 闭环）

- **状态**：✅ 已实施并复验闭环（2026-10-22）
- **归属**：devtools 仓库（infra-monitor 前端）+ marschat-components 文档
- **作者**：WorkBuddy AI（良哥授权全权执行）
- **一手材料**：本会话 R14-L/M 全程记录；`marschat-components/docs/状态与待办.md` T-ENG-20 / T-LOW-22；`docs/排查手册.md` §16；证据 `mykng:/root/verify/r14/r14_l.py` / `r14_m.py`

---

## 1. 背景与问题

`T-ENG-20`（R13 登记）：infra-monitor **账密登录**后，全页面加载/重载会被踢回 `https://monitor.marschat.online/infra/login?slo=1`（SSO 路径不会）。R14-J7 曾记录「重载后 bootstrap 权限探针 `hasAuth=false` ⇒ legacy 令牌刷新后取不到」，根因未定位，待办留给「下轮」。

## 2. 根因（R14-L：静态代码 + 动态取证双重坐实；J7「取不到」被证伪）

**判据链（代码级）**：

1. infra `marschat.ts` **未传 `watchSession`**、`APP_OPTIONS` **未设 `sessionMode`** ⇒ app-kit `config.ts` 缺省 `sessionMode='oidc'` ⇒ `createMarschatApp.ts` 判据链③ `useWatcher = (undefined ?? true) && 'oidc'==='oidc'` = **恒真**；
2. 账密登录后令牌照落 localStorage（实测 `infra_access_token` 189 字符 HS384 JWT + `infra_token_kind=legacy`）；
3. **全页面重载** ⇒ bootstrap 见 `getToken()` 有值 ⇒ `sso.watchSession()` 启动；
4. watcher **3s 首探** `auth-center /auth/session`；legacy 登录**无 IdP 会话** ⇒ `authenticated:false`；
5. `confirmCount` 缺省 1 ⇒ 一次即判失 ⇒ `clearAuth()` 清令牌 + `assign('/infra/login?slo=1')`。

**动态实证（headless + CDP Network 层）**：账密登录 → 重载 → bootstrap 权限探针 **`hasAuth=true 200`**（令牌读得到！J7 记录的是**后果**：watcher 先清了令牌，login 页再探才无票 401）→ **3.3s 后** URL 变 `/infra/login?slo=1` 且 localStorage 全清（与「3s 首探 + clearAuth + assign」时序吻合）→ CDP 层证实 `/auth/session` 探针确实发出（200）。SSO 对照：探针 `authenticated=true username=1` 不踢。

三个此前的疑点一次说清：**为何仅重载触发**（SPA 内登录不重启 watcher）、**为何 SSO 不触发**（真 IdP 会话）、**为何 login 页探针无票**（令牌已被 watcher 清掉）。

**与 T-LOW-22 的关系**：portal / kb-web 显式传了 `sessionMode:'bff' + watchSession:false`（有意关掉对 legacy 的监视）；**infra 两个都没传 ⇒ 落缺省判据按纯 OIDC 应用处理 ⇒ 漏配的双模应用**。教训：**双模应用不传 watchSession ≠ 关掉监视**。

## 3. 决策与范围

| 项 | 决策 | 理由 |
|---|---|---|
| 修法 | infra `marschat.ts` 传 `watchSession: () => isOidcToken()` | app-kit `types.ts` 明确推荐的**双模函数式判据**；`isOidcToken()` 读 `infra_token_kind==='oidc'`，legacy 会话天然排除，SSO 监视语义原样保留 |
| 不改组件库 | app-kit 判据链不动 | 缺省语义（未传 = 纯 OIDC 应用）本身自洽；改缺省会动全部应用的行为面，风险与收益不成比例 |
| 验证深度 | 修前取证（L）+ 修后复验（M）两轮，断言全部反转 | 修复类验证必须「症状消失 + 机制证据（watcher 零探针）+ 对照不回归（SSO 照常探针）」三判据齐备 |

## 4. 执行结果

1. **代码**：devtools `4dc21f63`（单文件 16 行：import + 判据 + 注释）。本地 `vue-tsc` 存量 5 错**零新增**（stash 对照基线一致）、`vite build` EXIT=0、`pnpm install --frozen-lockfile` EXIT=0（顺带修复本机 node_modules 陈旧在 0.1.2 的问题——锁文件早已钉 0.1.8）。
2. **双推**：`git push origin dev` 一次推两端（Gitee + GitHub 双 pushurl），`git ls-remote` 逐端核验 tip 一致（`4dc21f63`）。
3. **流水线**：Woodpecker **#865** success（`git reset --hard 4dc21f63` 构建部署）；在线 chunk `assets-v2/index-CQu1Y_DI.js` 双签名核验（`watchSession:()=>Z0()` = 修复后的 `watchSession: () => isOidcToken()`，与本地构建模式一致；旧版只含 app-kit 内部 1 处）。
4. **复验（R14-M，14/14 全绿）**：

| 判据 | 账密（修复核心） | SSO（对照不回归） |
|---|---|---|
| 登录后落业务页 | ✅ | ✅ |
| 重载后权限探针带票 200 | ✅ | ✅ |
| **重载后不被踢**（URL 停 `/infra/dashboard`） | ✅（原必踢 `?slo=1`） | ✅ |
| 本地令牌键仍在 | ✅（原全清） | ✅ 三键俱在 |
| **watcher 探针**（CDP 层） | **零 `/auth/session`**（未启动） | 照常发出（oidc 语义保留） |
| 业务页特征词渲染 | ✅ 总览看板/主机数/凭据数 | ✅ |

## 5. 副产品：坑 #46 补丁（页面内包装的导航竞态丢失）

R14-L 取证时页面内 fetch 包装抓到 SSO 的 `/auth/session` 探针、却漏抓账密的——CDP 层证明后者确实发出。根因：watcher 判失后 `location.assign` **先于**包装的 `.then` push 发生 ⇒ 页面销毁、记录丢失。**固化判据**：console 类断言靠页面内包装（CDP 不报 console API）；网络类断言靠 CDP Network 层（页面内包装怕导航竞态）——**两层互不替代**。已写入 `排查手册.md` §16.2。

## 6. 遗留与后续

- **双模应用普查**：`grep -rn "watchSession" */src/marschat.ts` —— 没有的双模应用都是同类雷（本轮已修 infra；portal/kb-web 本就显式关停）。建议随下次发版顺手普查。
- `T-ENG-17`（kb-ops 权限链根因，涉后端安全语义）与 `T-ENG-19`（GR 单主模式根子）仍待良哥拍板，不在本轮范围。
