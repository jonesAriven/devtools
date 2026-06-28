# 登录冒烟测试
$ErrorActionPreference = "Stop"
$body = @{username = "admin"; password = "admin123"} | ConvertTo-Json -Compress
try {
    $r = Invoke-RestMethod -Uri "http://100.93.36.113:8090/kb/api/auth/login" -Method POST -ContentType "application/json" -Body $body -TimeoutSec 30
    Write-Host ("登录成功: code={0} user={1}" -f $r.code, $r.data.user.username)
    Write-Host ("accessToken 前 60 字符: {0}" -f $r.data.accessToken.Substring(0, 60))
    $r | ConvertTo-Json -Depth 5 | Out-File "d:\huliang\java\ideaworkspace\devtools\mykng\test-output\e2e\smoke-login.json" -Encoding utf8
} catch {
    Write-Host ("登录失败: {0}" -f $_.Exception.Message)
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errBody = $reader.ReadToEnd()
        Write-Host "错误响应: $errBody"
    }
    exit 1
}
