#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修复腾讯云2号 Nginx 配置并添加 /ops/
"""
import paramiko

# mykng-debain（跳板）
JUMP_HOST = "192.168.31.105"
JUMP_PORT = 22
JUMP_USER = "root"
JUMP_PASS = "root"

# 腾讯云2号
TX2_HOST = "100.110.114.16"
TX2_PASS = "Hwx@1120930"

CONF_FILE = '/etc/nginx/sites-enabled/main.marschat.online'

OPS_BLOCK = """
    # kb-ops 运维管理平台（转发到 mykng-debain Nginx）
    location /ops/ {
        proxy_pass http://100.93.36.113:80/ops/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
    }

    location /ops-api/ {
        proxy_pass http://100.93.36.113:80/ops-api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
    }
"""

INSERT_PY = r"""
import glob
conf_file = '/etc/nginx/sites-enabled/main.marschat.online'
with open(conf_file) as f:
    content = f.read()

# 先清理掉错误的配置（如果有的话）
if 'System.Management.Automation' in content:
    print('检测到损坏配置，从备份恢复...')
    backups = sorted(glob.glob(conf_file + '.bak.*'))
    if backups:
        bak = backups[0]
        with open(bak) as f:
            content = f.read()
        print('  已从 ' + bak + ' 恢复')
        with open(conf_file, 'w') as f:
            f.write(content)
    else:
        print('  无备份')

if 'location /ops/' in content and 'System.Management.Automation' not in content:
    print('ALREADY_EXISTS')
else:
    marker = '    location = /portal {'
    if marker in content:
        with open('/tmp/ops_block_correct.txt') as f:
            block = f.read()
        new_content = content.replace(marker, block + '\n' + marker, 1)
        with open(conf_file, 'w') as f:
            f.write(new_content)
        print('INSERT_OK')
    else:
        print('MARKER_NOT_FOUND')
        print('配置中的 location 行:')
        for i, line in enumerate(content.split('\n')):
            if 'location' in line.strip():
                print('  %d: %s' % (i, line.strip()))
"""


def run_jump(jump, cmd, timeout=30):
    stdin, stdout, stderr = jump.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    return out, err


def main():
    print("连接跳板机 mykng-debain...")
    jump = paramiko.SSHClient()
    jump.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    jump.connect(JUMP_HOST, port=JUMP_PORT, username=JUMP_USER, password=JUMP_PASS, timeout=15)

    # 1. 先从备份恢复（如果配置损坏）
    print("\n[1] 恢复配置（如果损坏）...")
    restore_cmd = (
        f"sshpass -p '{TX2_PASS}' ssh -o StrictHostKeyChecking=no root@{TX2_HOST} "
        f"'python3 -c \"import glob; f=\\\'{CONF_FILE}\\\'; "
        f"bs=sorted(glob.glob(f+\\\'.bak.*\\\')); "
        f"print(\\\'backups:\\\', bs); "
        f"open(f).read()\" 2>&1 | head -20'"
    )
    out, err = run_jump(jump, restore_cmd)
    print(out)

    # 2. 写正确的配置块到跳板机
    print("\n[2] 准备配置块...")
    sftp = jump.open_sftp()
    with sftp.file("/tmp/ops_block_correct.txt", "w") as f:
        f.write(OPS_BLOCK)
    sftp.close()
    print("  配置块已写入 /tmp/ops_block_correct.txt")

    # 3. 上传到腾讯云2号
    print("\n[3] 上传配置块到腾讯云2号...")
    scp_cmd = f"sshpass -p '{TX2_PASS}' scp -o StrictHostKeyChecking=no /tmp/ops_block_correct.txt root@{TX2_HOST}:/tmp/ops_block_correct.txt"
    run_jump(jump, scp_cmd)
    print("  上传完成")

    # 4. 写 Python 插入脚本到跳板机
    sftp = jump.open_sftp()
    with sftp.file("/tmp/insert_ops_fix.py", "w") as f:
        f.write(INSERT_PY)
    sftp.close()

    # 5. 上传到腾讯云2号并执行
    print("\n[4] 执行插入...")
    scp2_cmd = f"sshpass -p '{TX2_PASS}' scp -o StrictHostKeyChecking=no /tmp/insert_ops_fix.py root@{TX2_HOST}:/tmp/insert_ops_fix.py"
    run_jump(jump, scp2_cmd)

    exec_cmd = f"sshpass -p '{TX2_PASS}' ssh -o StrictHostKeyChecking=no root@{TX2_HOST} 'python3 /tmp/insert_ops_fix.py'"
    out, err = run_jump(jump, exec_cmd)
    print(f"  结果: {out.strip()}")
    if err:
        print(f"  [ERR] {err}")

    # 6. 验证配置
    print("\n[5] 验证 Nginx 配置...")
    test_cmd = f"sshpass -p '{TX2_PASS}' ssh -o StrictHostKeyChecking=no root@{TX2_HOST} 'nginx -t'"
    out, err = run_jump(jump, test_cmd)
    if out:
        print(out)
    if err:
        print(err)

    success = "test is successful" in err or "test is successful" in out

    if success:
        print("\n[6] 重载 Nginx...")
        reload_cmd = f"sshpass -p '{TX2_PASS}' ssh -o StrictHostKeyChecking=no root@{TX2_HOST} 'nginx -s reload'"
        out, err = run_jump(jump, reload_cmd)
        if err:
            print(err)
        print("  Nginx 已重载")

        # 7. 显示配置中的 /ops/ 部分
        print("\n[7] 配置中的 /ops/ 部分:")
        show_cmd = f"sshpass -p '{TX2_PASS}' ssh -o StrictHostKeyChecking=no root@{TX2_HOST} 'grep -A 10 \"location /ops/\" {CONF_FILE}'"
        out, err = run_jump(jump, show_cmd)
        print(out)
    else:
        print("\n[ERROR] 配置验证失败，请检查！")
        # 显示当前配置帮助调试
        show_cmd = f"sshpass -p '{TX2_PASS}' ssh -o StrictHostKeyChecking=no root@{TX2_HOST} 'cat {CONF_FILE}'"
        out, err = run_jump(jump, show_cmd)
        print("\n当前配置文件内容:")
        print(out[:2000])

    jump.close()
    print("\n完成！")


if __name__ == "__main__":
    main()
