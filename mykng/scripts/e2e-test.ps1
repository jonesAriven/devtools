# ============================================================
# MyKNG E2E Full API Test Script (PS5 Compatible)
# Target: Cover all 136 API endpoints via Tailscale to mykng
# Encoding: ASCII-only to avoid PS5 mojibake
# ============================================================

param(
    [string]$BaseUrl = "http://100.93.36.113:8090/kb/api"
)

$ErrorActionPreference = "Continue"
$results = @()
$global:token = ""
$global:refreshToken = ""
$global:createdIds = @{}

# ============================================================
# Utility Functions
# ============================================================

function Invoke-KbApi {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = "",
        [string]$TestName
    )

    $url = "$BaseUrl$Path"
    $headers = @{"Content-Type" = "application/json; charset=utf-8"}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }

    $bodyJson = $null
    if ($Body) { $bodyJson = $Body | ConvertTo-Json -Depth 10 -Compress }

    $stopWatch = [System.Diagnostics.Stopwatch]::StartNew()
    $content = ""
    $statusCode = 0
    try {
        $response = Invoke-WebRequest -Uri $url -Method $Method -Headers $headers -Body $bodyJson -UseBasicParsing -TimeoutSec 30
        $statusCode = [int]$response.StatusCode
        $content = $response.Content
        $stopWatch.Stop()
    } catch {
        $stopWatch.Stop()
        $resp = $null
        try { $resp = $_.Exception.Response } catch {}
        if ($resp) {
            try { $statusCode = [int]$resp.StatusCode } catch {}
            try {
                $stream = $resp.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $content = $reader.ReadToEnd()
                    $reader.Close()
                }
            } catch {}
        }
        # Fallback: extract status code from exception message (PS5 limitation)
        if ($statusCode -eq 0) {
            $msg = $_.Exception.Message
            if ($msg -match '\((\d+)\)') {
                $statusCode = [int]$Matches[1]
            }
        }
        # Construct fallback JSON if no content (PS5 cannot read 401 response body)
        if (-not $content) {
            $errorMsg = $_.Exception.Message
            $errorMsg = $errorMsg -replace '"', "'" -replace '\\', '/'
            $resultCode = $statusCode
            if ($resultCode -eq 0) { $resultCode = 500 }
            $content = "{`"code`":$resultCode,`"message`":`"$errorMsg`",`"data`":null,`"traceId`":`"`"}"
        }
    }

    # Parse JSON content
    try {
        $parsed = $content | ConvertFrom-Json
        return @{
            Success = ($statusCode -ge 200 -and $statusCode -lt 300)
            Code = $parsed.code
            Message = $parsed.message
            Data = $parsed.data
            TraceId = $parsed.traceId
            ElapsedMs = $stopWatch.ElapsedMilliseconds
            Raw = $parsed
            HttpStatusCode = $statusCode
        }
    } catch {
        return @{
            Success = $false
            Code = $statusCode
            Message = "Non-JSON response"
            Data = $null
            TraceId = ""
            ElapsedMs = $stopWatch.ElapsedMilliseconds
            Error = $content
            HttpStatusCode = $statusCode
        }
    }
}

function Assert-Test {
    param(
        [string]$Name,
        $Result,
        [int]$ExpectedCode = 200,
        [scriptblock]$ExtraCheck = $null
    )
    $passed = $false
    $detail = ""
    $elapsedMs = 0
    $actualCode = "-"
    $traceId = "-"

    if ($Result) {
        $elapsedMs = $Result.ElapsedMs
        if ($Result.Code) { $actualCode = $Result.Code }
        if ($Result.TraceId) { $traceId = $Result.TraceId }
    }

    if (-not $Result) {
        $detail = "No response"
    } elseif ($Result.Code -eq $ExpectedCode) {
        if ($ExtraCheck) {
            try {
                &$ExtraCheck $Result
                $passed = $true
            } catch {
                $detail = "Extra check failed: $_"
            }
        } else {
            $passed = $true
        }
    } else {
        $detail = "Expected code=$ExpectedCode, actual code=$($Result.Code), msg=$($Result.Message)"
    }

    $statusStr = "FAIL"
    if ($passed) { $statusStr = "PASS" }

    $script:results += [PSCustomObject]@{
        Test = $Name
        Status = $statusStr
        ElapsedMs = $elapsedMs
        Code = $actualCode
        TraceId = $traceId
        Detail = $detail
    }

    $tag = "[FAIL]"
    $color = "Red"
    if ($passed) { $tag = "[PASS]"; $color = "Green" }
    Write-Host ("{0} {1} ({2}ms)" -f $tag, $Name, $elapsedMs) -ForegroundColor $color
    return $passed
}

# Helper to build query string safely (avoid & issue)
function Build-Query {
    param([hashtable]$Params)
    $parts = @()
    foreach ($k in $Params.Keys) { $parts += "$k=$($Params[$k])" }
    return "?" + ($parts -join "&")
}

# ============================================================
# 1. Auth Module (12 endpoints)
# ============================================================
Write-Host "========== 1. Auth Module (kb-auth 12 endpoints) ==========" -ForegroundColor Cyan

# 1.1 Login
$r = Invoke-KbApi -Method POST -Path "/auth/login" -Body @{username="admin";password="admin123"} -TestName "Login"
Assert-Test "1.1 POST /auth/login Login" $r -ExtraCheck {
    param($r)
    if (-not $r.Data.accessToken) { throw "accessToken is null" }
    if (-not $r.Data.refreshToken) { throw "refreshToken is null" }
    $global:token = $r.Data.accessToken
    $global:refreshToken = $r.Data.refreshToken
}

