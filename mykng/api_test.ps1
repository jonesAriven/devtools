# mykng API Full Functional Test v3 - inline, no function return issues
$BASE = 'http://192.168.31.105:8090'
$results = [System.Collections.ArrayList]::new()

function LogResult($name, $method, $path, $http, $code, $ok, $detail) {
    [void]$results.Add([PSCustomObject]@{
        Test=$name; M=$method; Path=$path; Http=$http; Code=$code
        Result=if($ok){'PASS'}else{'FAIL'}; Detail=$detail
    })
}

Write-Host "========================================"
Write-Host " mykng API Full Functional Test v3"
Write-Host " Target: $BASE"
Write-Host "========================================"
Write-Host ""

# ============ Login ============
Write-Host "[1] Auth Module"
$loginResp = Invoke-RestMethod -Uri "$BASE/kb/api/auth/login" -Method Post -Body '{"username":"admin","password":"admin123"}' -ContentType 'application/json' -TimeoutSec 20
$token = $loginResp.data.accessToken
$refresh = $loginResp.data.refreshToken
$loginOk = $loginResp.code -eq 200 -and $token
LogResult 'Login-Success' 'POST' '/kb/api/auth/login' 200 $loginResp.code $loginOk 'returns accessToken+refreshToken'

# Wrong password
try {
    $wp = Invoke-RestMethod -Uri "$BASE/kb/api/auth/login" -Method Post -Body '{"username":"admin","password":"wrong"}' -ContentType 'application/json' -TimeoutSec 20
    LogResult 'Login-WrongPwd' 'POST' '/kb/api/auth/login' 200 $wp.code ($wp.code -eq 500) 'wrong password rejected'
} catch { LogResult 'Login-WrongPwd' 'POST' '/kb/api/auth/login' 0 0 $false $_.Exception.Message }

# Refresh
try {
    $rf = Invoke-RestMethod -Uri "$BASE/kb/api/auth/refresh" -Method Post -Body "{`"refreshToken`":`"$refresh`"}" -ContentType 'application/json' -TimeoutSec 20
    LogResult 'Refresh-Token' 'POST' '/kb/api/auth/refresh' 200 $rf.code ($rf.code -eq 200) 'returns new accessToken'
} catch { LogResult 'Refresh-Token' 'POST' '/kb/api/auth/refresh' 0 0 $false $_.Exception.Message }

if (-not $token) {
    Write-Host "!!! Login failed !!!" -ForegroundColor Red
    $results | Format-Table -AutoSize
    exit 1
}

# Helper for authenticated GET requests
function GetApi($name, $path) {
    try {
        $r = Invoke-RestMethod -Uri "$BASE$path" -Method Get -Headers @{ 'Authorization' = "Bearer $token" } -TimeoutSec 20
        $ok = $r.code -eq 200
        LogResult $name 'GET' $path 200 $r.code $ok 'OK'
    } catch {
        $hc = 0; $bc = 0; $body = ''
        if ($_.Exception.Response) {
            try {
                $hc = [int]$_.Exception.Response.StatusCode
                $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $body = $sr.ReadToEnd()
                $j = $body | ConvertFrom-Json
                $bc = [int]$j.code
            } catch {}
        }
        LogResult $name 'GET' $path $hc $bc $false "body=$($body.Substring(0,[Math]::Min(80,$body.Length)))"
    }
}

Write-Host "[2] User Module";     GetApi 'GetUserProfile' '/kb/api/user/profile'
Write-Host "[3] Space Module";    GetApi 'ListSpaces' '/kb/api/space/list'
Write-Host "[4] Folder Module";   GetApi 'GetFolderTree' '/kb/api/folder/tree/1'
Write-Host "[5] Doc Module";      GetApi 'ListDocs' '/kb/api/doc/list?page=1&size=10'
Write-Host "[6] Tag Module";      GetApi 'ListTags' '/kb/api/tag/list'
Write-Host "[7] Search Module";  GetApi 'SearchDocs' '/kb/api/search?q=test'
Write-Host "[8] Share Module";    GetApi 'ShareVerify-WList' '/kb/api/share/verify/nonexistent'
Write-Host "[9] Version Module";  GetApi 'ListVersions' '/kb/api/version/list/doc/1'
Write-Host "[10] Trash Module";   GetApi 'ListTrash' '/kb/api/trash/list?page=1&size=10'
Write-Host "[11] File Module";    GetApi 'ListBuckets' '/kb/api/bucket/list'
Write-Host "[12] Ops Module"
GetApi 'ListHosts' '/kb/api/ops/host/list?page=1&size=10'
GetApi 'ListServices' '/kb/api/ops/service/list?page=1&size=10'
GetApi 'Dashboard' '/kb/api/ops/dashboard'
GetApi 'ListLogs' '/kb/api/log/list?page=1&size=10'

