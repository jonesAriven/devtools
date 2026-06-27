$ErrorActionPreference = 'Continue'
$javap = 'D:\huliang\software\Java\jdk-21.0.11\bin\javap.exe'
$work = 'd:\huliang\java\ideaworkspace\devtools\mykng\tests\cmp'
New-Item -ItemType Directory -Force -Path $work | Out-Null

# 1. 提取本地(22:48新版) DocServiceImpl.class
& 'D:\huliang\software\Python\Python313\python.exe' -c "import zipfile; z=zipfile.ZipFile(r'd:\huliang\java\ideaworkspace\devtools\mykng\kb-knowledge\target\kb-knowledge.jar'); z.extract('BOOT-INF/classes/com/kb/knowledge/service/impl/DocServiceImpl.class', r'$work\new2248'); print('extracted new2248')"

# 2. scp 运行中(21:20旧版) DocServiceImpl.class
scp -o StrictHostKeyChecking=no root@100.93.36.113:/tmp/R1/BOOT-INF/classes/com/kb/knowledge/service/impl/DocServiceImpl.class "$work\old2120.class" 2>&1

# 3. javap 反汇编两个class，输出到文件
$newClass = "$work\new2248\BOOT-INF\classes\com\kb\knowledge\service\impl\DocServiceImpl.class"
Write-Output "=== javap 新版(22:48) ==="
& $javap -c -p $newClass 2>&1 | Out-File -FilePath "$work\new2248.txt" -Encoding utf8
Write-Output "新版字节码行数: $((Get-Content "$work\new2248.txt").Count)"

Write-Output "=== javap 旧版(21:20运行中) ==="
& $javap -c -p "$work\old2120.class" 2>&1 | Out-File -FilePath "$work\old2120.txt" -Encoding utf8
Write-Output "旧版字节码行数: $((Get-Content "$work\old2120.txt").Count)"

# 4. 提取 getById 方法块
Write-Output ""
Write-Output "=== 新版 getById 方法签名 ==="
Select-String -Path "$work\new2248.txt" -Pattern 'getById' | Select-Object -First 5 | ForEach-Object { $_.Line }
Write-Output "=== 旧版 getById 方法签名 ==="
Select-String -Path "$work\old2120.txt" -Pattern 'getById' | Select-Object -First 5 | ForEach-Object { $_.Line }

Write-Output ""
Write-Output "=== 新版 findByDocId 调用位置 ==="
Select-String -Path "$work\new2248.txt" -Pattern 'findByDocId' | ForEach-Object { "L$($_.LineNumber): $($_.Line)" }
Write-Output "=== 旧版 findByDocId 调用位置 ==="
Select-String -Path "$work\old2120.txt" -Pattern 'findByDocId' | ForEach-Object { "L$($_.LineNumber): $($_.Line)" }

Write-Output ""
Write-Output "DONE - 字节码文件: $work\new2248.txt 和 $work\old2120.txt"