# 1.2 Get current user info
$r = Invoke-KbApi -Method GET -Path "/auth/me" -Token $global:token -TestName "GetCurrentUser"
Assert-Test "1.2 GET /auth/me GetCurrentUser" $r -ExtraCheck {
    param($r)
    if (-not $r.Data.user) { throw "user is null" }
}

# 1.3 Get user profile
$r = Invoke-KbApi -Method GET -Path "/user/profile" -Token $global:token -TestName "GetUserProfile"
Assert-Test "1.3 GET /user/profile GetUserProfile" $r

# 1.4 Update user profile
$r = Invoke-KbApi -Method PUT -Path "/user/profile" -Body @{nickname="admin-e2e";email="admin@kb.test"} -Token $global:token -TestName "UpdateUserProfile"
Assert-Test "1.4 PUT /user/profile UpdateUserProfile" $r

# 1.5 Create API Token
$r = Invoke-KbApi -Method POST -Path "/token" -Body @{name="E2E-Token";scope="read"} -Token $global:token -TestName "CreateApiToken"
$apiTokenId = $null
if ($r.Data -and $r.Data.id) { $apiTokenId = $r.Data.id; $global:createdIds["apiToken"] = $apiTokenId }
Assert-Test "1.5 POST /token CreateApiToken" $r -ExtraCheck {
    param($r)
    if (-not $r.Data.token) { throw "plain token is null" }
}

# 1.6 API Token list
$q = Build-Query @{page=1;size=10}
$r = Invoke-KbApi -Method GET -Path "/token$q" -Token $global:token -TestName "ApiTokenList"
Assert-Test "1.6 GET /token ApiTokenList" $r

# 1.7 Toggle API Token status
if ($apiTokenId) {
    $r = Invoke-KbApi -Method PUT -Path "/token/$apiTokenId/toggle" -Token $global:token -TestName "ToggleApiToken"
    Assert-Test "1.7 PUT /token/$apiTokenId/toggle ToggleApiToken" $r
} else {
    Assert-Test "1.7 PUT /token/{id}/toggle ToggleApiToken" @{Code=0;Message="apiTokenId is null"}
}

# 1.8 Verify API Token (invalid)
$r = Invoke-KbApi -Method POST -Path "/token/verify" -Body @{token="invalid_token_test"} -TestName "VerifyApiTokenInvalid"
Assert-Test "1.8 POST /token/verify VerifyApiTokenInvalid (expect 401)" $r -ExpectedCode 401

# 1.9 Delete API Token
if ($apiTokenId) {
    $r = Invoke-KbApi -Method DELETE -Path "/token/$apiTokenId" -Token $global:token -TestName "DeleteApiToken"
    Assert-Test "1.9 DELETE /token/$apiTokenId DeleteApiToken" $r
} else {
    Assert-Test "1.9 DELETE /token/{id} DeleteApiToken" @{Code=0;Message="apiTokenId is null"}
}

# 1.10 Refresh token
$r = Invoke-KbApi -Method POST -Path "/auth/refresh" -Body @{refreshToken=$global:refreshToken} -TestName "RefreshToken"
if ($r.Data -and $r.Data.accessToken) { $global:token = $r.Data.accessToken }
Assert-Test "1.10 POST /auth/refresh RefreshToken" $r

# 1.11 Change password (back to original)
$r = Invoke-KbApi -Method PUT -Path "/user/password" -Body @{oldPassword="admin123";newPassword="admin123"} -Token $global:token -TestName "ChangePassword"
Assert-Test "1.11 PUT /user/password ChangePassword" $r

# 1.12 Logout
$r = Invoke-KbApi -Method POST -Path "/auth/logout" -Token $global:token -TestName "Logout"
Assert-Test "1.12 POST /auth/logout Logout" $r

# Re-login for subsequent tests
$r = Invoke-KbApi -Method POST -Path "/auth/login" -Body @{username="admin";password="admin123"} -TestName "ReLogin"
if ($r.Data -and $r.Data.accessToken) { $global:token = $r.Data.accessToken }

# ============================================================
# 2. File Module (13 endpoints)
# ============================================================
Write-Host "========== 2. File Module (kb-file 13 endpoints) ==========" -ForegroundColor Cyan

# 2.1 File list
$q = Build-Query @{page=1;size=10}
$r = Invoke-KbApi -Method GET -Path "/file/list$q" -Token $global:token -TestName "FileList"
Assert-Test "2.1 GET /file/list FileList" $r

# 2.2 Bucket list
$r = Invoke-KbApi -Method GET -Path "/bucket/list" -Token $global:token -TestName "BucketList"
Assert-Test "2.2 GET /bucket/list BucketList" $r

# 2.3 Get non-existent file detail
$r = Invoke-KbApi -Method GET -Path "/file/999999" -Token $global:token -TestName "GetNonExistentFile"
Assert-Test "2.3 GET /file/999999 GetNonExistentFile (expect 404)" $r -ExpectedCode 404

# 2.4 Get non-existent file parse status
$r = Invoke-KbApi -Method GET -Path "/file/999999/parse-status" -Token $global:token -TestName "GetNonExistentFileParseStatus"
Assert-Test "2.4 GET /file/999999/parse-status GetNonExistentFileParseStatus (expect 404)" $r -ExpectedCode 404

