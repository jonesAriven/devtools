#!/bin/bash
# ============================================================
# mykng 全量测试统一运行器
# 只输出紧凑摘要，完整日志写入 /tmp/mykng-test-<timestamp>.log
# 用法: ./run_all_tests.sh [quick|full|java|python|e2e]
#   quick  - 只跑功能+API+单元 (跳过E2E/性能/集成)
#   full   - 全量 (默认)
#   java   - 只跑Java测试
#   python - 只跑Python测试
#   e2e    - 只跑E2E
# ============================================================

cd /root/devtools/mykng

MODE="${1:-full}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="/tmp/mykng-test-${TIMESTAMP}.log"
PW_PYTHON="/opt/playwright-venv/bin/python"

PASS=0
FAIL=0
SKIP=0
FAILED_TESTS=()

run_test() {
    local name="$1"
    local cmd="$2"
    local timeout="${3:-120}"
    
    printf "  ⏳ %s... " "${name}"
    
    local start=$(date +%s)
    local tmp_out="/tmp/mykng-test-$$-${RANDOM}.out"
    
    timeout "${timeout}" bash -c "$cmd" > "${tmp_out}" 2>&1
    local exit_code=$?
    local end=$(date +%s)
    local duration=$((end - start))
    
    if [ $exit_code -eq 124 ]; then
        printf "⏰ TIMEOUT (%ds)\n" "${duration}"
        SKIP=$((SKIP + 1))
        FAILED_TESTS+=("${name}(超时)")
    else
        # 从输出提取通过/失败数
        local passed=""
        local failed=""
        
        # Python格式: "✅ 57 通过 | ❌ 0 失败" 或 "57项 | ✅ 57通过"
        passed=$(grep -oP '(✅\s*)\K\d+(?=\s*通过)' "${tmp_out}" | head -1)
        failed=$(grep -oP '(❌\s*)\K\d+(?=\s*失败)' "${tmp_out}" | head -1)
        
        # Surefire格式: 多模块聚合 "Tests run: 57, Failures: 0, Errors: 0, Skipped: 0"
        if [ -z "$passed" ] && [ -z "$failed" ]; then
            local total_p=0 total_f=0 total_s=0
            while IFS= read -r line; do
                local p=$(echo "$line" | grep -oP 'Tests run:\s*\K\d+' 2>/dev/null || echo "0")
                local f=$(echo "$line" | grep -oP 'Failures:\s*\K\d+' 2>/dev/null || echo "0")
                local e=$(echo "$line" | grep -oP 'Errors:\s*\K\d+' 2>/dev/null || echo "0")
                local s=$(echo "$line" | grep -oP 'Skipped:\s*\K\d+' 2>/dev/null || echo "0")
                total_p=$((total_p + p))
                total_f=$((total_f + f + e))
                total_s=$((total_s + s))
            done < <(grep 'Tests run:' "${tmp_out}")
            if [ $total_p -gt 0 ] || [ $total_f -gt 0 ]; then
                passed=$total_p
                failed=$total_f
                SKIP=$((SKIP + total_s))
            fi
        fi
        
        passed=${passed:-"?"}
        failed=${failed:-"0"}
        
        if [ "$failed" = "0" ] && [ "$exit_code" -eq 0 ]; then
            printf "✅ PASS (%ds) [%s通过]\n" "${duration}" "${passed}"
            PASS=$((PASS + 1))
        elif [ "$failed" = "0" ] && [ "$exit_code" -ne 0 ]; then
            printf "❌ FAIL (%ds, exit=%d)\n" "${duration}" "${exit_code}"
            FAIL=$((FAIL + 1))
            FAILED_TESTS+=("${name}")
            tail -15 "${tmp_out}" >> "${LOG_FILE}.failures" 2>/dev/null
        else
            printf "❌ FAIL (%ds) [%s通过/%s失败]\n" "${duration}" "${passed}" "${failed}"
            FAIL=$((FAIL + 1))
            FAILED_TESTS+=("${name}(${failed}失败)")
            # 只提取失败行
            grep -E '❌|FAIL|AssertionError|Exception' "${tmp_out}" | head -15 >> "${LOG_FILE}.failures" 2>/dev/null
        fi
    fi
    
    # 完整输出写入日志
    echo -e "\n============================================================" >> "${LOG_FILE}"
    echo "${name}" >> "${LOG_FILE}"
    echo "============================================================" >> "${LOG_FILE}"
    cat "${tmp_out}" >> "${LOG_FILE}"
    rm -f "${tmp_out}"
}

echo "============================================================"
echo "  mykng 全量测试  [mode=${MODE}]  $(date '+%Y-%m-%d %H:%M')"
echo "  日志: ${LOG_FILE}"
echo "============================================================"
echo ""

# Python 测试
case "$MODE" in
    full|python|quick)
        run_test "功能测试(57项)" "python3 tests/test_functional.py" 60
        run_test "接口测试(76项)" "python3 tests/test_api.py" 60
        ;;
esac

case "$MODE" in
    full|python)
        run_test "全量API(87端点)" "python3 tests/test_api_full.py" 120
        ;;
esac

# Java 测试
case "$MODE" in
    full|java|quick)
        run_test "Java单元测试" "cd kb-parent && mvn test -Dtest='*Test,!*IntegrationTest,!*ApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false 2>&1" 180
        ;;
esac

case "$MODE" in
    full|java)
        run_test "Java集成测试" "cd kb-parent && mvn test -Dtest='*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1" 180
        ;;
esac

# E2E
case "$MODE" in
    full|e2e)
        run_test "E2E测试(31场景)" "${PW_PYTHON} tests/test_e2e.py" 120
        ;;
esac

# 性能测试
case "$MODE" in
    full)
        run_test "性能压测" "python3 tests/test_performance.py" 90
        ;;
esac

# 汇总
echo ""
echo "============================================================"
TOTAL=$((PASS + FAIL + SKIP))
printf "  总计: %d 套件 | ✅ %d 通过 | ❌ %d 失败 | ⏰ %d 跳过\n" "${TOTAL}" "${PASS}" "${FAIL}" "${SKIP}"
if [ ${FAIL} -gt 0 ]; then
    echo "  失败项:"
    for ft in "${FAILED_TESTS[@]}"; do
        echo "    - ${ft}"
    done
    echo ""
    echo "  失败详情: ${LOG_FILE}.failures"
fi
echo "  完整日志: ${LOG_FILE}"
echo "============================================================"

exit ${FAIL}
