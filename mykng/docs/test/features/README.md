# MyKNG 知识库平台 — BDD Feature 文件集中备份

> **版本**：v1.2
> **更新日期**：2026-06-28
> **SOP 对应**：SOP V1.1 附录G 目录结构合规性要求 / 2.5.1 BDD 业务场景
> **工具**：Cucumber-JVM 7.15+（Gherkin 语法）+ JUnit 5

---

## 一、本目录用途

本目录是 **BDD feature 文件的集中备份**，满足 SOP V1.1 附录G 对目录结构合规性的要求。

> ⚠️ **重要**：本目录仅作为只读备份，**不参与实际测试运行**。
>
> 实际运行 BDD 测试时，使用各模块 `src/test/resources/features/` 下的**原始文件**。
> 如需修改场景，请修改原始文件后重新同步到此备份目录。

---

## 二、Feature 文件清单

| 序号 | Feature 文件 | 来源模块 | 场景数 | 覆盖业务流程 | 优先级 |
|------|------------|---------|--------|------------|--------|
| 1 | auth.feature | kb-auth | 12 | FLOW-01 登录认证闭环 | P0 |
| 2 | file_upload_search.feature | kb-file | 27 | FLOW-03 文件上传-搜索 | P0 |
| 3 | knowledge_import.feature | kb-intelligence | 31 | 知识导入-文档解析-实体提取-双维度渲染 | P0 |
| 4 | doc_lifecycle.feature | kb-knowledge | 20 | FLOW-02 知识空间-文档管理 | P0 |
| 5 | share_access.feature | kb-knowledge | 21 | FLOW-04 分享-访问 | P1 |
| 6 | ops_dashboard.feature | kb-ops | 34 | FLOW-05 运维看板 / FLOW-08 端口-凭据 / FLOW-09 域名管理 | P2 |
| **合计** | **6 个文件** | **5 个模块** | **145** | — | — |

---

## 三、原始文件位置（实际运行时使用）

```
mykng/
├── kb-auth/src/test/resources/features/
│   └── auth.feature                              # 12 场景
├── kb-file/src/test/resources/features/
│   └── file_upload_search.feature                # 27 场景
├── kb-intelligence/src/test/resources/features/
│   └── knowledge_import.feature                  # 31 场景
├── kb-knowledge/src/test/resources/features/
│   ├── doc_lifecycle.feature                     # 20 场景
│   └── share_access.feature                      # 21 场景
└── kb-ops/src/test/resources/features/
    └── ops_dashboard.feature                     # 34 场景
```

---

## 四、Feature 文件示例（auth.feature 片段）

```gherkin
# language: zh-CN
Feature: 用户认证
  作为一个用户
  我想要登录系统
  以便访问我的知识库

  Scenario: 正确账号密码登录成功
    Given 系统存在用户 "admin"，密码 "admin123"，状态为启用
    When 用户使用 "admin" 和 "admin123" 登录
    Then 返回有效的 accessToken 和 refreshToken
    And accessToken 有效期为 15 分钟
    And refreshToken 有效期为 7 天
```

---

## 五、Cucumber 执行方式

### 5.1 Maven 执行

```bash
# 执行所有 BDD 测试
mvn test -Dtest=CucumberIT -pl kb-auth,kb-file,kb-knowledge,kb-ops,kb-intelligence

# 仅执行 kb-auth 的 BDD
mvn test -Dtest=CucumberIT -pl kb-auth

# 指定 feature 文件执行
mvn test -Dtest=CucumberIT -pl kb-auth -Dcucumber.features="src/test/resources/features/auth.feature"
```

### 5.2 运行器配置

每个模块创建 Cucumber 运行器类（如 `CucumberIT.java`）：

```java
@CucumberOptions(
    features = "src/test/resources/features",
    glue = "com.kb.auth",
    plugin = {"pretty", "html:target/cucumber/auth.html", "json:target/cucumber/auth.json"}
)
public class CucumberIT {}
```

### 5.3 报告输出

| 报告类型 | 位置 | 格式 |
|---------|------|------|
| 控制台输出 | 终端 | pretty |
| HTML 报告 | `test-output/bdd/cucumber/{module}.html` | HTML |
| JSON 报告 | `test-output/bdd/cucumber/{module}.json` | JSON（供 CI 集成） |

---

## 六、通过准则

| 准则 | 要求 |
|------|------|
| 场景全绿 | 所有 Scenario 状态为 Passed |
| 步骤全绿 | 所有 Step 状态为 Passed |
| 无未定义步骤 | 无 Undefined 步骤 |
| 无跳过步骤 | 无 Skipped 步骤（除前置失败导致的跳过） |

---

## 七、同步说明

当原始 feature 文件发生变更后，需同步到此备份目录：

```powershell
# PowerShell 同步脚本（在项目根目录执行）
Copy-Item kb-auth\src\test\resources\features\auth.feature docs\test\features\
Copy-Item kb-file\src\test\resources\features\file_upload_search.feature docs\test\features\
Copy-Item kb-intelligence\src\test\resources\features\knowledge_import.feature docs\test\features\
Copy-Item kb-knowledge\src\test\resources\features\doc_lifecycle.feature docs\test\features\
Copy-Item kb-knowledge\src\test\resources\features\share_access.feature docs\test\features\
Copy-Item kb-ops\src\test\resources\features\ops_dashboard.feature docs\test\features\
```

---

## 八、参考文档

| 文档 | 用途 |
|------|------|
| [../test-cases/测试用例清单.md](../test-cases/测试用例清单.md) | BDD 用例编号清单 |
| [../test-plan/测试计划_v1.1.md](../test-plan/测试计划_v1.1.md) | 层级 6 业务场景验收 |
| [../../architecture.md](../../architecture.md) | 微服务架构与模块划分 |

---

## 九、变更记录

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| v1.1 | 2026-06-28 | 初版：5 个 feature 文件，7 个场景 |
| v1.2 | 2026-06-28 | SOP附录G合规更新：6 个 feature 文件，145 个场景；补充 kb-intelligence 模块 |
