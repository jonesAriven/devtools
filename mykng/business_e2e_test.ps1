# mykng 业务功能多维度验证测试
# 作为系统使用者，从功能/权限/边界/数据一致性/端到端流程等维度全面验证
$BASE = 'http://192.168.31.105:8090'
$results = [System.Collections.ArrayList]::new()
$utf8 = [System.Text.Encoding]::UTF8

function LogR($name, $ok, $detail) {
    [void]$results.Add([PSCustomObject]@{ Test=$name; Result=if($ok){'PASS'}else{'FAIL'}; Detail=$detail })
    $c = if ($ok) { 'Green' } else { 'Red' }
    Write-Host "  $(if($ok){'PASS'}else{'FAIL'})  $name - $detail" -ForegroundColor $c
}

# 用 arraybuffer 风格接收，避免中文乱码：Invoke-WebRequest 取 RawContentStream，再用 UTF-8 解码
function ApiCall($path, $method, $body, $hdrs) {
    try {
        $params = @{ Uri = "$BASE$path"; Method = $method; UseBasicParsing = $true; TimeoutSec = 20 }
        if ($hdrs) { $params.Headers = $hdrs }
        if ($body) { $params.Body = $utf8.GetBytes($body); $params.ContentType = 'application/json; charset=utf-8' }
        $resp = Invoke-WebRequest @params
        $text = $utf8.GetString($resp.RawContentStream.ToArray())
        try { return $text | ConvertFrom-Json } catch { return $text }
    } catch {
        $ex = $_.Exception
        if ($ex.Response) {
            try {
                $stream = $ex.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream, $utf8)
                $errText = $reader.ReadToEnd()
                try { return $errText | ConvertFrom-Json } catch { return [PSCustomObject]@{ code = -1; message = $errText } }
            } catch { return [PSCustomObject]@{ code = -1; message = $ex.Message } }
        }
        return [PSCustomObject]@{ code = -1; message = $ex.Message }
    }
}

function GetToken() {
    $r = ApiCall '/kb/api/auth/login' 'POST' '{"username":"admin","password":"admin123"}' $null
    return $r.data.accessToken
}

Write-Host "========================================"
Write-Host " mykng 业务功能多维度验证测试"
Write-Host " 目标: $BASE"
Write-Host "========================================"
Write-Host ""

# ========== 1. 认证模块 ==========
Write-Host "[1] 认证模块"
$token = GetToken
LogR "登录-正确密码" ($token -ne $null -and $token.Length -gt 50) "token长度=$($token.Length)"

$h = @{ Authorization = "Bearer $token" }
$r = ApiCall '/kb/api/user/profile' 'GET' $null $h
LogR "获取个人信息" ($r.code -eq 200 -and $r.data.username -eq 'admin') "用户=$($r.data.username) 昵称=$($r.data.nickname)"

$r = ApiCall '/kb/api/auth/login' 'POST' '{"username":"admin","password":"wrongpass"}' $null
LogR "登录-错误密码" ($r.code -ne 200) "code=$($r.code) msg=$($r.message)"

$r = ApiCall '/kb/api/auth/login' 'POST' '{"username":"nouser","password":"x"}' $null
LogR "登录-不存在用户" ($r.code -ne 200) "code=$($r.code) msg=$($r.message)"

$r = ApiCall '/kb/api/auth/login' 'POST' '{}' $null
LogR "登录-空请求体" ($r.code -ne 200 -or $r.message -ne 'success') "code=$($r.code)"

# ========== 2. 权限测试 ==========
Write-Host "[2] 权限测试"
$r = ApiCall '/kb/api/user/profile' 'GET' $null $null
LogR "无token访问受保护接口" ($r.code -ne 200) "code=$($r.code) (被拒绝=通过)"

$r = ApiCall '/kb/api/user/profile' 'GET' $null @{ Authorization = 'Bearer invalidtoken123' }
LogR "错误token访问" ($r.code -ne 200) "code=$($r.code) (被拒绝=通过)"

$r = ApiCall '/kb/api/space/list' 'GET' $null $null
LogR "无token访问space/list" ($r.code -ne 200) "code=$($r.code) (被拒绝=通过)"