# ============ Security Tests ============
Write-Host "[Security] JWT"
# No token -> 401
try {
    $r = Invoke-RestMethod -Uri "$BASE/kb/api/user/profile" -Method Get -TimeoutSec 20
    LogResult 'NoToken-401' 'GET' '/kb/api/user/profile' 200 $r.code $false 'should be 401'
} catch {
    $hc = 0; $bc = 0
    if ($_.Exception.Response) { try { $hc = [int]$_.Exception.Response.StatusCode; $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); $j = $sr.ReadToEnd() | ConvertFrom-Json; $bc = $j.code } catch {} }
    LogResult 'NoToken-401' 'GET' '/kb/api/user/profile' $hc $bc ($hc -eq 401 -or $bc -eq 401) 'no token 401'
}
# Invalid token -> 401
try {
    $r = Invoke-RestMethod -Uri "$BASE/kb/api/user/profile" -Method Get -Headers @{ 'Authorization' = 'Bearer invalid.token.here' } -TimeoutSec 20
    LogResult 'InvalidToken-401' 'GET' '/kb/api/user/profile' 200 $r.code $false 'should be 401'
} catch {
    $hc = 0; $bc = 0
    if ($_.Exception.Response) { try { $hc = [int]$_.Exception.Response.StatusCode; $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); $j = $sr.ReadToEnd() | ConvertFrom-Json; $bc = $j.code } catch {} }
    LogResult 'InvalidToken-401' 'GET' '/kb/api/user/profile' $hc $bc ($hc -eq 401 -or $bc -eq 401) 'invalid token 401'
}
# Refresh token -> 401
try {
    $r = Invoke-RestMethod -Uri "$BASE/kb/api/user/profile" -Method Get -Headers @{ 'Authorization' = "Bearer $refresh" } -TimeoutSec 20
    LogResult 'RefreshToken-401' 'GET' '/kb/api/user/profile' 200 $r.code $false 'should be 401'
} catch {
    $hc = 0; $bc = 0
    if ($_.Exception.Response) { try { $hc = [int]$_.Exception.Response.StatusCode; $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); $j = $sr.ReadToEnd() | ConvertFrom-Json; $bc = $j.code } catch {} }
    LogResult 'RefreshToken-401' 'GET' '/kb/api/user/profile' $hc $bc ($hc -eq 401 -or $bc -eq 401) 'refresh token 401'
}

# ============ CORS Test ============
Write-Host "[CORS]"
try {
    $preflight = Invoke-WebRequest -Uri "$BASE/kb/api/auth/login" -Method Options -Headers @{ 'Origin'='http://example.com'; 'Access-Control-Request-Method'='POST' } -TimeoutSec 10 -UseBasicParsing
    $corsOk = $preflight.Headers['Access-Control-Allow-Origin'] -ne $null
    LogResult 'CORS-Preflight' 'OPTIONS' '/kb/api/auth/login' $preflight.StatusCode 0 $corsOk 'CORS header present'
} catch {
    LogResult 'CORS-Preflight' 'OPTIONS' '/kb/api/auth/login' 0 0 $false $_.Exception.Message
}

# ============ Logout ============
Write-Host "[Logout]"
try {
    $lo = Invoke-RestMethod -Uri "$BASE/kb/api/auth/logout" -Method Post -Headers @{ 'Authorization' = "Bearer $token" } -TimeoutSec 20
    LogResult 'Logout' 'POST' '/kb/api/auth/logout' 200 $lo.code ($lo.code -eq 200) 'token blacklisted'
} catch {
    $hc = 0; $bc = 0
    if ($_.Exception.Response) { try { $hc = [int]$_.Exception.Response.StatusCode; $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); $j = $sr.ReadToEnd() | ConvertFrom-Json; $bc = $j.code } catch {} }
    LogResult 'Logout' 'POST' '/kb/api/auth/logout' $hc $bc $false "http=$hc"
}

# ============ Summary ============
Write-Host ""
Write-Host "========================================"
Write-Host " Test Results Summary"
Write-Host "========================================"
$results | Format-Table @{L='Test';E={$_.Test};W=20},@{L='M';E={$_.M};W=6},@{L='Path';E={$_.Path};W=40},@{L='Http';E={$_.Http};W=5},@{L='Code';E={$_.Code};W=5},@{L='Result';E={$_.Result};W=6},@{L='Detail';E={$_.Detail}} -AutoSize

$pass = ($results | Where-Object { $_.Result -eq 'PASS' }).Count
$fail = ($results | Where-Object { $_.Result -eq 'FAIL' }).Count
Write-Host ""
Write-Host "Total: $($results.Count) | PASS: $pass | FAIL: $fail"
Write-Host "========================================"
