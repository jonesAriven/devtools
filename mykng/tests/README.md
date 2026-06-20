# mykng 知识库 - 自动化测试

## 文件结构

```
tests/
├── run_all_tests.sh          # 全量测试运行器
├── test_api.py               # API 接口测试 (76 用例)
├── test_ui_pages.py          # UI 页面 HTTP 级测试 (28 用例)
├── test_e2e_playwright.py    # E2E 浏览器测试 (Playwright)
├── api-test-report.json      # API 测试报告 (自动生成)
└── screenshots/              # E2E 截图目录 (自动生成)
```

## 快速运行

```bash
# 运行全部测试
bash tests/run_all_tests.sh

# 只跑 API 测试
bash tests/run_all_tests.sh api

# 只跑 UI 页面测试
bash tests/run_all_tests.sh ui

# 只跑 E2E 浏览器测试
bash tests/run_all_tests.sh e2e
```

## 测试覆盖

### API 接口测试 (`test_api.py`)
- 20 个测试模块，76 个测试用例
- 覆盖全部 83 个接口
- 包含完整 CRUD 流程测试
- 自动清理测试数据

| 模块 | 用例数 | 覆盖范围 |
|------|--------|---------|
| 认证服务 | 8 | 登录/登出/刷新/用户信息/密码/API Token |
| 文件服务 | 10 | 上传/合并/列表/解析/下载/删除/收藏/移动/桶 |
| 知识服务 | 32 | 文档/文件夹/搜索/分享/空间/标签/回收站/版本/网页 |
| 运维服务 | 18 | 矛盾/看板/部署/主机/导入/知识/服务 |
| 清理 | 8 | 测试数据清理 |

### UI 页面测试 (`test_ui_pages.py`)
- 9 个测试模块，28 个测试用例
- 测试页面加载、重定向、静态资源、SPA 路由回退
- 验证 HTML 结构、缓存头、CORS 配置
- 模拟登录流程和认证后 API 调用

### E2E 浏览器测试 (`test_e2e_playwright.py`)
- 9 个测试模块
- 真实浏览器操作（需要 Playwright + Chromium）
- 测试登录→Dashboard→空间→文档→搜索→设置→退出全流程
- 自动截图保存
- 响应式测试（手机/平板/桌面）
- Console 错误检测

## 环境要求

- Python 3.11+
- `requests` 库（API + UI 测试）
- `playwright` + Chromium（E2E 测试，可选）

安装 Playwright:
```bash
pip install playwright
playwright install chromium
```

## 测试目标

- 本地: `http://localhost:8090/kb/`
- 公网: `https://tools.marschat.online/kb/`
- 管理员: `admin` / `admin123`

## 报告

- API 测试报告: `tests/api-test-report.json`（每次运行自动覆盖）
- E2E 截图: `tests/screenshots/`（按步骤编号命名）
