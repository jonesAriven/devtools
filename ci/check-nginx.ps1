$host = "1.117.70.30"
$user = "root"
$pass = "Hwx@1120930"

# 使用 plink 或期望 SSH 密钥方式
# 先尝试用 Expect 风格的 SSH，或者直接生成一个临时脚本
# Windows 下用 plink (PuTTY) 或者通过 ssh 的 stdin 传密码

# 方案: 用 ssh 直接连，看看能不能用 key
$testKey = ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 -o BatchMode=yes ${user}@${host} "echo OK" 2>&1
Write-Output "SSH Key test: $testKey"

if ($testKey -match "Permission denied") {
    Write-Output "Key auth failed, trying password via plink..."
    
    # 检查 plink 是否存在
    $plink = Get-Command plink -ErrorAction SilentlyContinue
    if ($plink) {
        $env:SSHPASS = $pass
        echo y | plink -ssh ${user}@${host} -pw $pass "grep -rn 'activecode\|18080\|18081\|tools.marschat' /etc/nginx/ --include='*.conf' 2>/dev/null; echo '===SITES==='; ls /etc/nginx/sites-enabled/ 2>/dev/null; ls /etc/nginx/conf.d/ 2>/dev/null" 2>&1
    } else {
        Write-Output "plink not found, trying ssh with expect-like approach..."
        # 尝试用 PowerShell 的 Process + stdin
        $process = New-Object System.Diagnostics.Process
        $process.StartInfo.FileName = "ssh"
        $process.StartInfo.Arguments = "-o StrictHostKeyChecking=no ${user}@${host}"
        $process.StartInfo.UseShellExecute = $false
        $process.StartInfo.RedirectStandardInput = $true
        $process.StartInfo.RedirectStandardOutput = $true
        $process.StartInfo.RedirectStandardError = $true
        
        $process.Start() | Out-Null
        
        # 发送命令
        $cmd = "grep -rn 'activecode|18080|18081|tools.marschat' /etc/nginx/ --include='*.conf' 2>/dev/null; echo '===SITES==='; ls /etc/nginx/sites-enabled/ 2>/dev/null; ls /etc/nginx/conf.d/ 2>/dev/null; exit`n"
        $process.StandardInput.Write($cmd)
        $process.StandardInput.Close()
        
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit(15000)
        
        Write-Output "STDOUT: $stdout"
        Write-Output "STDERR: $stderr"
    }
}
