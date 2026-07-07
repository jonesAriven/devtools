import paramiko
import time

HOST = '100.110.114.16'
USER = 'root'
PASSWORD = 'Hwx@1120930'
PORT = 22

JAVA_PATH = '/software/devTools/nexus/nexus-3.92.2-01/jdk/temurin_21.0.9_10_linux_x86_64/jdk-21.0.9+10/bin/java'
REMOTE_BACKEND_DIR = '/opt/portal-server'
REMOTE_BACKEND_JAR = f'{REMOTE_BACKEND_DIR}/portal-server.jar'
BACKEND_PORT = 8087

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(HOST, PORT, USER, PASSWORD, timeout=30)

def run(cmd):
    print(f'$ {cmd}')
    stdin, stdout, stderr = client.exec_command(cmd)
    out = stdout.read().decode('utf-8', errors='ignore')
    err = stderr.read().decode('utf-8', errors='ignore')
    if out.strip():
        print(out.strip())
    if err.strip():
        print('[ERR]', err.strip())
    return out.strip()

print('=== 检查 Java ===')
run(f'{JAVA_PATH} -version 2>&1 | head -3')

print('\n=== 清理旧进程 ===')
run(f"pkill -f 'portal-server.jar' 2>/dev/null; sleep 2")
run(f"fuser -k -9 {BACKEND_PORT}/tcp 2>/dev/null; sleep 1")

print('\n=== 启动后端 ===')
start_cmd = (
    f'nohup {JAVA_PATH} -Xms128m -Xmx256m -XX:+UseG1GC -Dfile.encoding=UTF-8 '
    f'-jar {REMOTE_BACKEND_JAR} '
    f'--spring.profiles.active=prod '
    f'> {REMOTE_BACKEND_DIR}/portal-server.log 2>&1 &'
)
run(start_cmd)

print('\n等待 15 秒...')
time.sleep(15)

print('\n=== 检查启动状态 ===')
out = run(f"ss -tlnp | grep ':{BACKEND_PORT} ' | head -1")
if out:
    print('✅ 后端启动成功！')
else:
    print('⚠️  端口未监听，查看日志:')
    run(f'tail -50 {REMOTE_BACKEND_DIR}/portal-server.log')

client.close()