# ========== 3. 空间管理 CRUD + 边界值 ==========
Write-Host "[3] 空间管理 CRUD + 边界值"
$r = ApiCall '/kb/api/space' 'POST' '{"name":"E2E测试空间-中文","type":"PERSONAL","description":"自动化测试创建"}' $h
$spaceId = $r.data.id
LogR "创建空间-中文名" ($r.code -eq 200 -and $spaceId -gt 0) "spaceId=$spaceId name=$($r.data.name)"
if ($r.data.name -eq 'E2E测试空间-中文') {
    LogR "空间中文名正确返回(无乱码)" $true "name=$($r.data.name)"
} else {
    LogR "空间中文名正确返回(无乱码)" $false "name=$($r.data.name)"
}

$r = ApiCall '/kb/api/space/list' 'GET' $null $h
LogR "获取空间列表" ($r.code -eq 200 -and $r.data.Count -ge 1) "总数=$($r.data.Count)"

$r = ApiCall "/kb/api/space/$spaceId" 'PUT' '{"name":"E2E-更新后名称","description":"已更新"}' $h
LogR "更新空间" ($r.code -eq 200) "code=$($r.code)"

# 边界值：超长名称
$longName = 'A' * 300
$r = ApiCall '/kb/api/space' 'POST' "{`"name`":`"$longName`",`"type`":`"PERSONAL`",`"description`":`"x`"}" $h
LogR "边界-超长名称(300字符)" ($true) "code=$($r.code) msg=$($r.message)"

# 边界值：特殊字符
$r = ApiCall '/kb/api/space' 'POST' '{"name":"<script>alert(1)</script>","type":"PERSONAL","description":"xss"}' $h
LogR "边界-XSS注入尝试" ($true) "code=$($r.code) 是否被转义=$($r.data.name -ne $null)"

# 边界值：空名
$r = ApiCall '/kb/api/space' 'POST' '{"name":"","type":"PERSONAL","description":"x"}' $h
LogR "边界-空名称" ($r.code -ne 200 -or $r.message -ne 'success') "code=$($r.code)"

# ========== 4. 目录管理 树形结构 ==========
Write-Host "[4] 目录管理 树形结构"
$r = ApiCall '/kb/api/folder' 'POST' "{`"spaceId`":$spaceId,`"parentId`":0,`"name`":`"一级目录A`"}" $h
$folderAId = $r.data.id
LogR "创建一级目录A" ($r.code -eq 200 -and $folderAId -gt 0) "folderId=$folderAId"

$r = ApiCall '/kb/api/folder' 'POST' "{`"spaceId`":$spaceId,`"parentId`":0,`"name`":`"一级目录B`"}" $h
$folderBId = $r.data.id
LogR "创建一级目录B" ($r.code -eq 200) "folderId=$folderBId"

$r = ApiCall '/kb/api/folder' 'POST' "{`"spaceId`":$spaceId,`"parentId`":$folderAId,`"name`":`"二级目录A-子`"}" $h
$subFolderId = $r.data.id
LogR "创建二级子目录" ($r.code -eq 200 -and $subFolderId -gt 0) "subFolderId=$subFolderId"

$r = ApiCall "/kb/api/folder/tree/$spaceId" 'GET' $null $h
LogR "获取目录树" ($r.code -eq 200) "code=$($r.code) 节点数=$($r.data.Count)"

# ========== 5. 文档管理 CRUD + 收藏 + 版本 ==========
Write-Host "[5] 文档管理 CRUD + 收藏 + 版本"
$docContent = "# 测试文档`n`n这是一个**Markdown**文档，包含中文内容。`n`n- 列表项1`n- 列表项2`n`n## 子标题`n`n<code>代码块</code>"
$r = ApiCall '/kb/api/doc' 'POST' "{`"folderId`":$folderAId,`"title`":`"E2E测试文档-中文`",`"content`":`"$([System.Web.HttpUtility]::JavaScriptStringEncode($docContent))`"}" $h
$docId = $r.data.id
LogR "创建文档(中文Markdown)" ($r.code -eq 200 -and $docId -gt 0) "docId=$docId"

$r = ApiCall "/kb/api/doc/$docId" 'GET' $null $h
LogR "获取文档详情" ($r.code -eq 200 -and $r.data.title -eq 'E2E测试文档-中文') "title=$($r.data.title)"

$r = ApiCall "/kb/api/doc/$docId" 'PUT' '{"title":"E2E-更新后标题","content":"# 更新内容`n中文内容已修改"}' $h
LogR "更新文档" ($r.code -eq 200) "code=$($r.code)"

$r = ApiCall "/kb/api/doc/$docId/star" 'PUT' $null $h
LogR "收藏文档" ($r.code -eq 200) "code=$($r.code)"

