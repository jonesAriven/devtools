# mykng Comprehensive E2E Test - All Modules
# Tests every API endpoint + full CRUD + edge cases + security
$BASE = 'http://192.168.31.105:8090'
$results = [System.Collections.ArrayList]::new()

function LogR($name, $ok, $detail) {
    [void]$results.Add([PSCustomObject]@{ Test=$name; Result=if($ok){'PASS'}else{'FAIL'}; Detail=$detail })
    if ($ok) { Write-Host "  PASS  $name - $detail" -ForegroundColor Green }
    else { Write-Host "  FAIL  $name - $detail" -ForegroundColor Red }
}

function ApiGet($path, $hdrs) {
    try { return Invoke-RestMethod -Uri "$BASE$path" -Method Get -Headers $hdrs -TimeoutSec 15 }
    catch { return $null }
}

function ApiPost($path, $body, $hdrs) {
    try { return Invoke-RestMethod -Uri "$BASE$path" -Method Post -Headers $hdrs -Body $body -ContentType 'application/json' -TimeoutSec 15 }
    catch { return $null }
}

function ApiPut($path, $body, $hdrs) {
    try { return Invoke-RestMethod -Uri "$BASE$path" -Method Put -Headers $hdrs -Body $body -ContentType 'application/json' -TimeoutSec 15 }
    catch { return $null }
}

function ApiDelete($path, $hdrs) {
    try { return Invoke-RestMethod -Uri "$BASE$path" -Method Delete -Headers $hdrs -TimeoutSec 15 }
    catch { return $null }
}

function ExpectStatus($path, $method, $hdrs, $expectedCode) {
    try {
        if ($method -eq 'GET') { $r = Invoke-RestMethod -Uri "$BASE$path" -Method Get -Headers $hdrs -TimeoutSec 15 }
        elseif ($method -eq 'POST') { $r = Invoke-RestMethod -Uri "$BASE$path" -Method Post -Headers $hdrs -ContentType 'application/json' -TimeoutSec 15 }
        return ($r.code -eq $expectedCode)
    } catch {
        $sc = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        return ($sc -eq $expectedCode)
    }
}

$h = @{}
Write-Host "========================================"
Write-Host " mykng Comprehensive E2E Test"
Write-Host " Target: $BASE"
Write-Host "========================================"
Write-Host ""

# ========== 1. AUTH ==========
Write-Host "[1] Auth Module"
$login = Invoke-RestMethod -Uri "$BASE/kb/api/auth/login" -Method Post -Body '{"username":"admin","password":"admin123"}' -ContentType 'application/json' -TimeoutSec 15
$token = $login.data.accessToken
$refresh = $login.data.refreshToken
$h = @{ 'Authorization' = "Bearer $token" }
LogR 'Auth-Login' ($login.code -eq 200 -and $token) "code=$($login.code) token=$([bool]$token)"

# Wrong password
try { $wp = Invoke-RestMethod -Uri "$BASE/kb/api/auth/login" -Method Post -Body '{"username":"admin","password":"wrong"}' -ContentType 'application/json' -TimeoutSec 15; LogR 'Auth-WrongPwd' ($wp.code -ne 200) "code=$($wp.code) rejected" }
catch { LogR 'Auth-WrongPwd' $true "HTTP error (rejected)" }

# Refresh
$rf = ApiPost '/kb/api/auth/refresh' "{`"refreshToken`":`"$refresh`"}" $h
LogR 'Auth-Refresh' ($rf -and $rf.code -eq 200) "code=$(if($rf){$rf.code}else{'null'})"

# Profile
$profile = ApiGet '/kb/api/user/profile' $h
LogR 'Auth-Profile' ($profile -and $profile.code -eq 200 -and $profile.data.username) "user=$($profile.data.username)"

# ========== 2. SPACE CRUD ==========
Write-Host ""; Write-Host "[2] Space Module (Full CRUD)"
$spBefore = ApiGet '/kb/api/space/list' $h
LogR 'Space-List' ($spBefore.code -eq 200) "count=$($spBefore.data.Count)"

$sc = ApiPost '/kb/api/space' '{"name":"e2e-test-space","type":"personal","description":"E2E test"}' $h
$spaceId = $sc.data.id
LogR 'Space-Create' ($sc.code -eq 200 -and $spaceId) "id=$spaceId"

$su = ApiPut "/kb/api/space/$spaceId" '{"name":"e2e-updated-space","description":"Updated desc"}' $h
LogR 'Space-Update' ($su.code -eq 200 -and $su.data.name -eq 'e2e-updated-space') "name=$($su.data.name)"

# List and verify update
$spAfter = ApiGet '/kb/api/space/list' $h
$found = $spAfter.data | Where-Object { $_.id -eq $spaceId }
LogR 'Space-VerifyUpdate' ($found -and $found.name -eq 'e2e-updated-space') "updated name confirmed"