# 2.5 Get non-existent file download URL
$r = Invoke-KbApi -Method GET -Path "/file/999999/download" -Token $global:token -TestName "GetNonExistentFileDownload"
Assert-Test "2.5 GET /file/999999/download GetNonExistentFileDownload (expect 404)" $r -ExpectedCode 404

# 2.6 Reparse non-existent file
$r = Invoke-KbApi -Method POST -Path "/file/999999/reparse" -Token $global:token -TestName "ReparseNonExistentFile"
Assert-Test "2.6 POST /file/999999/reparse ReparseNonExistentFile (expect 404)" $r -ExpectedCode 404

# 2.7 Bucket stats (non-existent, expect 404)
$r = Invoke-KbApi -Method GET -Path "/bucket/999999/stats" -Token $global:token -TestName "GetNonExistentBucketStats"
Assert-Test "2.7 GET /bucket/999999/stats GetNonExistentBucketStats (expect 404)" $r -ExpectedCode 404

# ============================================================
# 3. Knowledge Module (52 endpoints)
# ============================================================
Write-Host "========== 3. Knowledge Module (kb-knowledge 52 endpoints) ==========" -ForegroundColor Cyan

# 3.1 Space list
$r = Invoke-KbApi -Method GET -Path "/space/list" -Token $global:token -TestName "SpaceList"
Assert-Test "3.1 GET /space/list SpaceList" $r

# 3.2 Create space
$r = Invoke-KbApi -Method POST -Path "/space" -Body @{name="E2E-Space";description="auto-test"} -Token $global:token -TestName "CreateSpace"
$spaceId = $null
if ($r.Data -and $r.Data.id) { $spaceId = $r.Data.id; $global:createdIds["space"] = $spaceId }
Assert-Test "3.2 POST /space CreateSpace" $r

# 3.3 Get space detail
if ($spaceId) {
    $r = Invoke-KbApi -Method GET -Path "/space/$spaceId" -Token $global:token -TestName "GetSpaceDetail"
    Assert-Test "3.3 GET /space/$spaceId GetSpaceDetail" $r
}

# 3.4 Update space
if ($spaceId) {
    $r = Invoke-KbApi -Method PUT -Path "/space/$spaceId" -Body @{name="E2E-Space-Updated";description="updated"} -Token $global:token -TestName "UpdateSpace"
    Assert-Test "3.4 PUT /space/$spaceId UpdateSpace" $r
}

# 3.5 Folder tree (spaceId required by @RequestParam Long spaceId)
if ($spaceId) {
    $q = Build-Query @{spaceId=$spaceId}
    $r = Invoke-KbApi -Method GET -Path "/folder/tree$q" -Token $global:token -TestName "FolderTree"
    Assert-Test "3.5 GET /folder/tree FolderTree" $r
} else {
    Assert-Test "3.5 GET /folder/tree FolderTree (skipped, no spaceId)" @{Code=0;Message="spaceId is null"}
}

# 3.6 Folder tree by space
if ($spaceId) {
    $r = Invoke-KbApi -Method GET -Path "/folder/tree/$spaceId" -Token $global:token -TestName "FolderTreeBySpace"
    Assert-Test "3.6 GET /folder/tree/$spaceId FolderTreeBySpace" $r
}

# 3.7 Create folder
$folderId = $null
if ($spaceId) {
    $r = Invoke-KbApi -Method POST -Path "/folder" -Body @{name="E2E-Folder";spaceId=$spaceId;parentId=0} -Token $global:token -TestName "CreateFolder"
    if ($r.Data -and $r.Data.id) { $folderId = $r.Data.id; $global:createdIds["folder"] = $folderId }
    Assert-Test "3.7 POST /folder CreateFolder" $r
}

# 3.8 Get folder detail
if ($folderId) {
    $r = Invoke-KbApi -Method GET -Path "/folder/$folderId" -Token $global:token -TestName "GetFolderDetail"
    Assert-Test "3.8 GET /folder/$folderId GetFolderDetail" $r
}

# 3.9 Update folder
if ($folderId) {
    $r = Invoke-KbApi -Method PUT -Path "/folder/$folderId" -Body @{name="E2E-Folder-Updated"} -Token $global:token -TestName "UpdateFolder"
    Assert-Test "3.9 PUT /folder/$folderId UpdateFolder" $r
}

# 3.10 Doc list
$q = Build-Query @{page=1;size=10}
$r = Invoke-KbApi -Method GET -Path "/doc/list$q" -Token $global:token -TestName "DocList"
Assert-Test "3.10 GET /doc/list DocList" $r

# 3.11 Create doc
$docId = $null
if ($folderId) {
    $r = Invoke-KbApi -Method POST -Path "/doc" -Body @{title="E2E-Doc";content="# Test content`n`nE2E test doc.";folderId=$folderId} -Token $global:token -TestName "CreateDoc"
    if ($r.Data -and $r.Data.id) { $docId = $r.Data.id; $global:createdIds["doc"] = $docId }
    Assert-Test "3.11 POST /doc CreateDoc" $r
}

# 3.12 Get doc detail
if ($docId) {
    $r = Invoke-KbApi -Method GET -Path "/doc/$docId" -Token $global:token -TestName "GetDocDetail"
    Assert-Test "3.12 GET /doc/$docId GetDocDetail" $r
}

# 3.13 Update doc
if ($docId) {
    $r = Invoke-KbApi -Method PUT -Path "/doc/$docId" -Body @{title="E2E-Doc-Updated";content="# Updated`n`nVersion 2"} -Token $global:token -TestName "UpdateDoc"
    Assert-Test "3.13 PUT /doc/$docId UpdateDoc" $r
}

