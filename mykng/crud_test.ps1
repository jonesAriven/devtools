# mykng CRUD Full-Chain Data Integrity Test
# Create -> Read -> Update -> Delete for Space/Folder/Doc
$BASE = 'http://192.168.31.105:8090'
$results = [System.Collections.ArrayList]::new()

function LogR($name, $ok, $detail) {
    [void]$results.Add([PSCustomObject]@{
        Step=$name; Result=if($ok){'PASS'}else{'FAIL'}; Detail=$detail
    })
    if ($ok) { Write-Host "  PASS  $name - $detail" }
    else { Write-Host "  FAIL  $name - $detail" -ForegroundColor Red }
}

Write-Host "========================================"
Write-Host " CRUD Full-Chain Data Integrity Test"
Write-Host "========================================"
Write-Host ""

# Login
Write-Host "[0] Login"
$login = Invoke-RestMethod -Uri "$BASE/kb/api/auth/login" -Method Post -Body '{"username":"admin","password":"admin123"}' -ContentType 'application/json' -TimeoutSec 15
$token = $login.data.accessToken
$headers = @{ 'Authorization' = "Bearer $token"; 'Content-Type' = 'application/json' }
LogR 'Login' ($login.code -eq 200) "token acquired"

# ============ CREATE: Space ============
Write-Host "[1] CREATE Space"
$spaceResp = Invoke-RestMethod -Uri "$BASE/kb/api/space" -Method Post -Headers $headers -Body '{"name":"crud-test-space","type":"personal","description":"CRUD test space"}' -TimeoutSec 15
$spaceId = $spaceResp.data.id
LogR 'CreateSpace' ($spaceResp.code -eq 200 -and $spaceId) "spaceId=$spaceId"

# ============ READ: Space ============
Write-Host "[2] READ Space List"
$spaceList = Invoke-RestMethod -Uri "$BASE/kb/api/space/list" -Method Get -Headers $headers -TimeoutSec 15
$found = $spaceList.data | Where-Object { $_.id -eq $spaceId }
LogR 'ReadSpaceList' ($spaceList.code -eq 200 -and $found) "space found in list"

# ============ CREATE: Folder ============
Write-Host "[3] CREATE Folder"
$folderBody = "{`"spaceId`":$spaceId,`"parentId`":0,`"name`":`"crud-test-folder`"}"
$folderResp = Invoke-RestMethod -Uri "$BASE/kb/api/folder" -Method Post -Headers $headers -Body $folderBody -TimeoutSec 15
$folderId = $folderResp.data.id
LogR 'CreateFolder' ($folderResp.code -eq 200 -and $folderId) "folderId=$folderId"

# ============ READ: Folder Tree ============
Write-Host "[4] READ Folder Tree"
$treeResp = Invoke-RestMethod -Uri "$BASE/kb/api/folder/tree/$spaceId" -Method Get -Headers $headers -TimeoutSec 15
$folderFound = $treeResp.data | Where-Object { $_.id -eq $folderId }
LogR 'ReadFolderTree' ($treeResp.code -eq 200 -and $folderFound) "folder found in tree"

# ============ CREATE: Doc ============
Write-Host "[5] CREATE Doc"
$docBody = "{`"folderId`":$folderId,`"title`":`"crud-test-doc`",`"content`":`"Hello CRUD World`"}"
$docResp = Invoke-RestMethod -Uri "$BASE/kb/api/doc" -Method Post -Headers $headers -Body $docBody -TimeoutSec 15
$docId = $docResp.data.id
LogR 'CreateDoc' ($docResp.code -eq 200 -and $docId) "docId=$docId"

# ============ READ: Doc ============
Write-Host "[6] READ Doc"
$getDoc = Invoke-RestMethod -Uri "$BASE/kb/api/doc/$docId" -Method Get -Headers $headers -TimeoutSec 15
LogR 'ReadDoc' ($getDoc.code -eq 200 -and $getDoc.data.title -eq 'crud-test-doc') "title=$($getDoc.data.title)"

# ============ READ: Doc List ============
Write-Host "[7] READ Doc List"
$docList = Invoke-RestMethod -Uri "$BASE/kb/api/doc/list?page=1&size=10" -Method Get -Headers $headers -TimeoutSec 15
LogR 'ReadDocList' ($docList.code -eq 200) "total=$($docList.data.total)"

