# Check Pipeline #31 steps
$cmd = 'ssh -o StrictHostKeyChecking=no -p 3385 root@120.26.66.182 "curl -s https://woodci.marschat.online/api/repos/1/pipelines/31/proc"'
Write-Output ">>> Pipeline #31 steps:"
$result = Invoke-Expression $cmd 2>&1
Write-Output $result
