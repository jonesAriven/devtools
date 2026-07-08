$token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0eXBlIjoidXNlciIsInVzZXItaWQiOiIxIn0.471qau5gcvZNQnxV4KfpE5VMnZ_9Q16IzNMESLfdmE4"
$headers = @{ "Authorization" = "Bearer $token" }
$body = '{"branch":"dev"}'

try {
    $r = Invoke-RestMethod -Uri "https://woodci.marschat.online/api/repos/1/pipelines" -Method POST -ContentType "application/json" -Headers $headers -Body $body
    Write-Output "SUCCESS: Pipeline #$($r.number) triggered!"
    Write-Output "Commit: $($r.commit)"
    Write-Output "Status: $($r.status)"
} catch {
    Write-Output "ERROR: $($_.Exception.Response.StatusCode.value__) $($_.Exception.Message)"
}
