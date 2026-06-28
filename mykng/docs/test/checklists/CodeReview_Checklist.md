# Code Review Checklist（代码评审清单）

> **文档版本**：v1.1
> **更新日期**：2026-06-28
> **适用范围**：MyKNG 知识库平台 7 模块（kb-gateway / kb-auth / kb-file / kb-knowledge / kb-ops / kb-intelligence / kb-common）
> **对应 SOP**：附录 C — Code Review Checklist
> **使用说明**：每次 MR/PR 提交后，评审人按本清单逐项检查。Blocker 项必须修复方可合并；Major 项建议修复；Minor 项可后续优化。
> **强制要求**：所有 MR 至少 1 名评审人 approve，核心模块（kb-auth/kb-gateway/kb-common）需 2 名 approve。

---

## 目录

- [一、评审流程规范](#一评审流程规范)
- [二、通用检查项](#二通用检查项)
- [三、后端 Java 专项检查](#三后端-java-专项检查)
- [四、前端 Vue3/TS 专项检查](#四前端-vue3ts-专项检查)
- [五、数据库专项检查](#五数据库专项检查)
- [六、API 接口专项检查](#六api-接口专项检查)
- [七、测试代码专项检查](#七测试代码专项检查)
- [八、配置与部署专项检查](#八配置与部署专项检查)
- [九、性能与安全专项检查](#九性能与安全专项检查)
- [十、CR 评审结论模板](#十cr-评审结论模板)
- [附录 A：评审等级定义](#附录-a评审等级定义)
- [附录 B：常见反模式速查](#附录-b常见反模式速查)

---

## 一、评审流程规范

### 1.1 MR 提交前自检（开发者）

| 编号 | 自检项 | 状态 |
|------|--------|------|
| CR-SELF-01 | 本地编译通过：`mvn clean compile -DskipTests` | ☐ |
| CR-SELF-02 | 本地单测通过：`mvn test` | ☐ |
| CR-SELF-03 | 代码格式化：`mvn spotless:apply` 或 IDE 格式化 | ☐ |
| CR-SELF-04 | 静态扫描通过：SonarLint IDE 插件无 Blocker | ☐ |
| CR-SELF-05 | 已移除调试代码（System.out/print/console.log/TODO） | ☐ |
| CR-SELF-06 | 已更新文档（接口规范/部署文档/CHANGELOG） | ☐ |
| CR-SELF-07 | Commit message 符合规范（见项目 git-commit-message.md） | ☐ |
| CR-SELF-08 | MR 描述完整（变更内容/影响范围/自测结论） | ☐ |

### 1.2 MR 评审流程

```
开发者提交 MR
   │
   ▼
[自动检查] CI 流水线
   │─ 编译 ✅
   │─ 单测 ✅
   │─ 覆盖率 ✅
   │─ SonarQube 扫描 ✅
   │
   ▼
[人工评审] 至少 1 名评审人（核心模块 2 名）
   │
   ▼
[评审结论] Approve / Request Changes / Reject
   │
   ▼
[合并] Squash Merge 到主分支
```

### 1.3 评审时效要求

| MR 类型 | 评审时效 | 升级机制 |
|---------|---------|---------|
| 紧急修复（Hotfix） | 1h 内 | 超时直接找架构师 |
| 常规功能 | 4h 内（工作日） | 超时 @评审人 |
| 重构/架构调整 | 1 个工作日 | 需架构师参与 |
| 文档变更 | 1 个工作日 | 仅需 1 人 approve |

---

## 二、通用检查项

### 2.1 命名规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-NAME-01 | 类名使用 UpperCamelCase | Blocker | 如 `UserService`，禁止 `userService` / `user_service` |
| CR-NAME-02 | 方法名/变量名使用 lowerCamelCase | Blocker | 如 `getUserById`，禁止 `GetUserById` / `get_user_by_id` |
| CR-NAME-03 | 常量使用 UPPER_SNAKE_CASE | Major | 如 `MAX_RETRY_COUNT`，禁止 `MaxRetryCount` |
| CR-NAME-04 | 包名全小写，无下划线 | Major | 如 `com.mykng.auth.service`，禁止 `com.mykng.Auth.Service` |
| CR-NAME-05 | 布尔变量以 is/has/can/should 开头 | Minor | 如 `isEnabled` / `hasPermission`，禁止 `enabled` / `permission` |
| CR-NAME-06 | 接口名不加 `I` 前缀 | Major | 如 `UserService`（接口）/ `UserServiceImpl`（实现），禁止 `IUserService` |
| CR-NAME-07 | 异常类以 `Exception` 结尾 | Major | 如 `BusinessException`，禁止 `BusinessError` |
| CR-NAME-08 | 枚举类使用单数形式 | Minor | 如 `DocStatus`，禁止 `DocStatuses` |
| CR-NAME-09 | 方法名能表达意图 | Major | 如 `findActiveUsersByRole`，禁止 `getData` / `process` |
| CR-NAME-10 | 避免缩写（除领域通用缩写） | Minor | 如 `getUser` 而非 `getUsr`，`password` 而非 `pwd` |

### 2.2 代码结构

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-STRUCT-01 | 单一职责原则 | Major | 一个类/方法只做一件事，Controller 不含业务逻辑 |
| CR-STRUCT-02 | 方法长度 ≤ 80 行 | Major | 超过需拆分 |
| CR-STRUCT-03 | 类长度 ≤ 500 行 | Major | 超过需拆分 |
| CR-STRUCT-04 | 参数个数 ≤ 5 个 | Minor | 超过需封装为对象 |
| CR-STRUCT-05 | 嵌套层级 ≤ 3 层 | Major | 超过需用卫语句/提取方法 |
| CR-STRUCT-06 | 无重复代码（DRY） | Major | 重复 ≥ 3 处需提取公共方法 |
| CR-STRUCT-07 | 无魔法数字/字符串 | Major | 用常量或枚举替代，如 `if (status == 1)` → `if (status == DocStatus.PUBLISHED.getCode())` |
| CR-STRUCT-08 | 注释适度 | Minor | 复杂逻辑必须有注释，简单代码无需注释，禁止"废话注释" |
| CR-STRUCT-09 | 无未使用代码 | Minor | 无未使用的 import / 变量 / 方法 / 类 |
| CR-STRUCT-10 | 无被注释掉的代码 | Minor | 用 git 历史管理，不要留注释代码 |

### 2.3 异常处理

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-EXC-01 | 不吞异常 | Blocker | 禁止 `catch (Exception e) {}`，至少记录日志或重新抛出 |
| CR-EXC-02 | 不捕获 Throwable | Blocker | Throwable 会捕获 Error（如 OOM），不应在业务代码捕获 |
| CR-EXC-03 | 异常分类处理 | Major | 先捕获具体异常，后捕获通用异常，禁止直接 `catch (Exception e)` |
| CR-EXC-04 | 异常信息含上下文 | Major | 如 `throw new BusinessException("用户不存在: " + userId)`，而非仅 `throw new BusinessException("用户不存在")` |
| CR-EXC-05 | 业务异常使用 BusinessException | Major | 业务规则违反使用 BusinessException，系统异常使用 RuntimeException |
| CR-EXC-06 | 异常不用于流程控制 | Major | 禁止用 try-catch 替代 if-else |
| CR-EXC-07 | finally 块无 return | Blocker | finally 中的 return 会覆盖 try 中的 return |
| CR-EXC-08 | 资源在 finally/try-with-resources 关闭 | Blocker | 如 InputStream/Connection 必须关闭 |
| CR-EXC-09 | 自定义异常有错误码 | Major | 如 `BusinessException(401, "token无效")` |
| CR-EXC-10 | 全局异常处理器覆盖 | Major | GlobalExceptionHandler 处理所有异常，不向前端暴露堆栈 |

### 2.4 日志规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-LOG-01 | 使用 SLF4J（@Slf4j） | Major | 禁止 `System.out.println` / `e.printStackTrace()` |
| CR-LOG-02 | 日志级别合理 | Major | ERROR：系统异常；WARN：业务异常；INFO：关键流程；DEBUG：调试信息 |
| CR-LOG-03 | 日志含上下文 | Major | 如 `log.error("用户登录失败, userId={}", userId, e)`，而非 `log.error("登录失败")` |
| CR-LOG-04 | 使用占位符而非字符串拼接 | Major | `log.info("user={}", user)` 而非 `log.info("user=" + user)` |
| CR-LOG-05 | 敏感信息不记录 | Blocker | 密码/token/身份证 等敏感字段不记录到日志 |
| CR-LOG-06 | 异常日志记录完整堆栈 | Major | `log.error("xxx", e)` 而非 `log.error("xxx: " + e.getMessage())` |
| CR-LOG-07 | 循环内不打印日志 | Minor | 避免日志量爆炸，必要时用 DEBUG 级别 |
| CR-LOG-08 | 关键操作有日志 | Major | 创建/修改/删除操作记录 INFO 日志，含操作人/对象/结果 |

### 2.5 事务与并发

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-TX-01 | @Transactional 标注在 Service 层 | Major | 禁止在 Controller 标注 |
| CR-TX-02 | @Transactional 注意外部调用 | Major | 事务内禁止调用外部接口（HTTP/RPC），避免长事务 |
| CR-TX-03 | @Transactional rollbackFor 配置 | Major | 默认只回滚 RuntimeException，业务异常需 `rollbackFor = Exception.class` |
| CR-TX-04 | @Transactional 不影响幂等 | Major | 幂等校验在事务外，避免重复提交 |
| CR-TX-05 | 并发修改用乐观锁/悲观锁 | Major | 如 `@Version` 或 `SELECT ... FOR UPDATE` |
| CR-TX-06 | 共享可变状态同步 | Blocker | 如有共享变量，必须用 synchronized/Lock/Atomic 类 |
| CR-TX-07 | 避免死锁 | Major | 多个锁按固定顺序获取 |
| CR-TX-08 | ThreadLocal 使用后清理 | Major | `try-finally` 中 `threadLocal.remove()`，避免线程池复用导致内存泄漏 |

---

## 三、后端 Java 专项检查

### 3.1 Spring Boot 规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-SPRING-01 | Controller 只做参数校验和转发 | Major | 业务逻辑在 Service 层 |
| CR-SPRING-02 | Service 接口与实现分离 | Major | 接口在 `service` 包，实现在 `service.impl` 包 |
| CR-SPRING-03 | 依赖注入使用构造器注入 | Minor | `@RequiredArgsConstructor` + `final` 字段，避免 `@Autowired` 字段注入 |
| CR-SPRING-04 | 配置类使用 @Configuration | Major | 禁止在 @Component 中用 @Bean |
| CR-SPRING-05 | Bean 作用域合理 | Minor | 默认 singleton，prototype 慎用 |
| CR-SPRING-06 | @Value 替代为 @ConfigurationProperties | Minor | 多配置项用 `@ConfigurationProperties`，单个值用 `@Value` |
| CR-SPRING-07 | 避免循环依赖 | Blocker | 如 A 依赖 B，B 依赖 A，需重构 |
| CR-SPRING-08 | 过滤器/拦截器注册合理 | Major | 在 WebConfig 中注册，注意 order |

### 3.2 MyBatis-Plus 规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-MP-01 | Mapper 继承 BaseMapper | Major | 复用通用 CRUD |
| CR-MP-02 | Service 继承 ServiceImpl | Major | 复用通用方法 |
| CR-MP-03 | 复杂查询用 XML 或 LambdaQueryWrapper | Major | 禁止拼接 SQL 字符串 |
| CR-MP-04 | 分页使用 Page 对象 | Major | 禁止手写 limit |
| CR-MP-05 | 逻辑删除字段配置 @TableLogic | Major | deleted=1 逻辑删除，不物理删除 |
| CR-MP-06 | 自动填充字段配置 @TableField(fill=...) | Major | create_time/update_time 自动填充 |
| CR-MP-07 | 主键策略合理 | Minor | 自增 `@TableId(type=IdType.AUTO)` 或雪花 `ASSIGN_ID` |

### 3.3 工具类规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-UTIL-01 | 工具类构造器私有化 | Minor | `private XxxUtils() {}` 防止实例化 |
| CR-UTIL-02 | 工具类方法 static | Minor | 无需创建实例即可调用 |
| CR-UTIL-03 | 字符串判空用 StringUtils | Major | `StringUtils.isBlank(str)` 而非 `str == null || str.isEmpty()` |
| CR-UTIL-04 | 集合判空用 CollectionUtils | Major | `CollectionUtils.isEmpty(list)` 而非 `list == null \|\| list.size() == 0` |
| CR-UTIL-05 | 日期处理用 LocalDate/LocalDateTime | Major | 禁止 Date/Calendar |
| CR-UTIL-06 | 金额用 BigDecimal | Blocker | 禁止 double/float 处理金额 |
| CR-UTIL-07 | 随机数用 ThreadLocalRandom | Minor | 多线程下性能优于 Math.random() |

---

## 四、前端 Vue3/TS 专项检查

### 4.1 Vue3 规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-VUE-01 | 使用 Composition API | Major | `<script setup>` 优先，禁止 Options API |
| CR-VUE-02 | 组件名使用 UpperCamelCase | Major | 文件名 `UserList.vue`，使用 `<UserList />` |
| CR-VUE-03 | Props 定义类型 | Major | `defineProps<{ title: string; count?: number }>()` |
| CR-VUE-04 | Emits 定义类型 | Major | `defineEmits<{ (e: 'update', value: string): void }>()` |
| CR-VUE-05 | 响应式数据用 ref/reactive | Major | 禁止直接修改 props |
| CR-VUE-06 | computed 有缓存意识 | Minor | 复杂计算用 computed，简单模板表达式可直接用 |
| CR-VUE-07 | watch 明确 deep/immediate | Minor | `watch(obj, cb, { deep: true, immediate: true })` |
| CR-VUE-08 | v-for 必须有 key | Blocker | `:key="item.id"`，禁止用 index 作 key |
| CR-VUE-09 | v-if 和 v-for 不共存 | Blocker | 用 computed 过滤后再 v-for |
| CR-VUE-10 | 组件单向数据流 | Major | 子组件不直接修改 props，通过 emit 通知父组件 |

### 4.2 TypeScript 规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-TS-01 | 禁用 any | Major | 用 unknown 替代或定义具体类型 |
| CR-TS-02 | 接口定义完整 | Major | API 请求/响应有 interface 定义 |
| CR-TS-03 | 枚举优于魔法字符串 | Minor | `enum DocStatus { DRAFT = 'draft' }` 优于 `'draft'` |
| CR-TS-04 | 类型断言谨慎使用 | Major | `as` 断言需有依据，禁止 `as any` |
| CR-TS-05 | 严格模式开启 | Major | `tsconfig.json` 中 `strict: true` |
| CR-TS-06 | 工具类型复用 | Minor | `Pick<T, K>` / `Omit<T, K>` / `Partial<T>` |
| CR-TS-07 | 函数返回类型显式 | Minor | 复杂函数显式标注返回类型 |

### 4.3 前端安全

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-FSEC-01 | 禁止 v-html 渲染用户输入 | Blocker | XSS 风险，必须用 DOMPurify 过滤 |
| CR-FSEC-02 | 不在代码中硬编码密钥 | Blocker | API key/secret 走环境变量 |
| CR-FSEC-03 | token 存储在 localStorage 或 cookie | Major | 禁止存 sessionStorage（关闭即失效） |
| CR-FSEC-04 | 请求统一加 Authorization 头 | Major | axios 拦截器统一处理 |
| CR-FSEC-05 | 401 自动跳转登录 | Major | axios 响应拦截器处理 |

### 4.4 前端性能

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-FPERF-01 | 路由懒加载 | Major | `() => import('@/views/xxx.vue')` |
| CR-FPERF-02 | 大列表虚拟滚动 | Major | 超过 100 条用 vue-virtual-scroller |
| CR-FPERF-03 | 图片懒加载 | Minor | `loading="lazy"` 或 IntersectionObserver |
| CR-FPERF-04 | 防抖/节流 | Major | 搜索框、resize、scroll 事件 |
| CR-FPERF-05 | 避免不必要的响应式 | Minor | 大数据用 shallowRef / markRaw |
| CR-FPERF-06 | 组件按需加载 | Minor | 第三方库按需引入，如 `import { debounce } from 'lodash-es'` |

---

## 五、数据库专项检查

### 5.1 表设计规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-DB-01 | 表名使用小写下划线 | Major | 如 `kb_user` / `kb_doc`，禁止 `KbUser` / `kbUser` |
| CR-DB-02 | 字段名使用小写下划线 | Major | 如 `user_id` / `created_at`，禁止 `userId` / `createdAt` |
| CR-DB-03 | 主键统一为 id（bigint） | Major | 自增或雪花 |
| CR-DB-04 | 必备字段齐全 | Major | `created_at` / `updated_at` / `deleted`（逻辑删除） |
| CR-DB-05 | 字段类型合理 | Major | 金额用 decimal，状态用 tinyint，时间用 datetime |
| CR-DB-06 | 字段注释完整 | Major | `COMMENT '用户ID'` |
| CR-DB-07 | 表字符集统一 utf8mb4 | Major | 支持 emoji |
| CR-DB-08 | 禁止使用外键约束 | Major | 在应用层保证关联，外键影响性能 |

### 5.2 索引规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-IDX-01 | 查询字段有索引 | Major | WHERE / ORDER BY / GROUP BY 字段建索引 |
| CR-IDX-02 | 联合索引遵循最左前缀 | Major | 如 `(user_id, status)` 索引，查询可命中 `user_id` 或 `user_id+status` |
| CR-IDX-03 | 索引数量 ≤ 5 个/表 | Minor | 索引过多影响写入性能 |
| CR-IDX-04 | 区分度低的字段不单独建索引 | Major | 如 `deleted` 字段只有 0/1，不单独建索引 |
| CR-IDX-05 | 唯一约束用唯一索引 | Major | 如 `user.username` 唯一索引 |
| CR-IDX-06 | EXPLAIN 验证索引命中 | Major | 慢查询必须 EXPLAIN，禁止全表扫描 |

### 5.3 SQL 规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-SQL-01 | 禁止 SELECT * | Major | 明确列出字段 |
| CR-SQL-02 | 禁止拼接 SQL 字符串 | Blocker | SQL 注入风险，用参数化查询 |
| CR-SQL-03 | 大表分页优化 | Major | `WHERE id > last_id LIMIT 20` 优于 `LIMIT 100000, 20` |
| CR-SQL-04 | 批量操作用批量语句 | Major | `INSERT INTO ... VALUES (...), (...)` 优于循环单条插入 |
| CR-SQL-05 | 避免在 WHERE 上函数操作 | Major | `WHERE DATE(created_at) = '2026-01-01'` → `WHERE created_at >= '2026-01-01' AND created_at < '2026-01-02'` |
| CR-SQL-06 | JOIN 表数量 ≤ 3 | Major | 超过需拆分查询 |
| CR-SQL-07 | LIKE 左侧禁止 % | Major | `LIKE 'abc%'` 可命中索引，`LIKE '%abc'` 全表扫描 |
| CR-SQL-08 | OR 改为 IN 或 UNION | Minor | `WHERE id = 1 OR id = 2` → `WHERE id IN (1, 2)` |

### 5.4 数据库变更规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-DBCHG-01 | 禁止 DROP TABLE / TRUNCATE | Blocker | 表结构变更一律 ALTER TABLE |
| CR-DBCHG-02 | 新增字段用 ALTER TABLE ADD COLUMN | Blocker | 先查 information_schema 判断是否存在 |
| CR-DBCHG-03 | 新增字段默认值合理 | Major | NOT NULL 字段必须有 DEFAULT |
| CR-DBCHG-04 | 新增索引用 ALTER TABLE ADD INDEX | Blocker | 先查 information_schema.STATISTICS |
| CR-DBCHG-05 | 大表变更评估锁表风险 | Major | 千万级表用 pt-online-schema-change |
| CR-DBCHG-06 | 必须有回滚脚本 | Blocker | 升级脚本 + 回滚脚本成对提交 |

---

## 六、API 接口专项检查

### 6.1 接口设计规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-API-01 | RESTful 风格 | Major | GET 查询 / POST 创建 / PUT 更新 / DELETE 删除 |
| CR-API-02 | 路径使用小写 + 短横线 | Minor | `/kb/api/user-profile` 优于 `/kb/api/userProfile` |
| CR-API-03 | 版本控制 | Minor | `/kb/api/v1/xxx` 或 header 版本号 |
| CR-API-04 | 统一返回 Result<T> | Blocker | `{code, message, data, traceId}` |
| CR-API-05 | 分页返回 PageResult<T> | Major | `{list, total, page, size}` |
| CR-API-06 | HTTP 状态码合理 | Major | 200 成功 / 400 参数错 / 401 未认证 / 403 无权限 / 404 不存在 / 500 系统错 |
| CR-API-07 | 错误码与接口规范一致 | Blocker | 见接口规范清单 v2.3 错误码对照表 |

### 6.2 参数校验

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-PARAM-01 | 使用 @Valid + JSR-303 | Major | `@NotBlank` / `@NotNull` / `@Size` / `@Pattern` |
| CR-PARAM-02 | 必填字段校验 | Blocker | 缺失返回 400 |
| CR-PARAM-03 | 字段长度校验 | Major | 如 username 1-20 字符 |
| CR-PARAM-04 | 字段格式校验 | Major | 如 email / phone / url 格式 |
| CR-PARAM-05 | 业务规则校验 | Major | 如密码强度、日期范围 |
| CR-PARAM-06 | 参数校验失败返回明确信息 | Major | `username: 用户名不能为空; password: 密码不能为空` |

### 6.3 接口文档同步

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-DOC-01 | 接口规范清单同步更新 | Blocker | 新增/变更接口必须更新 `docs/接口规范清单_v1.md` |
| CR-DOC-02 | Swagger 注解完整 | Major | `@Operation` / `@Parameter` / `@ApiResponse` |
| CR-DOC-03 | 版本号升级 | Major | 接口变更升级版本号（v2.3 → v2.4） |
| CR-DOC-04 | 破坏性变更标注 | Major | 在变更记录中标注 BREAKING CHANGE |

---

## 七、测试代码专项检查

### 7.1 单元测试规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-UT-01 | 测试类与被测类同包 | Minor | `UserService` → `UserServiceTest` 在同包 |
| CR-UT-02 | 测试方法名表达意图 | Major | `should_return_user_when_id_exists` 而非 `test1` |
| CR-UT-03 | 遵循 AAA 模式 | Major | Arrange / Act / Assert 三段式 |
| CR-UT-04 | 单测只测一个行为 | Major | 一个测试方法只断言一个行为 |
| CR-UT-05 | 不依赖外部资源 | Blocker | 用 Mock 替代数据库/HTTP 调用 |
| CR-UT-06 | 覆盖正常+异常分支 | Major | 正常路径 + 边界值 + 异常分支 |
| CR-UT-07 | 覆盖率达标 | Major | 行覆盖 ≥ 85%，分支覆盖 ≥ 80% |
| CR-UT-08 | 测试可重复执行 | Major | 不依赖执行顺序，不依赖时间（用 Clock 注入） |

### 7.2 测试数据 Builder

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-TB-01 | 使用 Builder 模式构造测试数据 | Major | `UserBuilder.aUser().withName("test").build()` |
| CR-TB-02 | 测试数据工厂复用 | Major | 提取到 `TestFixtures` 或 `TestDataFactory` |
| CR-TB-03 | 测试数据隔离 | Major | 每个测试方法独立数据，不共享可变状态 |
| CR-TB-04 | 测试数据有意义 | Minor | `user("alice")` 优于 `user("test1")` |

### 7.3 集成测试规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-IT-01 | 使用 @SpringBootTest | Major | 集成测试注解完整 |
| CR-IT-02 | 使用 Testcontainers（可选） | Minor | 真实容器化数据库 |
| CR-IT-03 | 测试数据清理 | Major | `@Transactional` + `@Rollback` 或 `@AfterEach` 清理 |
| CR-IT-04 | 不污染生产数据 | Blocker | 测试库与生产库隔离 |

---

## 八、配置与部署专项检查

### 8.1 配置管理

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-CONF-01 | 敏感配置走环境变量 | Blocker | 密码/密钥/token 不硬编码 |
| CR-CONF-02 | 配置项有默认值 | Major | `${DB_HOST:localhost}` |
| CR-CONF-03 | 环境隔离 | Major | DEV/SIT/TEST/UAT/STAGING/PROD 配置分离 |
| CR-CONF-04 | .env 不提交到 git | Blocker | `.gitignore` 排除 |
| CR-CONF-05 | KB_CONTEXT 五处一致 | Blocker | .env / 前端 .env / 网关 / 双层 Nginx |

### 8.2 部署规范

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-DEPLOY-01 | Dockerfile 合理 | Major | 多阶段构建，基础镜像指定版本 |
| CR-DEPLOY-02 | docker-compose.yml 合理 | Major | 资源限制 / 健康检查 / 依赖顺序 |
| CR-DEPLOY-03 | 镜像走私服 | Blocker | `nexus.marschat.online/repository/docker-hosted/` |
| CR-DEPLOY-04 | 部署文档同步 | Major | `docs/部署文档_v1.x.md` 更新 |
| CR-DEPLOY-05 | 回滚脚本就绪 | Blocker | 升级脚本 + 回滚脚本成对 |

---

## 九、性能与安全专项检查

> 详见独立文档：
> - `checklists/安全Checklist.md`（SOP 附录 D）
> - `checklists/性能Checklist.md`（SOP 附录 E）

### 9.1 性能要点

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-PERF-01 | 无 N+1 查询 | Blocker | 用 JOIN 或批量查询替代循环查询 |
| CR-PERF-02 | 缓存合理使用 | Major | 热点数据缓存到 Redis |
| CR-PERF-03 | 异步处理耗时操作 | Major | 文件解析/通知用 @Async 或消息队列 |
| CR-PERF-04 | 分页查询合理 | Major | 禁止一次查询全部数据 |
| CR-PERF-05 | 批量操作替代循环 | Major | `saveBatch` 替代循环 `save` |

### 9.2 安全要点

| 编号 | 检查项 | 等级 | 说明 |
|------|--------|------|------|
| CR-SEC-01 | 无 SQL 注入 | Blocker | 参数化查询，禁止拼接 |
| CR-SEC-02 | 无 XSS | Blocker | 输出转义，v-html 慎用 |
| CR-SEC-03 | 密码加密存储 | Blocker | BCrypt，禁止明文/MD5 |
| CR-SEC-04 | 越权校验 | Blocker | 资源 owner 校验 |
| CR-SEC-05 | 敏感数据脱敏 | Major | 日志/响应中手机号/邮箱脱敏 |

---

## 十、CR 评审结论模板

```markdown
## CR 评审结论

**MR**: !xxx（标题）
**评审人**: xxx
**评审日期**: 2026-xx-xx
**评审结论**: ✅ Approve / ⚠️ Request Changes / ❌ Reject

### Blocker 项（必须修复）
- [ ] [文件路径:行号] 问题描述 + 修改建议

### Major 项（建议修复）
- [ ] [文件路径:行号] 问题描述 + 修改建议

### Minor 项（可后续优化）
- [ ] [文件路径:行号] 问题描述 + 修改建议

### 亮点
- （值得推广的写法）

### 总评
（整体评价，1-3 句话）
```

---

## 附录 A：评审等级定义

| 等级 | 含义 | 处理方式 |
|------|------|---------|
| **Blocker** | 阻断性问题，必须修复 | 修复后方可合并 |
| **Major** | 重要问题，强烈建议修复 | 评审人讨论后决定是否必须修复 |
| **Minor** | 次要问题，可后续优化 | 记录 TODO，本次可不修复 |

---

## 附录 B：常见反模式速查

| 反模式 | 正确做法 |
|--------|---------|
| `catch (Exception e) {}` | 至少 `log.error("xxx", e)` 或重新抛出 |
| `e.printStackTrace()` | `log.error("xxx", e)` |
| `System.out.println` | `log.info("xxx")` |
| `new Date()` | `LocalDateTime.now()` |
| `String.format` | `MessageFormat` 或 SLF4J 占位符 |
| 字符串拼接 SQL | MyBatis 参数化 |
| `SELECT *` | 明确字段 |
| `LIKE '%abc'` | `LIKE 'abc%'` 或全文检索 |
| `v-for` 无 key | `:key="item.id"` |
| `any` 类型 | 定义 interface |
| `@Autowired` 字段 | 构造器注入 |
| 硬编码密码 | 环境变量 |
| 魔法数字 | 常量/枚举 |
| 重复代码 ≥ 3 处 | 提取公共方法 |

---

## 附录 C：变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|---------|------|
| v1.0 | 2026-06-28 | 初版创建 | 测试组 |
| v1.1 | 2026-06-28 | 对齐 SOP V1.1 附录 C，补充测试数据 Builder、KB_CONTEXT 五处一致、反模式速查 | 测试组 |
