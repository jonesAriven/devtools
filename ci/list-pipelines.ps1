# List Woodpecker pipelines via mykng
$apiCmd = 'ssh -o StrictHostKeyChecking=no -p 3385 root@120.26.66.182 "curl -s https://woodci.marschat.online/api/repos/1/pipelines?per_page=10"'
Write-Output ">>> Querying pipeline history..."
$result = Invoke-Expression $apiCmd 2>&1
Write-Output $result
