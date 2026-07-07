import paramiko
import time

HOST = '100.93.36.113'
USER = 'root'
PASSWORD = 'Hwx@1120930'
PORT = 22

REMOTE_BACKEND_DIR = '/opt/portal-server'
REMOTE_BACKEND_JAR = f'{REMOTE_BACKEND_DIR}/portal-server.jar'
BACKEND_PORT = 8087

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

def run(cmd):
    stdin, stdout, stderr = client.exec_command(cmd)
    out = stdout.read().decode('utf-8', errors='ignore')
    err = stderr.read().decode('utf-8', errors='ignore')
    return out.strip(), err.strip()

try:
    client.connect(HOST, PORT, USER, PASSWORD, timeout=15)

    print('=== 检查端口占用 ===')
    out, _ = run(f"ss -tlnp | grep ':{BACKEND_PORT} '")
    print(out if out else '端口空闲')

    print('\n=== 清理所有占用 8087 的进程 ===')
    run(f"fuser -k -9 {BACKEND_PORT}/tcp 2>/dev/null")
    time.sleep(2)
    out, _ = run(f"ss -tlnp | grep ':{BACKEND_PORT} '")
    print('清理后:', out if out else '端口空闲')

    print('\n=== 完整错误日志 (最后50行) ===')
    out, _ = run(f'tail -50 {REMOTE_BACKEND_DIR}/portal-server.log')
    print(out)

    print('\n=== 重新启动后端 ===')
    start_cmd = (
        f'nohup /usr/bin/java -Xms128m -Xmx256m -XX:+UseG1GC -Dfile.encoding=UTF-8 '
        f'-jar {REMOTE_BACKEND_JAR} '
        f'--spring.profiles.active=prod '
        f'--spring.config.location=file:{REMOTE_BACKEND_DIR}/application-prod.yml '
        f'> {REMOTE_BACKEND_DIR}/portal-server.log 2>&1 &'
    )
    run(start_cmd)
    print('启动命令已执行，等待 20 秒...')
    time.sleep(20)

    print('\n=== 检查启动状态 ===')
    out, _ = run(f"ss -tlnp | grep ':{BACKEND_PORT} ' | head -1")
    if out:
        print('✅ 后端启动成功！')
    else:
        print('❌ 启动失败，查看日志:')
        out, _ = run(f'tail -30 {REMOTE_BACKEND_DIR}/portal-server.log')
        print(out[-1000:])

finally:
    client.close()
