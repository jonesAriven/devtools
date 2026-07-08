# Step 1: SSH to mykng, then to debian
$step1 = 'ssh -o StrictHostKeyChecking=no -p 3385 root@120.26.66.182 "ssh -o StrictHostKeyChecking=no root@192.168.31.182 docker ps -a --filter name=activecode"'
Write-Output ">>> Step 1: Check container"
Invoke-Expression $step1 2>&1

$step2 = 'ssh -o StrictHostKeyChecking=no -p 3385 root@120.26.66.182 "ssh -o StrictHostKeyChecking=no root@192.168.31.182 ss -tlnp | grep -E ""18080|18081"""'
Write-Output "`n>>> Step 2: Check port listen"
Invoke-Expression $step2 2>&1

$step3 = 'ssh -o StrictHostKeyChecking=no -p 3385 root@120.26.66.182 "ssh -o StrictHostKeyChecking=no root@192.168.31.182 find /etc /usr/local /root /opt -maxdepth 4 -name frpc.ini 2>/dev/null"'
Write-Output "`n>>> Step 3: Find frpc.ini location"
Invoke-Expression $step3 2>&1