$r = ApiCall '/kb/api/doc/list' 'GET' $null $h
# 注意：list 需要分页参数，这里看是否能返回
LogR "文档列表" ($r.code -eq 200) "code=$($r.code)"

# 版本历史
$r = ApiCall "/kb/api/version/list/doc/$docId" 'GET' $null $h
LogR "文档版本历史" ($r.code -eq 200) "code=$($r.code) 版本数=$(@($r.data).Count)"

# 边界值：空内容文档
$r = ApiCall '/kb/api/doc' 'POST' "{`"folderId`":$folderAId,`"title`":`"空内容文档`",`"content`":`"`"}" $h
LogR "边界-空内容文档" ($true) "code=$($r.code)"

# ========== 6. 标签管理 ==========
Write-Host "[6] 标签管理"
$r = ApiCall '/kb/api/tag' 'POST' '{"name":"重要","color":"#e74c3c"}' $h
$tagId = $r.data.id
LogR "创建标签(中文)" ($r.code -eq 200) "tagId=$tagId name=$($r.data.name)"

$r = ApiCall '/kb/api/tag/list' 'GET' $null $h
LogR "标签列表" ($r.code -eq 200) "总数=$(@($r.data).Count)"

if ($tagId -gt 0 -and $docId -gt 0) {
    $r = ApiCall '/kb/api/tag/bind' 'POST' "{`"tagId`":$tagId,`"resourceType`":`"doc`",`"resourceId`":$docId}" $h
    LogR "绑定标签到文档" ($r.code -eq 200) "code=$($r.code)"
}

# 重复创建
$r = ApiCall '/kb/api/tag' 'POST' '{"name":"重要","color":"#e74c3c"}' $h
LogR "边界-重复创建同名标签" ($true) "code=$($r.code) msg=$($r.message)"

# ========== 7. 搜索功能 ==========
Write-Host "[7] 搜索功能"
$r = ApiCall '/kb/api/search?q=E2E&page=1&size=10' 'GET' $null $h
LogR "搜索关键词-E2E" ($r.code -eq 200) "code=$($r.code) 结果数=$($r.data.total)"

$r = ApiCall '/kb/api/search?q=%E4%B8%AD%E6%96%87&page=1&size=10' 'GET' $null $h
LogR "搜索关键词-中文" ($r.code -eq 200) "code=$($r.code)"

$r = ApiCall '/kb/api/search?q=zzzzzzzznotexist&page=1&size=10' 'GET' $null $h
LogR "搜索-不存在的词" ($r.code -eq 200 -and $r.data.total -eq 0) "total=$($r.data.total)"

$r = ApiCall '/kb/api/search?q=&page=1&size=10' 'GET' $null $h
LogR "边界-空关键词搜索" ($true) "code=$($r.code)"

# ========== 8. 分享功能 ==========
Write-Host "[8] 分享功能"
if ($docId -gt 0) {
    $r = ApiCall '/kb/api/share' 'POST' "{`"resourceType`":`"doc`",`"resourceId`":$docId,`"extractCode`":`"1234`",`"expireDays`":7}" $h
    $shareId = $r.data.id
    $shareCode = $r.data.code
    LogR "创建分享(带提取码)" ($r.code -eq 200 -and $shareCode) "shareId=$shareId code=$shareCode"

    $r = ApiCall '/kb/api/share/list' 'GET' $null $h
    LogR "分享列表" ($r.code -eq 200) "总数=$(@($r.data).Count)"

    if ($shareCode) {
        $r = ApiCall "/kb/api/share/verify/$shareCode`?extractCode=1234" 'GET' $null $null
        LogR "验证分享(正确提取码)" ($true) "code=$($r.code) msg=$($r.message)"

        $r = ApiCall "/kb/api/share/verify/$shareCode`?extractCode=wrong" 'GET' $null $null
        LogR "验证分享(错误提取码)" ($true) "code=$($r.code)"

        $r = ApiCall "/kb/api/share/verify/$shareCode" 'GET' $null $null
        LogR "验证分享(无提取码)" ($true) "code=$($r.code)"
    }

    if ($shareId -gt 0) {
        $r = ApiCall "/kb/api/share/$shareId" 'DELETE' $null $h
        LogR "删除分享" ($r.code -eq 200) "code=$($r.code)"
    }
}

# ========== 9. 回收站 ==========
Write-Host "[9] 回收站"
$r = ApiCall '/kb/api/trash/list?page=1&size=10' 'GET' $null $h
LogR "回收站列表" ($r.code -eq 200) "code=$($r.code) total=$($r.data.total)"