# ============ UPDATE: Doc ============
Write-Host "[8] UPDATE Doc"
$updateBody = '{"title":"updated-doc-title","content":"Updated content"}'
$updateResp = Invoke-RestMethod -Uri "$BASE/kb/api/doc/$docId" -Method Put -Headers $headers -Body $updateBody -TimeoutSec 15
LogR 'UpdateDoc' ($updateResp.code -eq 200 -and $updateResp.data.title -eq 'updated-doc-title') "title=$($updateResp.data.title)"

# ============ UPDATE: Star Doc ============
Write-Host "[9] STAR Doc"
$starResp = Invoke-RestMethod -Uri "$BASE/kb/api/doc/$docId/star" -Method Put -Headers $headers -TimeoutSec 15
LogR 'StarDoc' ($starResp.code -eq 200) "star toggled"

# ============ CREATE: Tag ============
Write-Host "[10] CREATE Tag"
try {
    $tagResp = Invoke-RestMethod -Uri "$BASE/kb/api/tag" -Method Post -Headers $headers -Body '{"name":"crud-test-tag","color":"#ff0000"}' -TimeoutSec 15
    $tagId = $tagResp.data.id
    LogR 'CreateTag' ($tagResp.code -eq 200 -and $tagId) "tagId=$tagId"
} catch {
    LogR 'CreateTag' $false $_.Exception.Message
}

# ============ CREATE: Share ============
Write-Host "[11] CREATE Share"
try {
    $shareBody = "{`"resourceType`":`"doc`",`"resourceId`":$docId}"
    $shareResp = Invoke-RestMethod -Uri "$BASE/kb/api/share" -Method Post -Headers $headers -Body $shareBody -TimeoutSec 15
    $shareCode = $shareResp.data.code
    LogR 'CreateShare' ($shareResp.code -eq 200 -and $shareCode) "shareCode=$shareCode"

    # ============ READ: Share Verify ============
    if ($shareCode) {
        Write-Host "[12] READ Share Verify"
        $verifyResp = Invoke-RestMethod -Uri "$BASE/kb/api/share/verify/$shareCode" -Method Get -TimeoutSec 15
        LogR 'VerifyShare' ($verifyResp.code -eq 200) "share verified"
    }
} catch {
    LogR 'CreateShare' $false $_.Exception.Message
}

# ============ DELETE: Doc ============
Write-Host "[13] DELETE Doc"
$delDoc = Invoke-RestMethod -Uri "$BASE/kb/api/doc/$docId" -Method Delete -Headers $headers -TimeoutSec 15
LogR 'DeleteDoc' ($delDoc.code -eq 200) "doc deleted"

# ============ DELETE: Folder ============
Write-Host "[14] DELETE Folder"
$delFolder = Invoke-RestMethod -Uri "$BASE/kb/api/folder/$folderId" -Method Delete -Headers $headers -TimeoutSec 15
LogR 'DeleteFolder' ($delFolder.code -eq 200) "folder deleted"

# ============ DELETE: Space ============
Write-Host "[15] DELETE Space"
$delSpace = Invoke-RestMethod -Uri "$BASE/kb/api/space/$spaceId" -Method Delete -Headers $headers -TimeoutSec 15
LogR 'DeleteSpace' ($delSpace.code -eq 200) "space deleted"

# ============ VERIFY: Cleanup ============
Write-Host "[16] VERIFY Cleanup"
$finalList = Invoke-RestMethod -Uri "$BASE/kb/api/space/list" -Method Get -Headers $headers -TimeoutSec 15
$stillExists = $finalList.data | Where-Object { $_.id -eq $spaceId }
LogR 'CleanupVerify' (-not $stillExists) "space removed from list"

# ============ Summary ============
Write-Host ""
Write-Host "========================================"
Write-Host " CRUD Test Summary"
Write-Host "========================================"
$results | Format-Table -AutoSize

$pass = ($results | Where-Object { $_.Result -eq 'PASS' }).Count
$fail = ($results | Where-Object { $_.Result -eq 'FAIL' }).Count
Write-Host ""
Write-Host "Total: $($results.Count) | PASS: $pass | FAIL: $fail"
Write-Host "========================================"