# 3.14 Star doc
if ($docId) {
    $r = Invoke-KbApi -Method PUT -Path "/doc/$docId/star" -Body @{starred=$true} -Token $global:token -TestName "StarDoc"
    Assert-Test "3.14 PUT /doc/$docId/star StarDoc" $r
}

# 3.15 Search (q required by @RequestParam String q)
$q = Build-Query @{q="test";page=1;size=10}
$r = Invoke-KbApi -Method GET -Path "/search$q" -Token $global:token -TestName "Search"
Assert-Test "3.15 GET /search Search" $r

# 3.16 Search suggest (supports both q and keyword, use q for consistency)
$q = Build-Query @{q="te"}
$r = Invoke-KbApi -Method GET -Path "/search/suggest$q" -Token $global:token -TestName "SearchSuggest"
Assert-Test "3.16 GET /search/suggest SearchSuggest" $r

# 3.17 Tag list
$r = Invoke-KbApi -Method GET -Path "/tag/list" -Token $global:token -TestName "TagList"
Assert-Test "3.17 GET /tag/list TagList" $r

# 3.18 Create tag
$r = Invoke-KbApi -Method POST -Path "/tag" -Body @{name="E2E-Tag"} -Token $global:token -TestName "CreateTag"
$tagId = $null
if ($r.Data -and $r.Data.id) { $tagId = $r.Data.id; $global:createdIds["tag"] = $tagId }
Assert-Test "3.18 POST /tag CreateTag" $r

# 3.19 Update tag
if ($tagId) {
    $r = Invoke-KbApi -Method PUT -Path "/tag/$tagId" -Body @{name="E2E-Tag-Updated"} -Token $global:token -TestName "UpdateTag"
    Assert-Test "3.19 PUT /tag/$tagId UpdateTag" $r
}

# 3.20 Tag bind
if ($tagId -and $docId) {
    $r = Invoke-KbApi -Method POST -Path "/tag/bind" -Body @{tagId=$tagId;resourceType="DOC";resourceId=$docId} -Token $global:token -TestName "TagBind"
    Assert-Test "3.20 POST /tag/bind TagBind" $r
}

# 3.21 Tag resource list (resourceId + resourceType required)
if ($docId) {
    $q = Build-Query @{resourceId=$docId;resourceType="DOC"}
    $r = Invoke-KbApi -Method GET -Path "/tag/resource$q" -Token $global:token -TestName "TagResourceList"
    Assert-Test "3.21 GET /tag/resource TagResourceList" $r
} else {
    Assert-Test "3.21 GET /tag/resource TagResourceList (skipped, no docId)" @{Code=0;Message="docId is null"}
}

# 3.22 Tag unbind (RequestParam: tagId, resourceType, resourceId as query params)
if ($tagId -and $docId) {
    $q = Build-Query @{tagId=$tagId;resourceType="DOC";resourceId=$docId}
    $r = Invoke-KbApi -Method DELETE -Path "/tag/unbind$q" -Token $global:token -TestName "TagUnbind"
    Assert-Test "3.22 DELETE /tag/unbind TagUnbind" $r
} else {
    Assert-Test "3.22 DELETE /tag/unbind TagUnbind (skipped, no tagId/docId)" @{Code=0;Message="tagId or docId is null"}
}

# 3.23 Create share (extractCode required: server auto-generates 4-digit if not provided)
$shareId = $null; $shareCode = $null; $shareExtractCode = $null
if ($docId) {
    $r = Invoke-KbApi -Method POST -Path "/share" -Body @{resourceType="doc";resourceId=$docId;extractCode="1234"} -Token $global:token -TestName "CreateShare"
    if ($r.Data) {
        $shareId = $r.Data.id
        $shareCode = $r.Data.code
        $shareExtractCode = $r.Data.extractCode
        $global:createdIds["share"] = $shareId
    }
    Assert-Test "3.23 POST /share CreateShare" $r
} else {
    Assert-Test "3.23 POST /share CreateShare (skipped, no docId)" @{Code=0;Message="docId is null"}
}

# 3.24 Share list
$q = Build-Query @{page=1;size=10}
$r = Invoke-KbApi -Method GET -Path "/share/list$q" -Token $global:token -TestName "ShareList"
Assert-Test "3.24 GET /share/list ShareList" $r

# 3.25 My shares
$r = Invoke-KbApi -Method GET -Path "/share/my" -Token $global:token -TestName "MyShares"
Assert-Test "3.25 GET /share/my MyShares" $r

# 3.26 Verify share (extractCode required, in gateway whitelist - no token needed)
if ($shareCode -and $shareExtractCode) {
    $q = Build-Query @{extractCode=$shareExtractCode}
    $r = Invoke-KbApi -Method GET -Path "/share/verify/$shareCode$q" -TestName "VerifyShare"
    Assert-Test "3.26 GET /share/verify/$shareCode VerifyShare" $r
} else {
    Assert-Test "3.26 GET /share/verify/{code} VerifyShare (skipped, no shareCode)" @{Code=0;Message="shareCode is null"}
}

# 3.27 Share detail (extractCode required, in gateway whitelist)
if ($shareCode -and $shareExtractCode) {
    $q = Build-Query @{extractCode=$shareExtractCode}
    $r = Invoke-KbApi -Method GET -Path "/share/detail/$shareCode$q" -TestName "ShareDetail"
    Assert-Test "3.27 GET /share/detail/$shareCode ShareDetail" $r
} else {
    Assert-Test "3.27 GET /share/detail/{code} ShareDetail (skipped, no shareCode)" @{Code=0;Message="shareCode is null"}
}

