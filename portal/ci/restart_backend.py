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
    print(f'$ {cmd[:100]}...' if len(cmd) > 100 else f'$ {cmd}')
    stdin, stdout, stderr = client.exec_command(cmd)
    out = stdout.read().decode('utf-8', errors='ignore')
    err = stderr.read().decode('utf-8', errors='ignore')
    if out.strip():
        print(out.strip()[-500:])
    if err.strip():
        print('[ERR]', err.strip()[-300:])
    return out.strip()

print('=== 停止旧进程 ===')
run("pkill -f 'portal-server.jar' 2>/dev/null; sleep 2")

print('\n=== 用正确的数据库配置启动 ===')
start_cmd = (
    f'nohup {JAVA_PATH} -Xms128m -Xmx256m -XX:+UseG1GC -Dfile.encoding=UTF-8 '
    f'-jar {REMOTE_BACKEND_JAR} '
    f'--spring.profiles.active=prod '
    f'--spring.datasource.url="jdbc:mysql://192.168.31.77:3306/tools?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false" '
    f'--spring.datasource.username=tools '
    f'--spring.datasource.password=toolsmarschat '
    f'> {REMOTE_BACKEND_DIR}/portal-server.log 2>&1 &'
)
run(start_cmd)

print('\n等待 20 秒...')
time.sleep(20)

print('\n=== 检查启动状态 ===')
out = run(f"ss -tlnp | grep ':{BACKEND_PORT} ' | head -1")
if out:
    print('✅ 端口已监听')
else:
    print('⚠️  端口未监听')

print('\n=== 最后 30 行日志 ===')
out = run(f'tail -30 {REMOTE_BACKEND_DIR}/portal-server.log')
print(out[-1000:])

client.close()