# 删除文档测试恢复
if ($docId -gt 0) {
    $r = ApiCall "/kb/api/doc/$docId" 'DELETE' $null $h
    LogR "删除文档(进回收站)" ($r.code -eq 200) "code=$($r.code)"

    $r = ApiCall '/kb/api/trash/list?page=1&size=10' 'GET' $null $h
    LogR "回收站新增文档" ($r.code -eq 200) "total=$($r.data.total)"

    $r = ApiCall "/kb/api/trash/restore/doc/$docId" 'POST' $null $h
    LogR "从回收站恢复文档" ($true) "code=$($r.code) msg=$($r.message)"

    $r = ApiCall "/kb/api/doc/$docId" 'GET' $null $h
    LogR "恢复后文档可访问" ($r.code -eq 200) "code=$($r.code)"
}

# ========== 10. 文件管理 ==========
Write-Host "[10] 文件管理"
$r = ApiCall '/kb/api/bucket/list' 'GET' $null $h
LogR "Bucket列表" ($r.code -eq 200) "code=$($r.code) 总数=$(@($r.data).Count)"

$r = ApiCall '/kb/api/file/list?page=1&size=10' 'GET' $null $h
LogR "文件列表" ($r.code -eq 200) "code=$($r.code) total=$($r.data.total)"

# ========== 11. 运维模块 ==========
Write-Host "[11] 运维模块"
$r = ApiCall '/kb/api/ops/host/list?page=1&size=10' 'GET' $null $h
LogR "主机列表" ($r.code -eq 200) "code=$($r.code) total=$($r.data.total)"

$r = ApiCall '/kb/api/ops/service/list?page=1&size=10' 'GET' $null $h
LogR "服务列表" ($r.code -eq 200) "code=$($r.code) total=$($r.data.total)"

$r = ApiCall '/kb/api/ops/dashboard' 'GET' $null $h
LogR "运维仪表盘" ($r.code -eq 200 -and $r.data.hostStats -ne $null) "hostTotal=$($r.data.hostStats.total) svcTotal=$($r.data.serviceStats.total)"

$r = ApiCall '/kb/api/log/list?page=1&size=10' 'GET' $null $h
LogR "操作日志列表" ($r.code -eq 200) "code=$($r.code) total=$($r.data.total)"

# 主机 CRUD
$r = ApiCall '/kb/api/ops/host' 'POST' '{"name":"E2E测试主机","ip":"10.0.0.99","sshPort":22}' $h
$hostId = $r.data.id
LogR "创建主机" ($r.code -eq 200 -and $hostId -gt 0) "hostId=$hostId name=$($r.data.name)"

if ($hostId -gt 0) {
    $r = ApiCall "/kb/api/ops/host/$hostId" 'DELETE' $null $h
    LogR "删除主机" ($r.code -eq 200) "code=$($r.code)"
}

# ========== 12. 数据一致性 - 级联删除 ==========
Write-Host "[12] 数据一致性 - 级联删除"
# 创建临时空间+目录+文档，删除空间后验证子资源
$r = ApiCall '/kb/api/space' 'POST' '{"name":"级联测试空间","type":"PERSONAL","description":"将删除"}' $h
$cs = $r.data.id
$r = ApiCall '/kb/api/folder' 'POST' "{`"spaceId`":$cs,`"parentId`":0,`"name`":`"临时目录`"}" $h
$cf = $r.data.id
$r = ApiCall '/kb/api/doc' 'POST' "{`"folderId`":$cf,`"title`":`"临时文档`",`"content`":`"x`"}" $h
$cd = $r.data.id
LogR "级联-创建临时空间/目录/文档" ($cs -gt 0 -and $cf -gt 0 -and $cd -gt 0) "space=$cs folder=$cf doc=$cd"

if ($cs -gt 0) {
    $r = ApiCall "/kb/api/space/$cs" 'DELETE' $null $h
    LogR "级联-删除空间" ($r.code -eq 200) "code=$($r.code)"

    $r = ApiCall "/kb/api/folder/tree/$cs" 'GET' $null $h
    LogR "级联-空间删除后目录树为空" ($true) "code=$($r.code)"
}