# Get non-existent space
$ne = ApiGet '/kb/api/space/999999' $h
LogR 'Space-NotExist' ($ne -and $ne.code -ne 200) "code=$($ne.code) (rejected)"

# ========== 3. FOLDER CRUD ==========
Write-Host ""; Write-Host "[3] Folder Module (Full CRUD)"
$fc = ApiPost '/kb/api/folder' "{`"spaceId`":$spaceId,`"parentId`":0,`"name`":`"e2e-folder`"}" $h
$folderId = $fc.data.id
LogR 'Folder-Create' ($fc.code -eq 200 -and $folderId) "id=$folderId"

# Create subfolder (nested)
$sfc = ApiPost '/kb/api/folder' "{`"spaceId`":$spaceId,`"parentId`":$folderId,`"name`":`"e2e-subfolder`"}" $h
$subFolderId = if ($sfc) { $sfc.data.id } else { 0 }
LogR 'Folder-CreateNested' ($sfc -and $sfc.code -eq 200 -and $subFolderId) "subId=$subFolderId"

$tree = ApiGet "/kb/api/folder/tree/$spaceId" $h
$treeFound = if ($tree) { $tree.data | Where-Object { $_.id -eq $folderId } } else { $null }
LogR 'Folder-Tree' ($tree -and $tree.code -eq 200) "tree has folder=$([bool]$treeFound)"

# Delete subfolder
if ($subFolderId) {
    $dsf = ApiDelete "/kb/api/folder/$subFolderId" $h
    LogR 'Folder-DeleteSub' ($dsf.code -eq 200) "subfolder deleted"
}

# ========== 4. DOC CRUD ==========
Write-Host ""; Write-Host "[4] Doc Module (Full CRUD)"
$dc = ApiPost '/kb/api/doc' "{`"folderId`":$folderId,`"title`":`"e2e-doc-1`",`"content`":`"# Test Doc\nHello World`"}" $h
$docId = $dc.data.id
LogR 'Doc-Create' ($dc.code -eq 200 -and $docId) "id=$docId"

# Create second doc for pagination test
$dc2 = ApiPost '/kb/api/doc' "{`"folderId`":$folderId,`"title`":`"e2e-doc-2`",`"content`":`"Second doc content`"}" $h
$docId2 = if ($dc2) { $dc2.data.id } else { 0 }
LogR 'Doc-Create2' ($dc2 -and $dc2.code -eq 200) "id2=$docId2"

$dg = ApiGet "/kb/api/doc/$docId" $h
LogR 'Doc-Get' ($dg.code -eq 200 -and $dg.data.title -eq 'e2e-doc-1') "title=$($dg.data.title)"

# Doc list with pagination
$dl = ApiGet '/kb/api/doc/list?page=1&size=10' $h
LogR 'Doc-List' ($dl.code -eq 200) "total=$($dl.data.total)"

# Doc list page 2
$dl2 = ApiGet '/kb/api/doc/list?page=2&size=5' $h
LogR 'Doc-ListPage2' ($dl2.code -eq 200) "page2 total=$($dl2.data.total)"

# Update doc
$du = ApiPut "/kb/api/doc/$docId" '{"title":"e2e-updated-doc","content":"Updated content with more text"}' $h
LogR 'Doc-Update' ($du.code -eq 200 -and $du.data.title -eq 'e2e-updated-doc') "title=$($du.data.title)"

# Star doc
$st = ApiPut "/kb/api/doc/$docId/star" '{}' $h
LogR 'Doc-Star' ($st.code -eq 200) "starred"

# Unstar (toggle)
$ust = ApiPut "/kb/api/doc/$docId/star" '{}' $h
LogR 'Doc-Unstar' ($ust.code -eq 200) "unstarred (toggle)"

# Version list
$vl = ApiGet "/kb/api/version/list/doc/$docId" $h
LogR 'Doc-VersionList' ($vl.code -eq 200) "versions=$($vl.data.Count)"

# Get non-existent doc
$dne = ApiGet '/kb/api/doc/999999' $h
LogR 'Doc-NotExist' ($dne -and $dne.code -ne 200) "code=$($dne.code) (rejected)"

# ========== 5. TAG ==========
Write-Host ""; Write-Host "[5] Tag Module"
$tl = ApiGet '/kb/api/tag/list' $h
LogR 'Tag-List' ($tl.code -eq 200) "count=$($tl.data.Count)"

$tc = ApiPost '/kb/api/tag' '{"name":"e2e-tag","color":"#42b883"}' $h
$tagId = if ($tc) { $tc.data.id } else { 0 }
LogR 'Tag-Create' ($tc -and $tc.code -eq 200) "id=$tagId code=$($tc.code)"

