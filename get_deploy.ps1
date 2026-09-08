$host = '192.168.31.31.182'
$user = 'root'
$keyPath = 'D:\huliang\java\ideawworkspace\devtools\ssh_key'
$key = Get-Content $keyPath -Raw

$sess = New-Object System.Management.Automation.SshSession()
$sess.HostName = $host
$sess.Credentials = New-Object System.Management.Automation.PSCredential($user, $key)
$sess.Connect() | Out-Null

$sftp = $sess.CreateSftpClient()
$sftp.Connect($host) | Out-Null
try {
    $o = $ftp.ReadFile('/mnt/shared/woodScript/cd/deploy-kb-ops-web.sh')
    Write-Host $o
} catch {
    Write-Host "Error: $_"
}
$sess.Close()
$sftp.Dispose()
