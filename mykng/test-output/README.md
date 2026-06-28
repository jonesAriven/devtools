# test-output/ 测试产物目录

> 本目录按 SOP V1.1 附录B 规范组织测试产物。
> 大部分子目录已在 `.gitignore` 中忽略内容，通过 `.gitkeep` 占位文件保持目录结构。
> 仅 `reports/` 目录的正式报告提交 Git 长期保留。

## 目录结构（SOP附录B）

```
test-output/
├── unit/                   # 单元测试产物（不入库，运行时自动生成）
│   └── java/               # JaCoCo覆盖率 + Surefire报告 + PITest变异报告
├── integration/            # 集成测试产物（不入库，运行时自动生成）
│   ├── java/               # Failsafe集成测试报告
│   └── apifox/             # Apifox接口测试报告（预留）
├── bdd/                    # BDD测试产物（不入库，运行时自动生成）
│   └── cucumber/           # Cucumber BDD报告
├── e2e/                    # E2E测试产物（不入库，运行时自动生成）
│   ├── playwright-report/  # Playwright HTML报告
│   ├── screenshots/        # E2E截图（成功+失败）
│   │   └── failed/         # 失败用例截图
│   ├── videos/             # 失败录屏
│   └── traces/             # Trace Viewer文件
├── performance/            # 性能测试产物（不入库，运行时自动生成）
│   └── jmeter/             # JMeter压测报告
├── security/               # 安全扫描产物（不入库，运行时自动生成）
│   ├── sonar/              # SonarQube报告
│   ├── dependency-check/   # OWASP依赖扫描
│   └── zap/                # ZAP安全扫描
├── logs/                   # 测试日志（不入库，运行时自动生成）
│   ├── backend/            # 后端应用日志
│   ├── frontend/           # 前端构建日志
│   ├── db/                 # 数据库日志
│   └── error/              # 错误日志归档
├── evidence/               # 手工测试截图证据（选择性入库）
│   ├── 冒烟/               # 冒烟测试截图
│   ├── 业务/               # 业务功能测试截图
│   ├── 兼容/               # 兼容性测试截图
│   ├── App/                # App端测试截图
│   ├── UAT/                # 用户验收测试截图
│   ├── 回归/               # 回归测试截图
│   └── 灰度发布/           # 灰度发布测试截图
└── reports/                # ✅ 最终版测试报告（必须入库，长期保留）
    ├── summary.md          # 测试总结
    ├── 测试报告/            # 正式测试报告
    ├── 性能报告/            # 性能测试报告
    ├── 变异测试报告/        # PITest变异测试报告
    ├── BDD测试报告/         # Cucumber BDD测试报告
    └── 验收报告/            # 用户验收报告
```

## 目录创建状态

### ✅ 已创建（含 .gitkeep 占位）

以下子目录已通过 `.gitkeep` 占位文件创建，空目录结构可被 Git 跟踪：

| 目录 | 状态 | 说明 |
|------|------|------|
| `integration/java/` | ✅ 已创建 | 集成测试报告占位 |
| `bdd/cucumber/` | ✅ 已创建 | BDD报告占位 |
| `e2e/playwright-report/` | ✅ 已创建 | Playwright报告占位 |
| `e2e/screenshots/failed/` | ✅ 已创建 | 失败截图占位 |
| `e2e/videos/` | ✅ 已创建 | 录屏占位 |
| `e2e/traces/` | ✅ 已创建 | Trace文件占位 |
| `performance/jmeter/` | ✅ 已创建 | JMeter报告占位 |
| `security/sonar/` | ✅ 已创建 | SonarQube报告占位 |
| `security/dependency-check/` | ✅ 已创建 | OWASP依赖扫描占位 |
| `security/zap/` | ✅ 已创建 | ZAP扫描报告占位 |
| `logs/backend/` | ✅ 已创建 | 后端日志占位 |
| `logs/frontend/` | ✅ 已创建 | 前端日志占位 |
| `logs/db/` | ✅ 已创建 | 数据库日志占位 |
| `logs/error/` | ✅ 已创建 | 错误日志占位 |
| `evidence/冒烟/` | ✅ 已创建 | 冒烟测试证据占位 |
| `evidence/业务/` | ✅ 已创建 | 业务测试证据占位 |
| `evidence/兼容/` | ✅ 已创建 | 兼容性测试证据占位 |
| `evidence/App/` | ✅ 已创建 | App测试证据占位 |
| `evidence/UAT/` | ✅ 已创建 | UAT证据占位 |
| `evidence/回归/` | ✅ 已创建 | 回归测试证据占位 |
| `evidence/灰度发布/` | ✅ 已创建 | 灰度发布证据占位 |
| `reports/测试报告/` | ✅ 已创建 | 测试报告占位 |
| `reports/性能报告/` | ✅ 已创建 | 性能报告占位 |
| `reports/变异测试报告/` | ✅ 已创建 | 变异测试报告占位 |
| `reports/BDD测试报告/` | ✅ 已创建 | BDD报告占位 |
| `reports/验收报告/` | ✅ 已创建 | 验收报告占位 |

### ⏳ 运行时自动生成

以下子目录在测试运行时由 Maven 插件 / 测试框架自动生成，无需预先创建：

| 目录 | 生成方式 |
|------|---------|
| `unit/java/surefire-reports/` | `mvn test` → Surefire 插件 |
| `unit/java/jacoco/` | `mvn test` → JaCoCo 插件 |
| `unit/java/pit-reports/` | `mvn test -Pmutation` → PITest 插件 |
| `integration/java/failsafe-reports/` | `mvn verify` → Failsafe 插件 |
| `bdd/cucumber/*.html` | `mvn test -Dtest=CucumberIT` → Cucumber 插件 |
| `e2e/playwright-report/*.html` | `npx playwright test` → Playwright |
| `e2e/screenshots/*.png` | `npx playwright test` → 失败自动截图 |
| `e2e/videos/*.webm` | `npx playwright test` → 失败自动录屏 |
| `e2e/traces/*.zip` | `npx playwright test` → Trace Viewer |
| `performance/jmeter/*.jtl` | JMeter 命令行模式 |
| `security/sonar/*.json` | SonarQube 扫描 |
| `security/dependency-check/*.html` | OWASP Dependency-Check |
| `security/zap/*.html` | ZAP 安全扫描 |

## Git 跟踪规则

| 目录 | 是否提交 | 说明 |
|------|---------|------|
| `unit/` `integration/` `e2e/` `performance/` `security/` `logs/` `bdd/` | ❌ 不提交 | 内容自动生成，可随时重建；仅 `.gitkeep` 占位入库 |
| `evidence/` | ⚠️ 选择性提交 | 关键节点截图按需提交 |
| `reports/` | ✅ 必须提交 | 最终版正式报告，永久留存 |

> `.gitignore` 配置说明：被忽略目录的内容不提交，但通过 `!**/.gitkeep` 规则保留 `.gitkeep` 占位文件，使空目录结构可被 Git 跟踪。

## 生成方式

测试产物由 Maven 插件配置自动输出（见 `kb-parent/pom.xml`）：
- Surefire → `unit/java/surefire-reports/`
- Failsafe → `integration/java/failsafe-reports/`
- JaCoCo → `unit/java/jacoco/`
- PITest → `unit/java/pit-reports/`

执行 `mvn test` 或 `mvn verify` 后自动生成。
E2E 测试产物由 Playwright 配置输出（见 `playwright.config.ts`）。