# 3.28 Version list
$q = Build-Query @{page=1;size=10}
$r = Invoke-KbApi -Method GET -Path "/version/list$q" -Token $global:token -TestName "VersionList"
Assert-Test "3.28 GET /version/list VersionList" $r

# 3.29 Version list by resource
if ($docId) {
    $r = Invoke-KbApi -Method GET -Path "/version/list/DOC/$docId" -Token $global:token -TestName "VersionListByResource"
    Assert-Test "3.29 GET /version/list/DOC/$docId VersionListByResource" $r
}

# 3.30 Trash list
$q = Build-Query @{page=1;size=10}
$r = Invoke-KbApi -Method GET -Path "/trash/list$q" -Token $global:token -TestName "TrashList"
Assert-Test "3.30 GET /trash/list TrashList" $r

# 3.31 Web collection list
$q = Build-Query @{page=1;size=10}
$r = Invoke-KbApi -Method GET -Path "/web/list$q" -Token $global:token -TestName "WebList"
Assert-Test "3.31 GET /web/list WebList" $r

# 3.32 Web collect (folderId required by API)
$webId = $null
if ($folderId) {
    $r = Invoke-KbApi -Method POST -Path "/web/collect" -Body @{url="https://example.com/e2e-test";title="E2E-Web";folderId=$folderId} -Token $global:token -TestName "WebCollect"
    if ($r.Data -and $r.Data.id) { $webId = $r.Data.id; $global:createdIds["web"] = $webId }
    Assert-Test "3.32 POST /web/collect WebCollect" $r
} else {
    Assert-Test "3.32 POST /web/collect WebCollect (skipped, no folderId)" @{Code=0;Message="folderId is null"}
}

# 3.33 Web detail
if ($webId) {
    $r = Invoke-KbApi -Method GET -Path "/web/$webId" -Token $global:token -TestName "WebDetail"
    Assert-Test "3.33 GET /web/$webId WebDetail" $r
}

# 3.34 Star web
if ($webId) {
    $r = Invoke-KbApi -Method PUT -Path "/web/$webId/star" -Body @{starred=$true} -Token $global:token -TestName "StarWeb"
    Assert-Test "3.34 PUT /web/$webId/star StarWeb" $r
}

# 3.35 Delete web
if ($webId) {
    $r = Invoke-KbApi -Method DELETE -Path "/web/$webId" -Token $global:token -TestName "DeleteWeb"
    Assert-Test "3.35 DELETE /web/$webId DeleteWeb" $r
}

# 3.36 Delete share
if ($shareId) {
    $r = Invoke-KbApi -Method DELETE -Path "/share/$shareId" -Token $global:token -TestName "DeleteShare"
    Assert-Test "3.36 DELETE /share/$shareId DeleteShare" $r
}

# 3.37 Delete tag
if ($tagId) {
    $r = Invoke-KbApi -Method DELETE -Path "/tag/$tagId" -Token $global:token -TestName "DeleteTag"
    Assert-Test "3.37 DELETE /tag/$tagId DeleteTag" $r
}

# 3.38 Doc to trash
if ($docId) {
    $r = Invoke-KbApi -Method DELETE -Path "/doc/$docId" -Token $global:token -TestName "DocToTrash"
    Assert-Test "3.38 DELETE /doc/$docId DocToTrash" $r
}

# 3.39 Restore from trash
if ($docId) {
    $r = Invoke-KbApi -Method POST -Path "/trash/restore/DOC/$docId" -Token $global:token -TestName "RestoreFromTrash"
    Assert-Test "3.39 POST /trash/restore/DOC/$docId RestoreFromTrash" $r
}

# 3.40 Permanent delete
if ($docId) {
    $r = Invoke-KbApi -Method DELETE -Path "/doc/$docId" -Token $global:token -TestName "DocToTrashAgain"
    $r2 = Invoke-KbApi -Method DELETE -Path "/trash/DOC/$docId" -Token $global:token -TestName "PermanentDelete"
    Assert-Test "3.40 DELETE /trash/DOC/$docId PermanentDelete" $r2
}

# 3.41 Delete folder
if ($folderId) {
    $r = Invoke-KbApi -Method DELETE -Path "/folder/$folderId" -Token $global:token -TestName "DeleteFolder"
    Assert-Test "3.41 DELETE /folder/$folderId DeleteFolder" $r
}

# 3.42 Delete space
if ($spaceId) {
    $r = Invoke-KbApi -Method DELETE -Path "/space/$spaceId" -Token $global:token -TestName "DeleteSpace"
    Assert-Test "3.42 DELETE /space/$spaceId DeleteSpace" $r
}

# 3.43 Empty trash
$r = Invoke-KbApi -Method DELETE -Path "/trash/empty" -Token $global:token -TestName "EmptyTrash"
Assert-Test "3.43 DELETE /trash/empty EmptyTrash" $r

# ============================================================
# 4. Ops Module (47 endpoints)
# ============================================================
Write-Host "========== 4. Ops Module (kb-ops 47 endpoints) ==========" -ForegroundColor Cyan

# 4.1 Dashboard
$r = Invoke-KbApi -Method GET -Path "/ops/dashboard" -Token $global:token -TestName "OpsDashboard"
Assert-Test "4.1 GET /ops/dashboard OpsDashboard" $r

