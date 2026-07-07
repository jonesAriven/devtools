# 连接腾讯云2号并部署 Jenkins Nginx 配置
$ErrorActionPreference = "Stop"

$password = ConvertTo-SecureString "Hwx@1120930" -AsPlainText -Force
$credential = New-Object System.Management.Automation.PSCredential("root", $password)

Write-Host "[1/4] 连接腾讯云2号 (1.117.70.30)..." -ForegroundColor Cyan
$session = New-SSHSession -ComputerName "1.117.70.30" -Credential $credential -AcceptKey
if (-not $session) { Write-Host "连接失败!" -ForegroundColor Red; exit 1 }
Write-Host "连接成功! SessionId: $($session.SessionId)" -ForegroundColor Green

$sid = $session.SessionId

Write-Host "`n[2/4] 检查环境..." -ForegroundColor Cyan
$r = Invoke-SSHCommand -SessionId $sid -Command "hostname && whoami && nginx -v 2>&1"
Write-Host $r.Output

Write-Host "`n[3/4] 检查证书和Nginx配置目录..." -ForegroundColor Cyan
$r = Invoke-SSHCommand -SessionId $sid -Command "ls /etc/nginx/conf.d/ && echo '---' && ls /etc/letsencrypt/live/ 2>/dev/null || echo '无证书目录'"
Write-Host $r.Output

Remove-SSHSession -SessionId $sid
Write-Host "`n[Done] 断开连接" -ForegroundColor Green
