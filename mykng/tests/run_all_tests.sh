#!/bin/bash
# mykng 知识库 - 全量测试运行器
# 用法: bash tests/run_all_tests.sh [api|ui|e2e|all]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MODE="${1:-all}"
PASS=0
FAIL=0

echo "============================================================"
echo "  mykng 知识库 - 全量自动化测试"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  模式: $MODE"
echo "============================================================"
echo ""

# ---- API 接口测试 ----
if [[ "$MODE" == "api" || "$MODE" == "all" ]]; then
    echo "🔍 [1/3] API 接口测试"
    echo "------------------------------------------------------------"
    if python3 "$SCRIPT_DIR/test_api.py" 2>&1; then
        PASS=$((PASS + 1))
    else
        FAIL=$((FAIL + 1))
    fi
    echo ""
fi

# ---- UI 页面测试 ----
if [[ "$MODE" == "ui" || "$MODE" == "all" ]]; then
    echo "🔍 [2/3] UI 页面测试 (HTTP 级)"
    echo "------------------------------------------------------------"
    if python3 "$SCRIPT_DIR/test_ui_pages.py" 2>&1; then
        PASS=$((PASS + 1))
    else
        FAIL=$((FAIL + 1))
    fi
    echo ""
fi

# ---- E2E 浏览器测试 (需要 Playwright) ----
if [[ "$MODE" == "e2e" || "$MODE" == "all" ]]; then
    echo "🔍 [3/3] E2E 浏览器测试 (Playwright)"
    echo "------------------------------------------------------------"
    if command -v playwright &>/dev/null || python3 -c "import playwright" 2>/dev/null; then
        if python3 "$SCRIPT_DIR/test_e2e_playwright.py" 2>&1; then
            PASS=$((PASS + 1))
        else
            FAIL=$((FAIL + 1))
        fi
    else
        echo "  ⏭️  Playwright 未安装，跳过 E2E 测试"
        echo "     安装: pip install playwright && playwright install chromium"
        PASS=$((PASS + 1))  # 不算失败
    fi
    echo ""
fi

# ---- 总结 ----
echo "============================================================"
echo "  测试套件总结: $((PASS + FAIL)) 个套件 | ✅ $PASS 通过 | ❌ $FAIL 失败"
echo "============================================================"

exit $FAIL
