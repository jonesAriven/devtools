import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('120.26.66.182', port=3385, username='root', password='root', timeout=15)

def check_http(url):
    stdin, stdout, stderr = ssh.exec_command(f"curl -s --max-time 5 -o /dev/null -w '%{{http_code}}' {url}")
    return stdout.read().decode().strip()

print("=" * 60)
print("  服务器内部访问地址")
print("=" * 60)

checks = [
    ("网关模块状态", "http://localhost:8090/kb/api/system/modules"),
    ("Swagger UI 聚合页", "http://localhost:8090/swagger-ui.html"),
    ("认证服务 API Docs", "http://localhost:8090/kb/api/auth/v3/api-docs"),
    ("文件服务 API Docs", "http://localhost:8090/kb/api/file/v3/api-docs"),
    ("知识库服务 API Docs", "http://localhost:8090/kb/api/knowledge/v3/api-docs"),
    ("知识引擎 API Docs", "http://localhost:8090/kb/api/intelligence/v3/api-docs"),
    ("Nacos 控制台", "http://localhost:8848/nacos/"),
    ("MinIO 控制台", "http://localhost:9001/"),
    ("MeiliSearch 健康", "http://localhost:7700/health"),
]

for name, url in checks:
    code = check_http(url)
    status = "OK" if code.startswith("2") or code.startswith("3") else "FAIL"
    print(f"  [{status}] {name}")
    print(f"         {url}  ({code})")
    print()

ssh.close()