# 4.2 Host list
$r = Invoke-KbApi -Method GET -Path "/ops/host/list" -Token $global:token -TestName "HostList"
Assert-Test "4.2 GET /ops/host/list HostList" $r

# 4.3 Create host
$r = Invoke-KbApi -Method POST -Path "/ops/host" -Body @{name="E2E-Host";ip="192.168.99.99";os="Linux";description="E2E test"} -Token $global:token -TestName "CreateHost"
$hostId = $null
if ($r.Data -and $r.Data.id) { $hostId = $r.Data.id; $global:createdIds["host"] = $hostId }
Assert-Test "4.3 POST /ops/host CreateHost" $r

# 4.4 Host detail
if ($hostId) {
    $r = Invoke-KbApi -Method GET -Path "/ops/host/$hostId" -Token $global:token -TestName "HostDetail"
    Assert-Test "4.4 GET /ops/host/$hostId HostDetail" $r
}

# 4.5 Update host
if ($hostId) {
    $r = Invoke-KbApi -Method PUT -Path "/ops/host/$hostId" -Body @{name="E2E-Host-Updated";ip="192.168.99.100";os="Linux"} -Token $global:token -TestName "UpdateHost"
    Assert-Test "4.5 PUT /ops/host/$hostId UpdateHost" $r
}

# 4.6 Service list
$r = Invoke-KbApi -Method GET -Path "/ops/service/list" -Token $global:token -TestName "ServiceList"
Assert-Test "4.6 GET /ops/service/list ServiceList" $r

# 4.7 Create service
$svcId = $null
if ($hostId) {
    $r = Invoke-KbApi -Method POST -Path "/ops/service" -Body @{name="E2E-Service";hostId=$hostId;port=8080;protocol="HTTP"} -Token $global:token -TestName "CreateService"
    if ($r.Data -and $r.Data.id) { $svcId = $r.Data.id; $global:createdIds["service"] = $svcId }
    Assert-Test "4.7 POST /ops/service CreateService" $r
}

# 4.8 Port list
$r = Invoke-KbApi -Method GET -Path "/ops/port/list" -Token $global:token -TestName "PortList"
Assert-Test "4.8 GET /ops/port/list PortList" $r

# 4.9 Create port
$portId = $null
if ($hostId) {
    $r = Invoke-KbApi -Method POST -Path "/ops/port" -Body @{hostId=$hostId;port=9999;protocol="TCP";purpose="E2E test port"} -Token $global:token -TestName "CreatePort"
    if ($r.Data -and $r.Data.id) { $portId = $r.Data.id; $global:createdIds["port"] = $portId }
    Assert-Test "4.9 POST /ops/port CreatePort" $r
}

# 4.10 Credential list
$r = Invoke-KbApi -Method GET -Path "/ops/credential/list" -Token $global:token -TestName "CredentialList"
Assert-Test "4.10 GET /ops/credential/list CredentialList" $r

# 4.11 Create credential
$credId = $null
$r = Invoke-KbApi -Method POST -Path "/ops/credential" -Body @{name="E2E-Cred";type="SSH";username="root";password="test123"} -Token $global:token -TestName "CreateCredential"
if ($r.Data -and $r.Data.id) { $credId = $r.Data.id; $global:createdIds["credential"] = $credId }
Assert-Test "4.11 POST /ops/credential CreateCredential" $r

# 4.12 Domain list
$r = Invoke-KbApi -Method GET -Path "/ops/domain/list" -Token $global:token -TestName "DomainList"
Assert-Test "4.12 GET /ops/domain/list DomainList" $r

# 4.13 Create domain
$domainId = $null
$r = Invoke-KbApi -Method POST -Path "/ops/domain" -Body @{domain="e2e-test.kb.local";type="SUB_DOMAIN";purpose="E2E test"} -Token $global:token -TestName "CreateDomain"
if ($r.Data -and $r.Data.id) { $domainId = $r.Data.id; $global:createdIds["domain"] = $domainId }
Assert-Test "4.13 POST /ops/domain CreateDomain" $r

# 4.14 Dependency list
$r = Invoke-KbApi -Method GET -Path "/ops/dependency/list" -Token $global:token -TestName "DependencyList"
Assert-Test "4.14 GET /ops/dependency/list DependencyList" $r

# 4.15 Create dependency
$depId = $null
if ($svcId) {
    $r = Invoke-KbApi -Method POST -Path "/ops/dependency" -Body @{serviceId=$svcId;serviceName="E2E-Service";dependsOnServiceId=$svcId;dependsOnServiceName="E2E-Service";dependencyType="RUNTIME"} -Token $global:token -TestName "CreateDependency"
    if ($r.Data -and $r.Data.id) { $depId = $r.Data.id; $global:createdIds["dependency"] = $depId }
    Assert-Test "4.15 POST /ops/dependency CreateDependency" $r
}

# 4.16 Deployment list
$r = Invoke-KbApi -Method GET -Path "/ops/deployment/list" -Token $global:token -TestName "DeploymentList"
Assert-Test "4.16 GET /ops/deployment/list DeploymentList" $r

# 4.17 Recent deployments
$r = Invoke-KbApi -Method GET -Path "/ops/deployment/recent" -Token $global:token -TestName "RecentDeployments"
Assert-Test "4.17 GET /ops/deployment/recent RecentDeployments" $r

# 4.18 Conflict detect
$r = Invoke-KbApi -Method POST -Path "/ops/conflict/detect" -Token $global:token -TestName "ConflictDetect"
Assert-Test "4.18 POST /ops/conflict/detect ConflictDetect" $r