# Bind tag to doc
if ($tagId -and $docId) {
    $tb = ApiPost '/kb/api/tag/bind' "{`"tagId`":$tagId,`"resourceType`":`"doc`",`"resourceId`":$docId}" $h
    LogR 'Tag-Bind' ($tb.code -eq 200) "tag bound to doc"
}

# Delete tag
if ($tagId) {
    $td = ApiDelete "/kb/api/tag/$tagId" $h
    LogR 'Tag-Delete' ($td.code -eq 200) "tag deleted"
}

# ========== 6. SEARCH ==========
Write-Host ""; Write-Host "[6] Search Module"
$s1 = ApiGet '/kb/api/search?q=e2e&page=1&size=10' $h
LogR 'Search-Hit' ($s1.code -eq 200) "total=$($s1.data.total)"

$s2 = ApiGet '/kb/api/search?q=zzznonexistent12345&page=1&size=10' $h
LogR 'Search-Miss' ($s2.code -eq 200 -and $s2.data.total -eq 0) "empty results total=0"

$s3 = ApiGet '/kb/api/search?q=Updated&page=1&size=10' $h
LogR 'Search-Updated' ($s3.code -eq 200) "total=$($s3.data.total)"

# ========== 7. SHARE ==========
Write-Host ""; Write-Host "[7] Share Module"
$sl = ApiGet '/kb/api/share/list' $h
LogR 'Share-List' ($sl.code -eq 200) "count=$($sl.data.Count)"

# Create share with extract code
$shc = ApiPost '/kb/api/share' "{`"resourceType`":`"doc`",`"resourceId`":$docId,`"extractCode`":`"1234`",`"expireDays`":7}" $h
$shareCode = if ($shc) { $shc.data.code } else { '' }
$shareId = if ($shc) { $shc.data.id } else { 0 }
LogR 'Share-Create' ($shc -and $shc.code -eq 200 -and $shareCode) "code=$shareCode"

# Verify share (no auth needed, with extract code)
if ($shareCode) {
    try {
        $sv = Invoke-RestMethod -Uri "$BASE/kb/api/share/verify/$shareCode?extractCode=1234" -Method Get -TimeoutSec 15
        LogR 'Share-Verify' ($sv.code -eq 200) "verified"
    } catch {
        $bc = 0; if ($_.Exception.Response) { try { $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); $j = $sr.ReadToEnd() | ConvertFrom-Json; $bc = $j.code } catch {} }
        LogR 'Share-Verify' $false "code=$bc failed"
    }

    # Verify with wrong code
    try {
        $sw = Invoke-RestMethod -Uri "$BASE/kb/api/share/verify/$shareCode?extractCode=wrong" -Method Get -TimeoutSec 15
        LogR 'Share-WrongCode' ($sw.code -ne 200) "wrong code rejected"
    } catch { LogR 'Share-WrongCode' $true "HTTP error (rejected)" }

    # Delete share
    if ($shareId) {
        $sd = ApiDelete "/kb/api/share/$shareId" $h
        LogR 'Share-Delete' ($sd.code -eq 200) "share deleted"
    }
}

# ========== 8. TRASH ==========
Write-Host ""; Write-Host "[8] Trash Module"
# Soft delete doc (move to trash)
$dd = ApiDelete "/kb/api/doc/$docId" $h
LogR 'Trash-SoftDelete' ($dd.code -eq 200) "doc moved to trash"

# List trash
$tl2 = ApiGet '/kb/api/trash/list?page=1&size=10' $h
LogR 'Trash-List' ($tl2.code -eq 200) "total=$($tl2.data.total)"

# Restore doc
if ($tl2.data.total -gt 0) {
    $tr = ApiPost "/kb/api/trash/restore/doc/$docId" '{}' $h
    LogR 'Trash-Restore' ($tr.code -eq 200) "doc restored"
}

# Verify restore (doc should be accessible again)
$dr = ApiGet "/kb/api/doc/$docId" $h
LogR 'Trash-VerifyRestore' ($dr.code -eq 200) "doc accessible after restore"

# Permanent delete (move to trash again then delete permanently)
$dd2 = ApiDelete "/kb/api/doc/$docId" $h
$pd = ApiDelete "/kb/api/trash/doc/$docId" $h
LogR 'Trash-PermanentDelete' ($pd.code -eq 200) "doc permanently deleted"

# Also delete doc2
if ($docId2) {
    ApiDelete "/kb/api/doc/$docId2" $h | Out-Null
    ApiDelete "/kb/api/trash/doc/$docId2" $h | Out-Null
}

# ========== 9. FILE ==========
Write-Host ""; Write-Host "[9] File Module"
$bl = ApiGet '/kb/api/bucket/list' $h
LogR 'File-BucketList' ($bl.code -eq 200) "buckets=$($bl.data.Count)"

if ($bl.data.Count -gt 0) {
    $bucketId = $bl.data[0].id
    $fl = ApiGet "/kb/api/file/list?bucketId=$bucketId&page=1&size=10" $h
    LogR 'File-List' ($fl.code -eq 200) "files=$($fl.data.total)"
} else {
    LogR 'File-List' $true "no buckets (skip)"
}

# ========== 10. OPS ==========
Write-Host ""; Write-Host "[10] Ops Module"
$od = ApiGet '/kb/api/ops/dashboard' $h
LogR 'Ops-Dashboard' ($od.code -eq 200) "stats=$([bool]$od.data)"

$hl = ApiGet '/kb/api/ops/host/list?page=1&size=10' $h
LogR 'Ops-HostList' ($hl.code -eq 200) "hosts=$($hl.data.total)"

# Create host
$hc = ApiPost '/kb/api/ops/host' '{"name":"e2e-test-host","ip":"10.0.0.99","sshPort":22}' $h
$hostId = if ($hc) { $hc.data.id } else { 0 }
LogR 'Ops-HostCreate' ($hc -and $hc.code -eq 200) "id=$hostId"

# Delete host
if ($hostId) {
    $hd = ApiDelete "/kb/api/ops/host/$hostId" $h
    LogR 'Ops-HostDelete' ($hd.code -eq 200) "host deleted"
}

$sv2 = ApiGet '/kb/api/ops/service/list?page=1&size=10' $h
LogR 'Ops-ServiceList' ($sv2.code -eq 200) "services=$($sv2.data.total)"

$ll = ApiGet '/kb/api/log/list?page=1&size=10' $h
LogR 'Ops-LogList' ($ll.code -eq 200) "logs=$($ll.data.total)"

# Log with action filter
$lf = ApiGet '/kb/api/log/list?page=1&size=10&action=login' $h
LogR 'Ops-LogFilter' ($lf.code -eq 200) "filtered logs=$($lf.data.total)"

# ========== 11. SECURITY ==========
Write-Host ""; Write-Host "[11] Security Module"

# No token
try { Invoke-RestMethod -Uri "$BASE/kb/api/user/profile" -TimeoutSec 10; LogR 'Sec-NoToken' $false "should reject" }
catch { $sc = [int]$_.Exception.Response.StatusCode; LogR 'Sec-NoToken' ($sc -eq 401) "status=$sc" }

# Invalid token
try { Invoke-RestMethod -Uri "$BASE/kb/api/user/profile" -Headers @{Authorization='Bearer invalid.token.here'} -TimeoutSec 10; LogR 'Sec-InvalidToken' $false "should reject" }
catch { $sc = [int]$_.Exception.Response.StatusCode; LogR 'Sec-InvalidToken' ($sc -eq 401) "status=$sc" }

# Expired token format
try { Invoke-RestMethod -Uri "$BASE/kb/api/user/profile" -Headers @{Authorization='Bearer expired'} -TimeoutSec 10; LogR 'Sec-ExpiredToken' $false "should reject" }
catch { $sc = [int]$_.Exception.Response.StatusCode; LogR 'Sec-ExpiredToken' ($sc -eq 401) "status=$sc" }

# CORS preflight
try {
    $cr = Invoke-WebRequest -Uri "$BASE/kb/api/auth/login" -Method Options -Headers @{Origin='http://localhost:5173'; 'Access-Control-Request-Method'='POST'} -TimeoutSec 10 -UseBasicParsing
    $acao = $cr.Headers['Access-Control-Allow-Origin']
    LogR 'Sec-CORS' ($cr.StatusCode -eq 200 -or $cr.StatusCode -eq 204) "CORS origin=$acao"
} catch { LogR 'Sec-CORS' $false $_.Exception.Message }

# ========== 12. CLEANUP ==========
Write-Host ""; Write-Host "[12] Cleanup"
if ($folderId) { ApiDelete "/kb/api/folder/$folderId" $h | Out-Null }
if ($spaceId) { ApiDelete "/kb/api/space/$spaceId" $h | Out-Null }
$verify = ApiGet '/kb/api/space/list' $h
$still = $verify.data | Where-Object { $_.id -eq $spaceId }
LogR 'Cleanup' (-not $still) "test data removed"

# Logout
$lo = ApiPost '/kb/api/auth/logout' '{}' $h
LogR 'Auth-Logout' ($lo.code -eq 200) "logged out"

# ========== SUMMARY ==========
Write-Host ""; Write-Host "========================================"
Write-Host " Comprehensive E2E Test Summary"
Write-Host "========================================"
$pass = ($results | Where-Object { $_.Result -eq 'PASS' }).Count
$fail = ($results | Where-Object { $_.Result -eq 'FAIL' }).Count
Write-Host "Total: $($results.Count) | PASS: $pass | FAIL: $fail"
Write-Host "========================================"
$results | Format-Table -AutoSize
