# 安全 Checklist（Security Checklist）

> **文档版本**：v1.1
> **更新日期**：2026-06-28
> **适用范围**：MyKNG 知识库平台 7 模块（kb-gateway / kb-auth / kb-file / kb-knowledge / kb-ops / kb-intelligence / kb-common）+ 前端 kb-web + 双层 Nginx
> **对应 SOP**：附录 D — 安全 Checklist
> **使用说明**：每次发布前必须由安全负责人逐项确认；任一 Blocker 项未通过禁止发布。
> **检查频率**：每次发布前 + 每季度全面安全审计
> **责任角色**：安全负责人 / 开发负责人 / 运维负责人

---

## 目录

- [一、认证与授权](#一认证与授权)
- [二、输入校验与注入防护](#二输入校验与注入防护)
- [三、XSS 与 CSRF 防护](#三xss-与-csrf-防护)
- [四、敏感数据保护](#四敏感数据保护)
- [五、文件上传安全](#五文件上传安全)
- [六、接口安全](#六接口安全)
- [七、HTTPS 与传输安全](#七https-与传输安全)
- [八、CORS 配置](#八cors-配置)
- [九、依赖与漏洞管理](#九依赖与漏洞管理)
- [十、日志安全](#十日志安全)
- [十一、Nginx 与基础设施安全](#十一nginx-与基础设施安全)
- [十二、容器与部署安全](#十二容器与部署安全)
- [十三、安全审计与应急响应](#十三安全审计与应急响应)
- [附录 A：安全等级定义](#附录-a安全等级定义)
- [附录 B：OWASP Top 10 对应表](#附录-bowasp-top-10-对应表)

---

## 一、认证与授权

### 1.1 JWT Token 安全

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-JWT-01 | JWT 密钥强度 | 密钥长度 ≥ 256 位（32 字节），走环境变量 `JWT_SECRET` | 检查 `.env` 配置 | Blocker | ☐ ✅ ❌ |
| SEC-JWT-02 | JWT 密钥不在代码中 | 代码中无硬编码密钥 | `grep -r "JWT_SECRET\|jwtSecret" kb-*/src` | Blocker | ☐ ✅ ❌ |
| SEC-JWT-03 | Access Token 有效期 | ≤ 15 分钟（900000ms） | 检查 `kb-auth application.yml` | Blocker | ☐ ✅ ❌ |
| SEC-JWT-04 | Refresh Token 有效期 | ≤ 7 天（604800000ms） | 检查 `kb-auth application.yml` | Major | ☐ ✅ ❌ |
| SEC-JWT-05 | JWT 签名算法 | 使用 HMAC-SHA256 或 RS256，禁止 none | 检查 JwtUtils 实现 | Blocker | ☐ ✅ ❌ |
| SEC-JWT-06 | JWT 含 type 字段 | access token type=access，refresh token type=refresh | 解码 token 验证 | Major | ☐ ✅ ❌ |
| SEC-JWT-07 | Token 篡改检测 | 修改 token payload 后验签失败 | 测试用例 L1-AUTH-015 | Blocker | ☐ ✅ ❌ |
| SEC-JWT-08 | 登出加入黑名单 | logout 后 access token 立即失效 | 测试用例 L1-AUTH-007 | Blocker | ☐ ✅ ❌ |
| SEC-JWT-09 | Refresh Token 轮换 | 刷新后旧 refresh token 失效 | 测试用例 L1-AUTH-005 | Major | ☐ ✅ ❌ |
| SEC-JWT-10 | Refresh Token 唯一约束 | `refresh_token` 表 user_id 唯一索引，避免 TooManyResults | 检查数据库表结构 | Blocker | ☐ ✅ ❌ |
| SEC-JWT-11 | API Token 明文仅返回一次 | 创建时返回明文，后续仅返回 tokenPrefix | 测试用例 L1-AUTH-011 | Major | ☐ ✅ ❌ |
| SEC-JWT-12 | API Token 校验接口 | `/token/verify` 内部调用，非白名单 | 检查 SecurityConfig | Major | ☐ ✅ ❌ |

### 1.2 权限校验

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-AUTHZ-01 | 网关 JWT 过滤器拦截 | 所有 `/kb/api/**` 请求经过 `JwtAuthFilter`（白名单除外） | 检查 kb-gateway 配置 | Blocker | ☐ ✅ ❌ |
| SEC-AUTHZ-02 | 白名单最小化 | 仅 `/auth/login`、`/auth/refresh`、`/share/verify/**` 免认证 | 检查 `application.yml` | Blocker | ☐ ✅ ❌ |
| SEC-AUTHZ-03 | X-User-Id 防伪造 | 网关清除客户端伪造的 `X-User-Id`，注入真实 userId | 检查 JwtAuthFilter 实现 | Blocker | ☐ ✅ ❌ |
| SEC-AUTHZ-04 | 资源 owner 校验 | 操作资源前校验 `resource.userId == currentUserId` | 检查 Service 层 | Blocker | ☐ ✅ ❌ |
| SEC-AUTHZ-05 | 越权访问测试 | 用户 A 不能访问/修改用户 B 的资源 | 测试用例 L2-AUTHZ-001~005 | Blocker | ☐ ✅ ❌ |
| SEC-AUTHZ-06 | API Token 仅创建者可操作 | 删除/切换 token 校验 `token.userId == currentUserId` | 检查 ApiTokenService | Major | ☐ ✅ ❌ |
| SEC-AUTHZ-07 | 分享链接访问控制 | 公开分享可访问，私密分享需密码 | 测试用例 L1-KNOW-009/010 | Blocker | ☐ ✅ ❌ |
| SEC-AUTHZ-08 | 管理员权限隔离 | 普通用户不能访问运维接口 `/ops/**` | 检查 kb-ops Controller | Major | ☐ ✅ ❌ |

### 1.3 密码安全

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-PWD-01 | 密码加密存储 | BCrypt 加密，cost ≥ 10 | 检查 UserServiceImpl | Blocker | ☐ ✅ ❌ |
| SEC-PWD-02 | 密码不明文传输 | 全站 HTTPS | 见第七章 | Blocker | ☐ ✅ ❌ |
| SEC-PWD-03 | 密码不记录日志 | 日志中无明文密码 | `grep -r "password" logs/` | Blocker | ☐ ✅ ❌ |
| SEC-PWD-04 | 密码强度校验 | 至少 8 位，含大小写+数字+特殊字符 | 检查注册/修改密码接口 | Major | ☐ ✅ ❌ |
| SEC-PWD-05 | 密码错误次数限制 | 连续错误 5 次锁定账户 30min | 检查 AuthServiceImpl | Major | ☐ ✅ ❌ |
| SEC-PWD-06 | 修改密码校验旧密码 | 修改密码需校验 oldPassword | 测试用例 L1-AUTH-010 | Blocker | ☐ ✅ ❌ |
| SEC-PWD-07 | 默认密码强制修改 | admin 首次登录强制修改密码（建议） | 检查登录逻辑 | Minor | ☐ ✅ ❌ |

---

## 二、输入校验与注入防护

### 2.1 SQL 注入防护

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-SQLI-01 | 参数化查询 | 全部使用 MyBatis `#{}` 或 LambdaQueryWrapper，禁止 `${}` 拼接 | 代码扫描 + `grep -r '\${' kb-*/src/main/resources/mapper/` | Blocker | ☐ ✅ ❌ |
| SEC-SQLI-02 | 动态表名/字段名安全 | 如必须用 `${}`，白名单校验 | 代码审查 | Blocker | ☐ ✅ ❌ |
| SEC-SQLI-03 | ORDER BY 注入 | 排序字段白名单校验 | 检查分页查询实现 | Major | ☐ ✅ ❌ |
| SEC-SQLI-04 | LIKE 注入 | 用户输入转义 `%` 和 `_` | 检查搜索实现 | Major | ☐ ✅ ❌ |
| SEC-SQLI-05 | SQL 注入测试 | 用 sqlmap 扫描，0 高危 | `sqlmap -u "https://kb.marschat.online/kb/api/doc/list?keyword=test"` | Blocker | ☐ ✅ ❌ |

### 2.2 命令注入防护

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-CMD-01 | 禁止 Runtime.exec 用户输入 | 如必须执行命令，参数白名单校验 | 代码扫描 `grep -r "Runtime.exec\|ProcessBuilder" kb-*/src` | Blocker | ☐ ✅ ❌ |
| SEC-CMD-02 | 文件解析命令安全 | PDF/Word 解析不直接调用系统命令 | 检查 FileParseService | Blocker | ☐ ✅ ❌ |

### 2.3 参数校验

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-PARAM-01 | 必填字段校验 | `@NotNull` / `@NotBlank` | 检查 Controller DTO | Blocker | ☐ ✅ ❌ |
| SEC-PARAM-02 | 字段长度校验 | `@Size(max=N)` | 检查 DTO | Major | ☐ ✅ ❌ |
| SEC-PARAM-03 | 字段格式校验 | `@Pattern` / `@Email` | 检查 DTO | Major | ☐ ✅ ❌ |
| SEC-PARAM-04 | 数值范围校验 | `@Min` / `@Max` | 检查 DTO | Major | ☐ ✅ ❌ |
| SEC-PARAM-05 | 分页参数限制 | page ≤ 1000，size ≤ 100 | 检查分页 Controller | Major | ☐ ✅ ❌ |
| SEC-PARAM-06 | 文件 ID 防遍历 | id 校验为 Long，禁止字符串拼接 | 检查 Controller | Blocker | ☐ ✅ ❌ |
| SEC-PARAM-07 | 全局异常处理 | `@Valid` 校验失败返回 400 + 明确信息 | GlobalExceptionHandler | Major | ☐ ✅ ❌ |

---

## 三、XSS 与 CSRF 防护

### 3.1 XSS 防护

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-XSS-01 | 前端输出转义 | Vue3 默认转义 `{{ }}`，禁止 `v-html` 渲染用户输入 | `grep -r "v-html" kb-web/src/` | Blocker | ☐ ✅ ❌ |
| SEC-XSS-02 | 富文本内容过滤 | 文档内容用 DOMPurify 过滤后再 `v-html` | 检查 DocEditor 组件 | Blocker | ☐ ✅ ❌ |
| SEC-XSS-03 | 后端输入过滤 | 用户输入字段过滤 `<script>` / `onerror` 等 | 检查 Service 层 | Major | ☐ ✅ ❌ |
| SEC-XSS-04 | 响应头 CSP | `Content-Security-Policy: default-src 'self'` | 检查 Nginx 配置 | Major | ☐ ✅ ❌ |
| SEC-XSS-05 | Cookie httpOnly | JWT 在 cookie 中时设置 httpOnly + secure | 检查 Set-Cookie | Major | ☐ ✅ ❌ |
| SEC-XSS-06 | XSS 扫描测试 | 用 OWASP ZAP 扫描，0 高危 | ZAP 报告 | Blocker | ☐ ✅ ❌ |

### 3.2 CSRF 防护

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-CSRF-01 | API 使用 Bearer Token | JWT 在 Authorization 头，不在 cookie | 检查前端 axios 配置 | Blocker | ☐ ✅ ❌ |
| SEC-CSRF-02 | SameSite Cookie | 如用 cookie，设置 SameSite=Strict/Lax | 检查 Set-Cookie | Major | ☐ ✅ ❌ |
| SEC-CSRF-03 | CORS 限制 | 见第八章 | — | Blocker | ☐ ✅ ❌ |
| SEC-CSRF-04 | 关键操作二次确认 | 删除/转账类操作需二次确认或验证码 | 检查前端交互 | Minor | ☐ ✅ ❌ |

---

## 四、敏感数据保护

### 4.1 数据加密

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-ENC-01 | 密码 BCrypt 加密 | 数据库 `user.password` 字段为 BCrypt 哈希 | `SELECT password FROM user LIMIT 1` | Blocker | ☐ ✅ ❌ |
| SEC-ENC-02 | 敏感字段加密存储 | 手机号/邮箱/身份证 加密存储（可选） | 检查数据库 | Major | ☐ ✅ ❌ |
| SEC-ENC-03 | API Token 哈希存储 | 数据库存 token 哈希，不存明文 | 检查 `api_token` 表 | Blocker | ☐ ✅ ❌ |
| SEC-ENC-04 | 传输加密 | 全站 HTTPS | 见第七章 | Blocker | ☐ ✅ ❌ |
| SEC-ENC-05 | 备份加密 | 数据库备份文件加密 | 检查备份脚本 | Major | ☐ ✅ ❌ |

### 4.2 数据脱敏

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-MASK-01 | 手机号脱敏 | 响应中显示 `138****0000` | 检查 UserResponse | Major | ☐ ✅ ❌ |
| SEC-MASK-02 | 邮箱脱敏 | 响应中显示 `a***@example.com` | 检查 UserResponse | Major | ☐ ✅ ❌ |
| SEC-MASK-03 | API Token 脱敏 | 列表仅返回 tokenPrefix，不返回明文 | 测试用例 L2-AUTHZ-001 | Blocker | ☐ ✅ ❌ |
| SEC-MASK-04 | 异常信息脱敏 | 错误响应不含堆栈/SQL/表名 | GlobalExceptionHandler | Blocker | ☐ ✅ ❌ |
| SEC-MASK-05 | 日志脱敏 | 日志中密码/token/身份证 等脱敏 | 检查 logback 配置 + 代码 | Blocker | ☐ ✅ ❌ |

### 4.3 数据访问控制

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-DAC-01 | 用户数据隔离 | 用户 A 看不到用户 B 的私有文档 | 测试用例 L2-AUTHZ-002 | Blocker | ☐ ✅ ❌ |
| SEC-DAC-02 | 分享链接访问控制 | 公开分享可访问，私密分享需密码 | 测试用例 L1-KNOW-009/010 | Blocker | ☐ ✅ ❌ |
| SEC-DAC-03 | 回收站数据隔离 | 用户 A 不能恢复用户 B 删除的文档 | 检查 TrashService | Blocker | ☐ ✅ ❌ |
| SEC-DAC-04 | 运维数据权限 | 普通用户不能访问 `/ops/**` 接口 | 测试用例 L2-AUTHZ-005 | Blocker | ☐ ✅ ❌ |
| SEC-DAC-05 | 数据导出限制 | 批量导出需权限校验 + 审计日志 | 检查导出接口 | Major | ☐ ✅ ❌ |

---

## 五、文件上传安全

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-FILE-01 | 文件类型白名单 | 仅允许 pdf/doc/docx/xls/xlsx/ppt/pptx/txt/md/html/图片 | 检查 FileController | Blocker | ☐ ✅ ❌ |
| SEC-FILE-02 | 文件类型双重校验 | 扩展名 + Magic Number（文件头）校验 | 检查 FileService | Blocker | ☐ ✅ ❌ |
| SEC-FILE-03 | 文件大小限制 | 单文件 ≤ 100MB，分片 ≤ 5MB | 检查 `application.yml` | Blocker | ☐ ✅ ❌ |
| SEC-FILE-04 | 文件名安全 | 重命名为 UUID，不保留用户原始文件名 | 检查 FileService | Major | ☐ ✅ ❌ |
| SEC-FILE-05 | 存储路径隔离 | 文件存储在 MinIO，不在 Web 根目录 | 检查部署 | Blocker | ☐ ✅ ❌ |
| SEC-FILE-06 | 文件执行权限 | 上传目录禁止执行权限 | `chmod -x /data/minio/` | Blocker | ☐ ✅ ❌ |
| SEC-FILE-07 | 图片防马 | 图片用 ImageIO 重新编码 | 检查图片处理 | Major | ☐ ✅ ❌ |
| SEC-FILE-08 | 文件下载权限 | 下载前校验权限 | 检查 FileController | Blocker | ☐ ✅ ❌ |
| SEC-FILE-09 | 分片上传幂等 | fileId 服务端生成，防篡改 | 检查上传逻辑 | Major | ☐ ✅ ❌ |
| SEC-FILE-10 | ZIP 炸弹防护 | 解压前校验总大小 | 检查解压逻辑 | Major | ☐ ✅ ❌ |

---

## 六、接口安全

### 6.1 限流防刷

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-RATE-01 | 登录接口限流 | 单 IP 5 次/分钟 | 检查网关限流配置 | Blocker | ☐ ✅ ❌ |
| SEC-RATE-02 | 注册接口限流 | 单 IP 3 次/小时 | 检查限流配置 | Major | ☐ ✅ ❌ |
| SEC-RATE-03 | 文件上传限流 | 单用户 10 次/分钟 | 检查限流配置 | Major | ☐ ✅ ❌ |
| SEC-RATE-04 | 全局限流 | 单 IP 100 次/分钟 | 检查网关限流 | Major | ☐ ✅ ❌ |
| SEC-RATE-05 | 搜索接口限流 | 单用户 30 次/分钟 | 检查限流配置 | Major | ☐ ✅ ❌ |
| SEC-RATE-06 | 限流响应 | 返回 429 + Retry-After 头 | 测试 | Major | ☐ ✅ ❌ |

### 6.2 接口幂等

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-IDEM-01 | 创建接口幂等 | 重复提交返回同一结果（如基于 requestId） | 测试用例 L2-IDEM-001 | Major | ☐ ✅ ❌ |
| SEC-IDEM-02 | 支付类操作幂等 | 重复提交不重复扣款（如适用） | — | Blocker | ☐ ✅ ❌ |

### 6.3 错误信息安全

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-ERR-01 | 错误信息不泄露内部细节 | 不返回 SQL/堆栈/表名/文件路径 | 检查 GlobalExceptionHandler | Blocker | ☐ ✅ ❌ |
| SEC-ERR-02 | 404 不区分资源不存在和无权限 | 统一返回 404（避免枚举攻击） | 检查错误处理 | Major | ☐ ✅ ❌ |
| SEC-ERR-03 | 登录失败统一信息 | "用户名或密码错误"，不区分用户不存在和密码错误 | 检查 AuthService | Blocker | ☐ ✅ ❌ |
| SEC-ERR-04 | 异常含 traceId | 便于排查，但不泄露细节 | 检查 Result 返回 | Major | ☐ ✅ ❌ |

---

## 七、HTTPS 与传输安全

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-HTTPS-01 | 全站 HTTPS | 所有公网入口走 HTTPS | `curl -I http://kb.marschat.online` 应 301 跳转 https | Blocker | ☐ ✅ ❌ |
| SEC-HTTPS-02 | HTTP 强制跳转 HTTPS | 80 端口 301 跳转到 443 | 检查 Nginx 配置 | Blocker | ☐ ✅ ❌ |
| SEC-HTTPS-03 | TLS 版本 | 仅 TLS 1.2 + 1.3，禁用 1.0/1.1 | `openssl s_client -connect kb.marschat.online:443 -tls1_1` 应失败 | Blocker | ☐ ✅ ❌ |
| SEC-HTTPS-04 | 加密套件 | 强加密套件，禁用弱算法 | `nmap --script ssl-enum-ciphers -p 443 kb.marschat.online` | Major | ☐ ✅ ❌ |
| SEC-HTTPS-05 | HSTS 头 | `Strict-Transport-Security: max-age=31536000; includeSubDomains` | `curl -I https://kb.marschat.online` | Major | ☐ ✅ ❌ |
| SEC-HTTPS-06 | 证书有效期 | 剩余有效期 > 30 天 | `openssl s_client -connect kb.marschat.online:443` | Major | ☐ ✅ ❌ |
| SEC-HTTPS-07 | 证书自动续期 | acme.sh 自动续期配置 | 检查 crontab | Minor | ☐ ✅ ❌ |
| SEC-HTTPS-08 | 内部服务通信 | 服务间通信可走 HTTP，但限于内网/Tailscale | 检查部署架构 | Major | ☐ ✅ ❌ |

---

## 八、CORS 配置

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-CORS-01 | Origin 白名单 | `Access-Control-Allow-Origin` 仅允许已知域名，禁止 `*` | `curl -I -H "Origin: https://evil.com" https://kb.marschat.online/kb/api/user/profile` | Blocker | ☐ ✅ ❌ |
| SEC-CORS-02 | Credentials 配合 | 若 `Allow-Credentials: true`，则 Origin 不能为 `*` | 检查网关 CORS 配置 | Blocker | ☐ ✅ ❌ |
| SEC-CORS-03 | Methods 限制 | 仅允许实际使用的方法（GET/POST/PUT/DELETE/OPTIONS） | 检查 CORS 配置 | Major | ☐ ✅ ❌ |
| SEC-CORS-04 | Headers 限制 | 仅允许实际使用的头（Authorization/Content-Type） | 检查 CORS 配置 | Major | ☐ ✅ ❌ |
| SEC-CORS-05 | Max-Age 合理 | `Access-Control-Max-Age: 3600` 减少预检请求 | 检查 CORS 配置 | Minor | ☐ ✅ ❌ |
| SEC-CORS-06 | OPTIONS 预检放行 | OPTIONS 请求不触发业务逻辑 | 检查网关过滤器 | Major | ☐ ✅ ❌ |
| SEC-CORS-07 | 非法 Origin 拒绝 | 非白名单 Origin 返回 403 | 测试用例 L5-CORS-001 | Blocker | ☐ ✅ ❌ |

---

## 九、依赖与漏洞管理

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-DEP-01 | 依赖版本固定 | pom.xml 中版本号固定，禁止 SNAPSHOT | `grep -r "SNAPSHOT" kb-*/pom.xml` | Major | ☐ ✅ ❌ |
| SEC-DEP-02 | CVE 漏洞扫描 | 0 高危，0 中危 | `mvn org.owasp:dependency-check-maven:check` | Blocker | ☐ ✅ ❌ |
| SEC-DEP-03 | npm 漏洞扫描 | 0 高危，0 中危 | `pnpm audit` | Blocker | ☐ ✅ ❌ |
| SEC-DEP-04 | 依赖来源 | Maven 走 Nexus 私服，npm 走 Nexus 私服 | 检查 settings.xml / .npmrc | Blocker | ☐ ✅ ❌ |
| SEC-DEP-05 | 镜像来源 | Docker 基础镜像走私服 | 检查 Dockerfile FROM | Blocker | ☐ ✅ ❌ |
| SEC-DEP-06 | 镜像漏洞扫描 | 0 高危 | `trivy image nexus.marschat.online/.../kb-xxx:v1.x.x` | Major | ☐ ✅ ❌ |
| SEC-DEP-07 | 依赖更新策略 | 每月检查依赖更新，评估升级 | 依赖更新报告 | Minor | ☐ ✅ ❌ |
| SEC-DEP-08 | Spring Boot 版本 | 当前 LTS 版本，无已知高危 CVE | 检查 `spring-boot.version` | Major | ☐ ✅ ❌ |

---

## 十、日志安全

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-LOG-01 | 密码不记录 | 日志中无明文密码 | `grep -r "password=" logs/` | Blocker | ☐ ✅ ❌ |
| SEC-LOG-02 | Token 不记录 | 日志中无完整 token（可记录前 8 位） | `grep -r "Bearer eyJ" logs/` | Blocker | ☐ ✅ ❌ |
| SEC-LOG-03 | 敏感信息脱敏 | 手机号/邮箱/身份证 脱敏 | 检查日志输出 | Blocker | ☐ ✅ ❌ |
| SEC-LOG-04 | 异常堆栈不泄露 | 异常日志记录完整堆栈（仅内部），不返回前端 | 检查 GlobalExceptionHandler | Major | ☐ ✅ ❌ |
| SEC-LOG-05 | 操作审计日志 | 关键操作（登录/删除/权限变更）记录审计日志 | 检查 ops_operation_log 表 | Major | ☐ ✅ ❌ |
| SEC-LOG-06 | 日志保留期 | 业务日志 ≥ 30 天，审计日志 ≥ 180 天 | 检查 logback 滚动策略 | Major | ☐ ✅ ❌ |
| SEC-LOG-07 | 日志访问权限 | 日志文件仅运维可读 | `ls -la logs/` 权限检查 | Major | ☐ ✅ ❌ |
| SEC-LOG-08 | 日志注入防护 | 日志内容过滤换行符，防日志注入 | 检查日志输出 | Minor | ☐ ✅ ❌ |

---

## 十一、Nginx 与基础设施安全

### 11.1 Nginx 安全

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-NGINX-01 | 隐藏版本号 | `server_tokens off` | `curl -I` 不显示 nginx 版本 | Major | ☐ ✅ ❌ |
| SEC-NGINX-02 | 安全响应头 | X-Frame-Options/X-Content-Type-Options/X-XSS-Protection 配置 | `curl -I` 检查响应头 | Major | ☐ ✅ ❌ |
| SEC-NGINX-03 | 静态资源禁止执行 PHP/CGI | 无 PHP/CGI 配置 | 检查 Nginx 配置 | Blocker | ☐ ✅ ❌ |
| SEC-NGINX-04 | 目录遍历防护 | `autoindex off` | `curl https://kb.marschat.online/kb/s/assets/` | Blocker | ☐ ✅ ❌ |
| SEC-NGINX-05 | 上传目录禁止执行 | `location /uploads/ { deny php; }` | 检查配置 | Blocker | ☐ ✅ ❌ |
| SEC-NGINX-06 | 请求体大小限制 | `client_max_body_size 100m` | 检查配置 | Major | ☐ ✅ ❌ |
| SEC-NGINX-07 | 连接数限制 | `limit_conn` 防单 IP 大量连接 | 检查配置 | Major | ☐ ✅ ❌ |
| SEC-NGINX-08 | 慢请求防护 | `client_body_timeout` / `client_header_timeout` | 检查配置 | Minor | ☐ ✅ ❌ |
| SEC-NGINX-09 | 双层 Nginx 链路安全 | 腾讯云2号仅反代，mykng 本地 Nginx 处理静态资源 | 检查架构 | Major | ☐ ✅ ❌ |
| SEC-NGINX-10 | Nginx 配置备份 | 修改前备份，`nginx -t` 验证 | 检查运维流程 | Major | ☐ ✅ ❌ |

### 11.2 服务器安全

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-SRV-01 | SSH 端口非默认 | 非 22 端口（FRP 3383/3385） | `ss -tlnp \| grep ssh` | Major | ☐ ✅ ❌ |
| SEC-SRV-02 | SSH 密码强度 | 强密码或密钥登录 | 检查 sshd_config | Major | ☐ ✅ ❌ |
| SEC-SRV-03 | 防火墙开启 | ufw/iptables 仅开放必要端口 | `ufw status` | Blocker | ☐ ✅ ❌ |
| SEC-SRV-04 | 端口最小暴露 | 仅 80/443 对外，其余走 Tailscale | `nmap -p- 服务器IP` | Blocker | ☐ ✅ ❌ |
| SEC-SRV-05 | 系统更新 | 定期 apt update / 安全补丁 | `apt list --upgradable` | Major | ☐ ✅ ❌ |
| SEC-SRV-06 | fail2ban 防暴力破解 | 登录失败封禁 | `systemctl status fail2ban` | Major | ☐ ✅ ❌ |
| SEC-SRV-07 | Docker socket 权限 | `/var/run/docker.sock` 仅 root/docker 组可读写 | `ls -la /var/run/docker.sock` | Blocker | ☐ ✅ ❌ |
| SEC-SRV-08 | 文件权限合理 | 配置文件 600，日志 644，脚本 755 | `ls -la` 检查 | Major | ☐ ✅ ❌ |

---

## 十二、容器与部署安全

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-DOCKER-01 | 容器以非 root 运行 | Dockerfile 中 `USER` 指定非 root 用户 | 检查 Dockerfile | Major | ☐ ✅ ❌ |
| SEC-DOCKER-02 | 容器只读文件系统 | `read_only: true`（如适用） | 检查 docker-compose.yml | Minor | ☐ ✅ ❌ |
| SEC-DOCKER-03 | 资源限制 | CPU/内存限制 | 检查 docker-compose.yml | Major | ☐ ✅ ❌ |
| SEC-DOCKER-04 | 不挂载 docker.sock | 容器内不挂载宿主 docker.sock | `grep docker.sock docker-compose.yml` | Blocker | ☐ ✅ ❌ |
| SEC-DOCKER-05 | 镜像扫描 | trivy/grype 扫描 0 高危 | `trivy image xxx` | Major | ☐ ✅ ❌ |
| SEC-DOCKER-06 | 镜像走私服 | 拉取/推送走 `nexus.marschat.online` | 检查 docker login + image 名 | Blocker | ☐ ✅ ❌ |
| SEC-DOCKER-07 | 敏感配置走环境变量 | 密码/密钥不在 Dockerfile | 检查 Dockerfile + .env | Blocker | ☐ ✅ ❌ |
| SEC-DOCKER-08 | 网络隔离 | 业务容器与数据库容器不同网络 | 检查 docker network | Major | ☐ ✅ ❌ |
| SEC-DOCKER-09 | 容器端口不直接暴露 | 业务端口仅 8090 暴露，其余 127.0.0.1 | `docker ps` 检查端口映射 | Blocker | ☐ ✅ ❌ |
| SEC-DOCKER-10 | 健康检查配置 | healthcheck 配置完整 | 检查 docker-compose.yml | Major | ☐ ✅ ❌ |

---

## 十三、安全审计与应急响应

### 13.1 安全审计

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-AUDIT-01 | 操作审计日志 | 登录/登出/创建/删除/权限变更记录到 `ops_operation_log` | 查表数据 | Major | ☐ ✅ ❌ |
| SEC-AUDIT-02 | 登录日志 | 登录成功/失败记录 IP/UA/时间 | 检查登录日志 | Major | ☐ ✅ ❌ |
| SEC-AUDIT-03 | 敏感操作日志 | 删除文档/清空回收站/修改权限 记录详细日志 | 检查日志 | Major | ☐ ✅ ❌ |
| SEC-AUDIT-04 | 日志不可篡改 | 日志文件 append-only（可选：WORM 存储） | 检查文件权限 | Minor | ☐ ✅ ❌ |
| SEC-AUDIT-05 | 定期审计 | 每季度全面安全审计 | 审计报告 | Major | ☐ ✅ ❌ |
| SEC-AUDIT-06 | 渗透测试 | 每年至少 1 次渗透测试 | 渗透测试报告 | Major | ☐ ✅ ❌ |

### 13.2 应急响应

| 编号 | 检查项 | 通过标准 | 验证方式 | 等级 | 状态 |
|------|--------|---------|---------|------|------|
| SEC-IR-01 | 应急响应预案 | 文档化，含分级响应流程 | 检查预案文档 | Blocker | ☐ ✅ ❌ |
| SEC-IR-02 | 应急联系人 | 7×24 联系方式，含安全负责人 | 检查联系人列表 | Blocker | ☐ ✅ ❌ |
| SEC-IR-03 | 安全事件分级 | P0（数据泄露）/P1（服务中断）/P2（漏洞）分级 | 检查预案 | Major | ☐ ✅ ❌ |
| SEC-IR-04 | 响应时效 | P0 30min 内响应，P1 1h 内响应 | 检查 SLA | Major | ☐ ✅ ❌ |
| SEC-IR-05 | 备份恢复演练 | 每季度演练，恢复耗时 < 1h | 演练报告 | Major | ☐ ✅ ❌ |
| SEC-IR-06 | 事件复盘 | 安全事件 48h 内复盘，输出根因 + 改进项 | 复盘报告 | Major | ☐ ✅ ❌ |

---

## 附录 A：安全等级定义

| 等级 | 含义 | 处理方式 |
|------|------|---------|
| **Blocker** | 高危安全漏洞，可能被利用造成数据泄露/服务中断 | 必须修复，禁止发布 |
| **Major** | 中危安全风险，可能被利用但影响有限 | 强烈建议修复，评估后决定 |
| **Minor** | 低危安全建议，加固措施 | 可后续优化 |

---

## 附录 B：OWASP Top 10 对应表

| OWASP Top 10 (2021) | 对应检查章节 |
|---------------------|-------------|
| A01 - 失效的访问控制 | 第一章 1.2 权限校验 + 第四章 4.3 数据访问控制 |
| A02 - 加密机制失效 | 第一章 1.3 密码安全 + 第四章 4.1 数据加密 + 第七章 HTTPS |
| A03 - 注入 | 第二章 2.1 SQL 注入 + 2.2 命令注入 + 2.3 参数校验 |
| A04 - 不安全设计 | 第一章 1.1 JWT + 第六章接口安全 |
| A05 - 安全配置错误 | 第十一章 Nginx + 第十二章容器 + 第八章 CORS |
| A06 - 脆弱和过时的组件 | 第九章 依赖与漏洞管理 |
| A07 - 身份识别和认证失败 | 第一章 1.1 JWT + 1.3 密码安全 + 第六章 6.1 限流 |
| A08 - 软件和数据完整性故障 | 第九章依赖 + 第十二章容器 |
| A09 - 安全日志和监控失败 | 第十章日志 + 第十三章审计 |
| A10 - 服务端请求伪造（SSRF） | 第二章 2.2 命令注入 + 第五章文件上传 |

---

## 附录 C：安全扫描工具速查

| 工具 | 用途 | 命令示例 | 安装方式 |
|------|------|---------|---------|
| OWASP Dependency-Check | Maven 依赖漏洞扫描 | `mvn org.owasp:dependency-check-maven:check` | Maven 插件 |
| pnpm audit | npm 依赖漏洞扫描 | `pnpm audit` | pnpm 内置 |
| SonarQube | 代码静态安全扫描 | SonarQube Scanner | 独立部署 |
| Trivy | 容器镜像漏洞扫描 | `trivy image xxx` | `apt install trivy` |
| OWASP ZAP | Web 应用动态扫描 | `zap-cli quick-scan https://kb.marschat.online` | 下载安装 |
| sqlmap | SQL 注入扫描 | `sqlmap -u "url"` | `pip install sqlmap` |
| nmap | 端口与服务扫描 | `nmap -p- target` | `apt install nmap` |
| sslscan | SSL 配置扫描 | `sslscan kb.marschat.online` | `apt install sslscan` |

---

## 附录 D：变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|---------|------|
| v1.0 | 2026-06-28 | 初版创建，覆盖 13 大安全维度 | 测试组 |
| v1.1 | 2026-06-28 | 对齐 SOP V1.1 附录 D，补充 OWASP Top 10 对应表、安全扫描工具速查、KB_CONTEXT 五处一致 | 测试组 |