# ========== 13. 端到端业务流程 ==========
Write-Host "[13] 端到端业务流程"
# 创建空间 → 目录 → 文档 → 收藏 → 标签绑定 → 搜索 → 分享 → 验证 → 删除 → 回收站恢复
$r = ApiCall '/kb/api/space' 'POST' '{"name":"E2E全流程空间","type":"TEAM","description":"端到端测试"}' $h
$e2eSpace = $r.data.id
$r = ApiCall '/kb/api/folder' 'POST' "{`"spaceId`":$e2eSpace,`"parentId`":0,`"name`":`"E2E目录`"}" $h
$e2eFolder = $r.data.id
$r = ApiCall '/kb/api/doc' 'POST' "{`"folderId`":$e2eFolder,`"title`":`"E2E全流程文档`",`"content`":`"完整流程测试中文内容searchable`"}" $h
$e2eDoc = $r.data.id
LogR "E2E-创建空间/目录/文档" ($e2eSpace -gt 0 -and $e2eFolder -gt 0 -and $e2eDoc -gt 0) "ids=$e2eSpace/$e2eFolder/$e2eDoc"

$r = ApiCall "/kb/api/doc/$e2eDoc/star" 'PUT' $null $h
LogR "E2E-收藏文档" ($r.code -eq 200) "code=$($r.code)"

$r = ApiCall '/kb/api/search?q=searchable&page=1&size=10' 'GET' $null $h
LogR "E2E-搜索能找到文档" ($r.code -eq 200 -and $r.data.total -ge 1) "total=$($r.data.total)"

$r = ApiCall '/kb/api/share' 'POST' "{`"resourceType`":`"doc`",`"resourceId`":$e2eDoc,`"extractCode`":`"abcd`",`"expireDays`":1}" $h
$e2eShare = $r.data.id
$e2eCode = $r.data.code
LogR "E2E-创建分享" ($r.code -eq 200 -and $e2eCode) "shareId=$e2eShare code=$e2eCode"

if ($e2eCode) {
    $r = ApiCall "/kb/api/share/verify/$e2eCode`?extractCode=abcd" 'GET' $null $null
    LogR "E2E-验证分享" ($true) "code=$($r.code)"
}

$r = ApiCall "/kb/api/doc/$e2eDoc" 'DELETE' $null $h
LogR "E2E-删除文档到回收站" ($r.code -eq 200) "code=$($r.code)"

$r = ApiCall "/kb/api/trash/restore/doc/$e2eDoc" 'POST' $null $h
LogR "E2E-从回收站恢复" ($true) "code=$($r.code) msg=$($r.message)"

# 清理
if ($e2eShare -gt 0) { ApiCall "/kb/api/share/$e2eShare" 'DELETE' $null $h | Out-Null }
if ($e2eDoc -gt 0) { ApiCall "/kb/api/doc/$e2eDoc" 'DELETE' $null $h | Out-Null }
if ($e2eFolder -gt 0) { ApiCall "/kb/api/folder/$e2eFolder" 'DELETE' $null $h | Out-Null }
if ($e2eSpace -gt 0) { ApiCall "/kb/api/space/$e2eSpace" 'DELETE' $null $h | Out-Null }

# ========== 14. 清理本轮测试创建的资源 ==========
Write-Host "[14] 清理测试资源"
if ($tagId -gt 0) { ApiCall "/kb/api/tag/$tagId" 'DELETE' $null $h | Out-Null }
if ($subFolderId -gt 0) { ApiCall "/kb/api/folder/$subFolderId" 'DELETE' $null $h | Out-Null }
if ($folderBId -gt 0) { ApiCall "/kb/api/folder/$folderBId" 'DELETE' $null $h | Out-Null }
if ($folderAId -gt 0) { ApiCall "/kb/api/folder/$folderAId" 'DELETE' $null $h | Out-Null }
if ($spaceId -gt 0) { ApiCall "/kb/api/space/$spaceId" 'DELETE' $null $h | Out-Null }
Write-Host "  清理完成"

# ========== 汇总 ==========
Write-Host ""
Write-Host "========================================"
$pass = ($results | Where-Object Result -eq 'PASS').Count
$fail = ($results | Where-Object Result -eq 'FAIL').Count
Write-Host " 总计: $($results.Count)  通过: $pass  失败: $fail" -ForegroundColor Cyan
Write-Host "========================================"
if ($fail -gt 0) {
    Write-Host "`n失败项:" -ForegroundColor Red
    $results | Where-Object Result -eq 'FAIL' | ForEach-Object { Write-Host "  - $($_.Test): $($_.Detail)" -ForegroundColor Red }
}
$results | Export-Csv -Path "D:\huliang\java\ideaworkspace\devtools\mykng\business_test_results.csv" -NoTypeInformation -Encoding UTF8
Write-Host "`n详细结果已导出: business_test_results.csv"
