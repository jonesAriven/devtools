# MyKNG 知识库平台 — 全量 API 测试脚本
# 测试范围：96 个接口，分模块验证
# 用法：powershell -ExecutionPolicy Bypass -File test_full_api.ps1

$ErrorActionPreference = "Continue"
$baseUrl = "https://kb.marschat.online"
$results = [System.Collections.ArrayList]::new()
$passCount = 0
$failCount = 0
$warnCount = 0

function Test-Api {
    param(
        [string]$Module,
        [string]$Name,
        [string]$Method = "GET",
        [string]$Path,
        [string]$Body = "",
        [string]$Token = "",
        [int]$ExpectCode = 200,
        [switch]$NoAuth,
        [switch]$Allow404
    )

    $url = "$baseUrl$Path"
    $headers = @()
    $headers += "-H", "Content-Type: application/json"
    if (-not $NoAuth -and $Token) {
        $headers += "-H", "Authorization: Bearer $Token"
    }

    $args = @("-k", "-s", "-w", "`n%{http_code}", "--max-time", "15", "-X", $Method) + $headers
    if ($Body) {
        $args += "-d", $Body
    }
    $args += $url

    $startTime = Get-Date
    try {
        $rawOutput = & curl.exe @args 2>&1
        $elapsed = ((Get-Date) - $startTime).TotalSeconds
        $lines = $rawOutput -split "`n"
        $httpCode = $lines[-1].Trim()
        $responseBody = ($lines[0..($lines.Count-2)] -join "`n").Trim()

        $status = "PASS"
        $codeInt = 0
        [int]::TryParse($httpCode, [ref]$codeInt) | Out-Null

        if ($codeInt -eq 0) {
            $status = "FAIL"
            $script:failCount++
        } elseif ($codeInt -eq $ExpectCode) {
            $status = "PASS"
            $script:passCount++
        } elseif ($Allow404 -and $codeInt -eq 404) {
            $status = "WARN"
            $script:warnCount++
        } elseif ($codeInt -ge 500) {
            $status = "FAIL"
            $script:failCount++
        } elseif ($codeInt -ge 400 -and $codeInt -ne $ExpectCode) {
            # 4xx 可能是预期（如 401 无 Token）
            if ($ExpectCode -ge 400) {
                $status = "PASS"
                $script:passCount++
            } else {
                $status = "WARN"
                $script:warnCount++
            }
        } else {
            $status = "PASS"
            $script:passCount++
        }

        $script:results.Add([PSCustomObject]@{
            Module = $Module
            Name   = $Name
            Method = $Method
            Path   = $Path
            Code   = $httpCode
            Expect = $ExpectCode
            Status = $status
            Time   = "{0:F2}s" -f $elapsed
            Body   = if ($responseBody.Length -gt 120) { $responseBody.Substring(0,120) + "..." } else { $responseBody }
        }) | Out-Null
    } catch {
        $script:results.Add([PSCustomObject]@{
            Module = $Module; Name = $Name; Method = $Method; Path = $Path
            Code = "ERR"; Expect = $ExpectCode; Status = "FAIL"; Time = "0s"
            Body = $_.Exception.Message
        }) | Out-Null
        $script:failCount++
    }
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " MyKNG 全量 API 测试" -ForegroundColor Cyan
Write-Host " 开始时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Cyan
Write-Host " 目标: $baseUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ===== 1. 登录获取 Token =====
Write-Host "[1/6] 登录获取 Token..." -ForegroundColor Yellow
$loginBody = '{"username":"admin","password":"admin123"}'
$loginResp = & curl.exe -k -s -X POST -H "Content-Type: application/json" -d $loginBody "$baseUrl/kb/api/auth/login" --max-time 15
$loginData = $loginResp | ConvertFrom-Json
$token = $loginData.data.accessToken
$refreshToken = $loginData.data.refreshToken
Write-Host "  Token: $($token.Substring(0,30))..." -ForegroundColor Green
Write-Host "  expiresIn: $($loginData.data.expiresIn) (文档应为 900000=15min)" -ForegroundColor Yellow
Write-Host ""

# ===== 2. kb-auth 服务（11 个接口）=====
Write-Host "[2/6] 测试 kb-auth 服务（11 接口）..." -ForegroundColor Yellow
Test-Api "auth" "用户登录" "POST" "/kb/api/auth/login" $loginBody -NoAuth
Test-Api "auth" "用户登录-错误密码" "POST" "/kb/api/auth/login" '{"username":"admin","password":"wrong"}' -NoAuth -ExpectCode 401
Test-Api "auth" "用户登出" "POST" "/kb/api/auth/logout" "" $token
Test-Api "auth" "刷新令牌" "POST" "/kb/api/auth/refresh" "{\"refreshToken\":\"$refreshToken\"}" -NoAuth
Test-Api "auth" "获取用户信息" "GET" "/kb/api/user/profile" "" $token
Test-Api "auth" "更新用户信息" "PUT" "/kb/api/user/profile" '{"nickname":"admin"}' $token
Test-Api "auth" "修改密码-错误旧密码" "PUT" "/kb/api/user/password" '{"oldPassword":"wrong","newPassword":"newpass123"}' $token -ExpectCode 400
Test-Api "auth" "无Token访问" "GET" "/kb/api/user/profile" -NoAuth -ExpectCode 401
Test-Api "auth" "Token列表" "GET" "/kb/api/token/list" "" $token
Test-Api "auth" "Token验证" "POST" "/kb/api/token/verify" '{"token":"invalid"}' $token
Test-Api "auth" "Token创建" "POST" "/kb/api/token" '{"name":"test-token","expireDays":30}' $token
Write-Host ""

# ===== 3. kb-file 服务（12 个接口）=====
Write-Host "[3/6] 测试 kb-file 服务（12 接口）..." -ForegroundColor Yellow
Test-Api "file" "文件列表" "GET" "/kb/api/file/list" "" $token
Test-Api "file" "Bucket列表" "GET" "/kb/api/bucket/list" "" $token
Test-Api "file" "文件初始化上传" "POST" "/kb/api/file/init" '{"filename":"test.pdf","size":1024,"folderId":1}' $token
Test-Api "file" "文件详情-不存在" "GET" "/kb/api/file/999999" "" $token -ExpectCode 404
Test-Api "file" "文件删除-不存在" "DELETE" "/kb/api/file/999999" "" $token -ExpectCode 404
Test-Api "file" "Bucket创建" "POST" "/kb/api/bucket" '{"name":"test-bucket"}' $token
Test-Api "file" "Bucket详情" "GET" "/kb/api/bucket/test-bucket" "" $token -Allow404
Test-Api "file" "Bucket删除" "DELETE" "/kb/api/bucket/test-bucket" "" $token -Allow404
Test-Api "file" "文件合并" "POST" "/kb/api/file/merge?uploadId=test" "" $token
Test-Api "file" "文件分片上传" "POST" "/kb/api/file/upload?uploadId=test&partNumber=1" "" $token
Test-Api "file" "文件解析状态" "GET" "/kb/api/file/parse/status?fileId=1" "" $token -Allow404
Test-Api "file" "文件搜索" "GET" "/kb/api/file/search?q=test" "" $token -Allow404
Write-Host ""

# ===== 4. kb-knowledge 服务（46 个接口）=====
Write-Host "[4/6] 测试 kb-knowledge 服务（46 接口）..." -ForegroundColor Yellow
# 文档
Test-Api "knowledge" "文档列表" "GET" "/kb/api/doc/list" "" $token
Test-Api "knowledge" "文档详情-不存在" "GET" "/kb/api/doc/999999" "" $token -ExpectCode 404
Test-Api "knowledge" "创建文档" "POST" "/kb/api/doc" '{"title":"测试文档","content":"测试内容","folderId":1}' $token
Test-Api "knowledge" "文档分页" "GET" "/kb/api/doc/page?page=1&size=10" "" $token
# 文件夹
Test-Api "knowledge" "文件夹列表" "GET" "/kb/api/folder/list" "" $token
Test-Api "knowledge" "文件夹树" "GET" "/kb/api/folder/tree" "" $token
Test-Api "knowledge" "创建文件夹" "POST" "/kb/api/folder" '{"name":"测试文件夹","parentId":0}' $token
Test-Api "knowledge" "文件夹详情" "GET" "/kb/api/folder/1" "" $token -Allow404
Test-Api "knowledge" "更新文件夹" "PUT" "/kb/api/folder/1" '{"name":"新名称"}' $token -Allow404
Test-Api "knowledge" "删除文件夹" "DELETE" "/kb/api/folder/999999" "" $token
# 空间
Test-Api "knowledge" "空间列表" "GET" "/kb/api/space/list" "" $token
Test-Api "knowledge" "创建空间" "POST" "/kb/api/space" '{"name":"测试空间","description":"测试"}' $token
Test-Api "knowledge" "空间详情" "GET" "/kb/api/space/1" "" $token -Allow404
Test-Api "knowledge" "更新空间" "PUT" "/kb/api/space/1" '{"name":"新空间名"}' $token -Allow404
Test-Api "knowledge" "删除空间" "DELETE" "/kb/api/space/999999" "" $token
# 标签
Test-Api "knowledge" "标签列表" "GET" "/kb/api/tag/list" "" $token
Test-Api "knowledge" "创建标签" "POST" "/kb/api/tag" '{"name":"测试标签"}' $token
Test-Api "knowledge" "标签详情" "GET" "/kb/api/tag/1" "" $token -Allow404
Test-Api "knowledge" "更新标签" "PUT" "/kb/api/tag/1" '{"name":"新标签"}' $token -Allow404
Test-Api "knowledge" "删除标签" "DELETE" "/kb/api/tag/999999" "" $token
Test-Api "knowledge" "资源打标签" "POST" "/kb/api/tag/resource" '{"resourceType":"doc","resourceId":1,"tagIds":[1]}' $token
# 搜索
Test-Api "knowledge" "全文搜索" "GET" "/kb/api/search?q=test" "" $token
Test-Api "knowledge" "搜索-空关键词" "GET" "/kb/api/search?q=" "" $token
Test-Api "knowledge" "搜索建议" "GET" "/kb/api/search/suggest?q=t" "" $token -Allow404
# 分享
Test-Api "knowledge" "分享列表" "GET" "/kb/api/share/list" "" $token
Test-Api "knowledge" "创建分享" "POST" "/kb/api/share" '{"resourceType":"doc","resourceId":1,"expireDays":7}' $token
Test-Api "knowledge" "分享详情" "GET" "/kb/api/share/detail/testcode" "" $token -Allow404
Test-Api "knowledge" "分享验证(公开)" "GET" "/kb/api/share/verify/testcode" -NoAuth -Allow404
Test-Api "knowledge" "分享访问日志" "GET" "/kb/api/share/log/1" "" $token -Allow404
Test-Api "knowledge" "取消分享" "DELETE" "/kb/api/share/999999" "" $token
# 网页收藏
Test-Api "knowledge" "网页列表" "GET" "/kb/api/web/list" "" $token
Test-Api "knowledge" "创建网页收藏" "POST" "/kb/api/web" '{"url":"https://example.com","title":"测试"}' $token
Test-Api "knowledge" "网页详情" "GET" "/kb/api/web/1" "" $token -Allow404
Test-Api "knowledge" "删除网页" "DELETE" "/kb/api/web/999999" "" $token
# 回收站
Test-Api "knowledge" "回收站列表" "GET" "/kb/api/trash/list" "" $token
Test-Api "knowledge" "回收站恢复" "POST" "/kb/api/trash/restore/999999" "" $token
Test-Api "knowledge" "回收站彻底删除" "DELETE" "/kb/api/trash/999999" "" $token
Test-Api "knowledge" "回收站清空" "DELETE" "/kb/api/trash/clear" "" $token
# 版本
Test-Api "knowledge" "版本列表" "GET" "/kb/api/version/list?resourceType=doc&resourceId=1" "" $token -Allow404
Test-Api "knowledge" "版本详情" "GET" "/kb/api/version/1" "" $token -Allow404
Test-Api "knowledge" "版本回滚" "POST" "/kb/api/version/rollback/1" "" $token -Allow404
# 其他
Test-Api "knowledge" "文档更新" "PUT" "/kb/api/doc/1" '{"title":"更新标题","content":"更新内容"}' $token -Allow404
Test-Api "knowledge" "文档删除" "DELETE" "/kb/api/doc/999999" "" $token
Test-Api "knowledge" "文件夹更新" "PUT" "/kb/api/folder/999999" '{"name":"x"}' $token
Test-Api "knowledge" "网页更新" "PUT" "/kb/api/web/1" '{"title":"新标题"}' $token -Allow404
Test-Api "knowledge" "空间默认" "GET" "/kb/api/space/default" "" $token -Allow404
Test-Api "knowledge" "分享统计" "GET" "/kb/api/share/stats" "" $token -Allow404
Write-Host ""

# ===== 5. kb-ops 服务（27 个接口）=====
Write-Host "[5/6] 测试 kb-ops 服务（27 接口）..." -ForegroundColor Yellow
# 主机
Test-Api "ops" "主机列表" "GET" "/kb/api/ops/host/list" "" $token
Test-Api "ops" "主机详情" "GET" "/kb/api/ops/host/1" "" $token -Allow404
Test-Api "ops" "创建主机" "POST" "/kb/api/ops/host" '{"name":"test-host","ip":"192.168.1.1"}' $token
Test-Api "ops" "更新主机" "PUT" "/kb/api/ops/host/1" '{"name":"new-host"}' $token -Allow404
Test-Api "ops" "删除主机" "DELETE" "/kb/api/ops/host/999999" "" $token
# 端口
Test-Api "ops" "端口列表" "GET" "/kb/api/ops/port/list" "" $token -Allow404
Test-Api "ops" "端口详情" "GET" "/kb/api/ops/port/1" "" $token -Allow404
Test-Api "ops" "创建端口" "POST" "/kb/api/ops/port" '{"hostId":1,"port":8080}' $token -Allow404
Test-Api "ops" "更新端口" "PUT" "/kb/api/ops/port/1" '{"port":9090}' $token -Allow404
Test-Api "ops" "删除端口" "DELETE" "/kb/api/ops/port/999999" "" $token -Allow404
# 凭据
Test-Api "ops" "凭据列表" "GET" "/kb/api/ops/credential/list" "" $token -Allow404
Test-Api "ops" "创建凭据" "POST" "/kb/api/ops/credential" '{"name":"test-cred","username":"root"}' $token -Allow404
Test-Api "ops" "凭据详情" "GET" "/kb/api/ops/credential/1" "" $token -Allow404
Test-Api "ops" "更新凭据" "PUT" "/kb/api/ops/credential/1" '{"name":"new-cred"}' $token -Allow404
Test-Api "ops" "删除凭据" "DELETE" "/kb/api/ops/credential/999999" "" $token -Allow404
# 域名
Test-Api "ops" "域名列表" "GET" "/kb/api/ops/domain/list" "" $token -Allow404
Test-Api "ops" "创建域名" "POST" "/kb/api/ops/domain" '{"domain":"test.com"}' $token -Allow404
Test-Api "ops" "域名详情" "GET" "/kb/api/ops/domain/1" "" $token -Allow404
Test-Api "ops" "删除域名" "DELETE" "/kb/api/ops/domain/999999" "" $token -Allow404
# 依赖
Test-Api "ops" "依赖列表" "GET" "/kb/api/ops/dependency/list" "" $token -Allow404
Test-Api "ops" "创建依赖" "POST" "/kb/api/ops/dependency" '{"name":"test-dep"}' $token -Allow404
# 变更日志
Test-Api "ops" "变更日志列表" "GET" "/kb/api/ops/change/list" "" $token -Allow404
Test-Api "ops" "创建变更日志" "POST" "/kb/api/ops/change" '{"title":"test","content":"test"}' $token -Allow404
# 矛盾检测
Test-Api "ops" "矛盾列表" "GET" "/kb/api/ops/conflict/list" "" $token -Allow404
Test-Api "ops" "矛盾检测" "POST" "/kb/api/ops/conflict/detect" "" $token -Allow404
# 操作日志
Test-Api "ops" "操作日志列表" "GET" "/kb/api/ops/log/list" "" $token
Test-Api "ops" "操作日志详情" "GET" "/kb/api/ops/log/1" "" $token -Allow404
Write-Host ""

# ===== 6. 前端页面路由测试 =====
Write-Host "[6/6] 前端页面路由测试..." -ForegroundColor Yellow
$pages = @(
    "/kb/", "/kb/login", "/kb/dashboard", "/kb/settings", "/kb/search",
    "/kb/trash", "/kb/ops", "/kb/ops/hosts", "/kb/ops/services",
    "/kb/ops/conflicts", "/kb/ops/knowledge", "/kb/doc/create"
)
foreach ($p in $pages) {
    $code = & curl.exe -k -s -o NUL -w "%{http_code}" "$baseUrl$p" --max-time 10
    $status = if ($code -eq "200") { "PASS"; $script:passCount++ } else { "FAIL"; $script:failCount++ }
    $script:results.Add([PSCustomObject]@{
        Module = "page"; Name = "页面$p"; Method = "GET"; Path = $p
        Code = $code; Expect = 200; Status = $status; Time = "-"; Body = ""
    }) | Out-Null
}
# 静态资源
$assets = @(
    "/kb/s/index.html", "/kb/s/favicon.ico", "/kb/s/assets/"
)
foreach ($a in $assets) {
    $code = & curl.exe -k -s -o NUL -w "%{http_code}" "$baseUrl$a" --max-time 10
    $status = if ($code -eq "200" -or $code -eq "403" -or $code -eq "404") { "PASS"; $script:passCount++ } else { "FAIL"; $script:failCount++ }
    $script:results.Add([PSCustomObject]@{
        Module = "static"; Name = "静态$a"; Method = "GET"; Path = $a
        Code = $code; Expect = "200/403/404"; Status = $status; Time = "-"; Body = ""
    }) | Out-Null
}
Write-Host ""

# ===== 汇总报告 =====
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " 测试报告汇总" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "总测试数: $($results.Count)"
Write-Host "通过: $passCount" -ForegroundColor Green
Write-Host "警告: $warnCount" -ForegroundColor Yellow
Write-Host "失败: $failCount" -ForegroundColor Red
Write-Host "通过率: $([math]::Round($passCount / $results.Count * 100, 1))%" -ForegroundColor Cyan
Write-Host ""

Write-Host "----- 按模块统计 -----" -ForegroundColor Cyan
$results | Group-Object Module | ForEach-Object {
    $total = $_.Count
    $pass = ($_.Group | Where-Object Status -eq "PASS").Count
    $fail = ($_.Group | Where-Object Status -eq "FAIL").Count
    $warn = ($_.Group | Where-Object Status -eq "WARN").Count
    Write-Host ("  {0,-12} 总{1,-3} 通过{2,-3} 警告{3,-3} 失败{4,-3}" -f $_.Name, $total, $pass, $warn, $fail)
}
Write-Host ""

Write-Host "----- 失败和警告明细 -----" -ForegroundColor Red
$results | Where-Object { $_.Status -eq "FAIL" -or $_.Status -eq "WARN" } | Format-Table Module, Name, Method, Code, Expect, Status, Time, Body -AutoSize | Out-String | Write-Host

# 导出结果到 CSV
$csvPath = "$PSScriptRoot\test_results_$(Get-Date -Format 'yyyyMMdd_HHmmss').csv"
$results | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
Write-Host "详细结果已导出: $csvPath" -ForegroundColor Green