# 4.19 Conflict list
$r = Invoke-KbApi -Method GET -Path "/ops/conflict/list" -Token $global:token -TestName "ConflictList"
Assert-Test "4.19 GET /ops/conflict/list ConflictList" $r

# 4.20 Ops knowledge list
$r = Invoke-KbApi -Method GET -Path "/ops/knowledge/list" -Token $global:token -TestName "OpsKnowledgeList"
Assert-Test "4.20 GET /ops/knowledge/list OpsKnowledgeList" $r

# 4.21 Create ops knowledge
$knId = $null
$r = Invoke-KbApi -Method POST -Path "/ops/knowledge" -Body @{title="E2E-OpsKnowledge";content="test content"} -Token $global:token -TestName "CreateOpsKnowledge"
if ($r.Data -and $r.Data.id) { $knId = $r.Data.id; $global:createdIds["opsKnowledge"] = $knId }
Assert-Test "4.21 POST /ops/knowledge CreateOpsKnowledge" $r

# 4.22 Operation log list
$q = Build-Query @{page=1;size=10}
$r = Invoke-KbApi -Method GET -Path "/ops/log/list$q" -Token $global:token -TestName "OperationLogList"
Assert-Test "4.22 GET /ops/log/list OperationLogList" $r

# 4.23 Dashboard snapshot refresh
$r = Invoke-KbApi -Method POST -Path "/ops/dashboard/snapshot/refresh" -Token $global:token -TestName "DashboardSnapshotRefresh"
Assert-Test "4.23 POST /ops/dashboard/snapshot/refresh DashboardSnapshotRefresh" $r

# 4.24-4.30 Cleanup: delete created resources
if ($depId) {
    $r = Invoke-KbApi -Method DELETE -Path "/ops/dependency/$depId" -Token $global:token -TestName "DeleteDependency"
    Assert-Test "4.24 DELETE /ops/dependency/$depId DeleteDependency" $r
}
if ($domainId) {
    $r = Invoke-KbApi -Method DELETE -Path "/ops/domain/$domainId" -Token $global:token -TestName "DeleteDomain"
    Assert-Test "4.25 DELETE /ops/domain/$domainId DeleteDomain" $r
}
if ($credId) {
    $r = Invoke-KbApi -Method DELETE -Path "/ops/credential/$credId" -Token $global:token -TestName "DeleteCredential"
    Assert-Test "4.26 DELETE /ops/credential/$credId DeleteCredential" $r
}
if ($portId) {
    $r = Invoke-KbApi -Method DELETE -Path "/ops/port/$portId" -Token $global:token -TestName "DeletePort"
    Assert-Test "4.27 DELETE /ops/port/$portId DeletePort" $r
}
if ($svcId) {
    $r = Invoke-KbApi -Method DELETE -Path "/ops/service/$svcId" -Token $global:token -TestName "DeleteService"
    Assert-Test "4.28 DELETE /ops/service/$svcId DeleteService" $r
}
if ($knId) {
    $r = Invoke-KbApi -Method DELETE -Path "/ops/knowledge/$knId" -Token $global:token -TestName "DeleteOpsKnowledge"
    Assert-Test "4.29 DELETE /ops/knowledge/$knId DeleteOpsKnowledge" $r
}
if ($hostId) {
    $r = Invoke-KbApi -Method DELETE -Path "/ops/host/$hostId" -Token $global:token -TestName "DeleteHost"
    Assert-Test "4.30 DELETE /ops/host/$hostId DeleteHost" $r
}

# ============================================================
# 5. Intelligence Module (12 endpoints)
# ============================================================
Write-Host "========== 5. Intelligence Module (kb-intelligence 12 endpoints) ==========" -ForegroundColor Cyan

# 5.1 Knowledge doc list
$q = Build-Query @{page=1;size=10}
$r = Invoke-KbApi -Method GET -Path "/intelligence/machine/docs$q" -Token $global:token -TestName "IntelligenceDocList"
Assert-Test "5.1 GET /intelligence/machine/docs IntelligenceDocList" $r

# 5.2 Knowledge stats
$r = Invoke-KbApi -Method GET -Path "/intelligence/machine/stats" -Token $global:token -TestName "IntelligenceStats"
Assert-Test "5.2 GET /intelligence/machine/stats IntelligenceStats" $r

# 5.3 Entity - hosts
$r = Invoke-KbApi -Method GET -Path "/intelligence/machine/entities/hosts" -Token $global:token -TestName "EntityHosts"
Assert-Test "5.3 GET /intelligence/machine/entities/hosts EntityHosts" $r

# 5.4 Entity - services
$r = Invoke-KbApi -Method GET -Path "/intelligence/machine/entities/services" -Token $global:token -TestName "EntityServices"
Assert-Test "5.4 GET /intelligence/machine/entities/services EntityServices" $r

# 5.5 Entity - commands
$r = Invoke-KbApi -Method GET -Path "/intelligence/machine/entities/commands" -Token $global:token -TestName "EntityCommands"
Assert-Test "5.5 GET /intelligence/machine/entities/commands EntityCommands" $r

# 5.6 Entity - timelines
$r = Invoke-KbApi -Method GET -Path "/intelligence/machine/entities/timelines" -Token $global:token -TestName "EntityTimelines"
Assert-Test "5.6 GET /intelligence/machine/entities/timelines EntityTimelines" $r

# 5.7 Knowledge search
$r = Invoke-KbApi -Method POST -Path "/intelligence/machine/search" -Body @{query="test";page=1;size=10} -Token $global:token -TestName "IntelligenceSearch"
Assert-Test "5.7 POST /intelligence/machine/search IntelligenceSearch" $r

# 5.8 Get non-existent doc meta (expect 404)
$r = Invoke-KbApi -Method GET -Path "/intelligence/machine/docs/999999/meta" -Token $global:token -TestName "GetNonExistentDocMeta"
Assert-Test "5.8 GET /intelligence/machine/docs/999999/meta GetNonExistentDocMeta (expect 404)" $r -ExpectedCode 404

# 5.9 Get non-existent doc entities
$r = Invoke-KbApi -Method GET -Path "/intelligence/machine/docs/999999/entities" -Token $global:token -TestName "GetNonExistentDocEntities"
Assert-Test "5.9 GET /intelligence/machine/docs/999999/entities GetNonExistentDocEntities" $r

# 5.10 Get non-existent doc content (expect 404)
$r = Invoke-KbApi -Method GET -Path "/intelligence/machine/docs/999999/content" -Token $global:token -TestName "GetNonExistentDocContent"
Assert-Test "5.10 GET /intelligence/machine/docs/999999/content GetNonExistentDocContent (expect 404)" $r -ExpectedCode 404

# 5.11 Import status
$r = Invoke-KbApi -Method GET -Path "/intelligence/import/status" -Token $global:token -TestName "ImportStatus"
Assert-Test "5.11 GET /intelligence/import/status ImportStatus" $r

# 5.12 Path import (invalid path)
$r = Invoke-KbApi -Method POST -Path "/intelligence/import/path" -Body @{path="/nonexistent/path"} -Token $global:token -TestName "PathImportInvalid"
Assert-Test "5.12 POST /intelligence/import/path PathImportInvalid" $r

# ============================================================
# 6. Gateway Layer Tests
# ============================================================
Write-Host "========== 6. Gateway Layer Tests ==========" -ForegroundColor Cyan

# 6.1 Unauthenticated access
$r = Invoke-KbApi -Method GET -Path "/user/profile" -TestName "UnauthenticatedAccess"
Assert-Test "6.1 GET /user/profile UnauthenticatedAccess (expect 401)" $r -ExpectedCode 401

# 6.2 Invalid token
$r = Invoke-KbApi -Method GET -Path "/user/profile" -Token "invalid.token.here" -TestName "InvalidToken"
Assert-Test "6.2 GET /user/profile InvalidToken (expect 401)" $r -ExpectedCode 401

# 6.3 Wrong password login (BusinessException default code=400)
$r = Invoke-KbApi -Method POST -Path "/auth/login" -Body @{username="admin";password="wrongpassword"} -TestName "WrongPasswordLogin"
Assert-Test "6.3 POST /auth/login WrongPasswordLogin (expect 400)" $r -ExpectedCode 400

# 6.4 Non-existent user login (BusinessException default code=400)
$r = Invoke-KbApi -Method POST -Path "/auth/login" -Body @{username="nonexistent";password="test"} -TestName "NonExistentUserLogin"
Assert-Test "6.4 POST /auth/login NonExistentUserLogin (expect 400)" $r -ExpectedCode 400

# ============================================================
# Test Report
# ============================================================
Write-Host "========== E2E Test Report ==========" -ForegroundColor Yellow

$passedCount = 0
$failedCount = 0
foreach ($r in $results) {
    if ($r.Status -eq "PASS") { $passedCount++ } else { $failedCount++ }
}
$total = $results.Count
$passRate = 0
if ($total -gt 0) { $passRate = [math]::Round($passedCount / $total * 100, 2) }

$summaryColor = "Red"
if ($passRate -ge 95) { $summaryColor = "Green" } elseif ($passRate -ge 80) { $summaryColor = "Yellow" }
Write-Host ("Total: {0} | Passed: {1} | Failed: {2} | PassRate: {3}%" -f $total, $passedCount, $failedCount, $passRate) -ForegroundColor $summaryColor

# Detail table
$results | Format-Table -AutoSize -Wrap

# Failed cases
$failedTests = $results | Where-Object { $_.Status -eq "FAIL" }
if ($failedTests) {
    Write-Host "========== Failed Cases Detail ==========" -ForegroundColor Red
    foreach ($f in $failedTests) {
        Write-Host ("[FAIL] {0}" -f $f.Test) -ForegroundColor Red
        Write-Host ("   Code: {0} | TraceId: {1}" -f $f.Code, $f.TraceId)
        Write-Host ("   Detail: {0}" -f $f.Detail)
    }
}

# Save CSV report
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$reportPath = "D:\huliang\java\ideaworkspace\devtools\mykng\test-output\e2e\report_$timestamp.csv"
$dir = Split-Path $reportPath
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
$results | Export-Csv -Path $reportPath -NoTypeInformation -Encoding UTF8
Write-Host ("Report saved to: {0}" -f $reportPath) -ForegroundColor Cyan

# Save summary JSON
$summaryPath = "D:\huliang\java\ideaworkspace\devtools\mykng\test-output\e2e\summary_$timestamp.json"
$summary = @{
    total = $total
    passed = $passedCount
    failed = $failedCount
    passRate = $passRate
    timestamp = $timestamp
    baseUrl = $BaseUrl
} | ConvertTo-Json -Compress
$summary | Out-File $summaryPath -Encoding utf8
Write-Host ("Summary saved to: {0}" -f $summaryPath) -ForegroundColor Cyan

# Exit code
if ($failedCount -gt 0) { exit 1 } else { exit 0 }
