#!/bin/bash
# kb-knowledge 诊断脚本（只读检查，不修改任何代码/数据）
set +e
SEP="=============================================="

echo "$SEP"
echo "STEP 1: 容器状态与启动命令"
echo "$SEP"
docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}" 2>&1 | grep -iE "kb|NAMES"
echo "--- kb-knowledge inspect (Cmd/Entrypoint/Image) ---"
docker inspect kb-knowledge --format 'Cmd={{.Config.Cmd}}|Entrypoint={{.Config.Entrypoint}}|Image={{.Config.Image}}' 2>&1
echo "--- 容器创建/启动时间 ---"
docker inspect kb-knowledge --format 'Created={{.Created}}|StartedAt={{.State.StartedAt}}|RestartCount={{.RestartCount}}' 2>&1

echo ""
echo "$SEP"
echo "STEP 2: 容器内 JAR 时间戳"
echo "$SEP"
echo "--- /app.jar ---"
docker exec kb-knowledge ls -la /app.jar 2>&1
echo "--- /app/ 目录 ---"
docker exec kb-knowledge ls -la /app/ 2>&1
echo "--- 容器内查找所有 jar (maxdepth 4) ---"
docker exec kb-knowledge find / -maxdepth 4 -name '*.jar' 2>/dev/null | head -20

echo ""
echo "$SEP"
echo "STEP 3: 反编译检查 DocServiceImpl.class 是否含 MongoDB 查询代码"
echo "$SEP"
docker exec kb-knowledge sh -c 'cd /tmp && rm -rf BOOT-INF && unzip -o /app.jar "BOOT-INF/classes/com/kb/knowledge/service/impl/DocServiceImpl.class" >/dev/null 2>&1; ls -la BOOT-INF/classes/com/kb/knowledge/service/impl/DocServiceImpl.class 2>&1'
echo "--- 关键字符串检索 (findByDocId/Mongo/content/isCurrent/getById) ---"
docker exec kb-knowledge sh -c 'cd /tmp && strings BOOT-INF/classes/com/kb/knowledge/service/impl/DocServiceImpl.class 2>/dev/null | grep -iE "findByDocId|docContent|MongoTemplate|MongoRepository|isCurrent|getById|contentRepository|ContentService" | sort -u'
echo "--- 同时检查 DocController.class ---"
docker exec kb-knowledge sh -c 'cd /tmp && unzip -o /app.jar "BOOT-INF/classes/com/kb/knowledge/controller/DocController.class" >/dev/null 2>&1; strings BOOT-INF/classes/com/kb/knowledge/controller/DocController.class 2>/dev/null | grep -iE "getById|content|mongo" | sort -u | head -20'
echo "--- javap 方法签名 (若可用) ---"
docker exec kb-knowledge sh -c 'which javap >/dev/null 2>&1 && (cd /tmp && javap -p BOOT-INF/classes/com/kb/knowledge/service/impl/DocServiceImpl.class 2>/dev/null | grep -iE "getById|content|mongo|findByDocId") || echo "javap 不可用，仅 strings 结果"'

echo ""
echo "$SEP"
echo "STEP 4: 登录获取 token"
echo "$SEP"
LOGIN_RESP=$(curl -s -X POST http://localhost:8090/kb/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' 2>&1)
echo "登录返回原文: $LOGIN_RESP"
# 多种 token 提取方式
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json
try:
    d=json.load(sys.stdin)
    t=(d.get('data') or {}).get('token') or d.get('token') or (d.get('data') or {}).get('accessToken') or d.get('accessToken') or ''
    print(t)
except Exception as e:
    print('ERR:'+str(e))" 2>/dev/null)
if [ -z "$TOKEN" ] || echo "$TOKEN" | grep -q '^ERR'; then
    TOKEN=$(echo "$LOGIN_RESP" | grep -oE '"(token|accessToken)":"[^"]+"' | head -1 | sed -E 's/.*:"([^"]+)"/\1/')
fi
echo "提取的 TOKEN (前60字符): ${TOKEN:0:60}"
echo "TOKEN 长度: ${#TOKEN}"

echo ""
echo "$SEP"
echo "STEP 5: 调用 GET /doc/7"
echo "$SEP"
echo "--- 不带 token (预期 401) ---"
curl -s -o - -w "\n[HTTP_CODE:%{http_code}]" http://localhost:8090/kb/api/doc/7 2>&1
echo ""
echo "--- 带 token ---"
if [ -n "$TOKEN" ]; then
    DOC_RESP=$(curl -s -o - -w "\n[HTTP_CODE:%{http_code}]" http://localhost:8090/kb/api/doc/7 -H "Authorization: Bearer $TOKEN" 2>&1)
    echo "$DOC_RESP"
    echo ""
    echo "--- content 字段专项检查 ---"
    echo "$DOC_RESP" | python3 -c "import sys,json,re
raw=sys.stdin.read()
m=re.search(r'\{.*\}', raw, re.S)
if not m: print('无JSON'); sys.exit()
try:
    d=json.loads(m.group(0))
    data=d.get('data') or d
    c=data.get('content') if isinstance(data,dict) else None
    print('content 字段存在:', c is not None)
    print('content 值类型:', type(c).__name__)
    print('content 长度:', len(c) if c else 0)
    print('content 前200字符:', repr(c)[:200] if c else '(空)')
except Exception as e: print('解析失败:'+str(e))" 2>&1
else
    echo "TOKEN 为空，跳过带 token 调用"
fi

echo ""
echo "$SEP"
echo "STEP 6: kb-knowledge 日志 (MongoDB 相关)"
echo "$SEP"
docker logs kb-knowledge --tail 200 2>&1 | grep -iE "mongo|docContent|findByDocId|content|error|exception|warn" | tail -50
echo "--- 最近 20 行原始日志 ---"
docker logs kb-knowledge --tail 20 2>&1

echo ""
echo "$SEP"
echo "STEP 7: mykng 上源码/已构建 JAR"
echo "$SEP"
ls -la /root/kb-deploy/ 2>/dev/null || echo "/root/kb-deploy/ 不存在"
ls -la /root/kb-source/ 2>/dev/null || echo "/root/kb-source/ 不存在"
find /root -maxdepth 4 -name 'kb-knowledge*.jar' 2>/dev/null
echo "--- docker-compose 文件 ---"
find /root -maxdepth 3 \( -name 'docker-compose*.yml' -o -name 'compose*.yml' \) 2>/dev/null | head -5

echo ""
echo "$SEP"
echo "STEP 8: 容器挂载与镜像信息"
echo "$SEP"
echo "--- Mounts ---"
docker inspect kb-knowledge --format '{{range .Mounts}}{{.Type}}: {{.Source}} -> {{.Destination}} (RW={{.RW}}){{println}}{{end}}' 2>&1
IMG_ID=$(docker inspect kb-knowledge --format '{{.Image}}' 2>&1)
echo "镜像 ID: $IMG_ID"
docker image inspect "$IMG_ID" --format 'Created={{.Created}}|Size={{.Size}}|Tags={{.RepoTags}}' 2>&1

echo ""
echo "$SEP"
echo "附加: Mongo 数据库检查 (确认 docId=7 数据存在)"
echo "$SEP"
MONGO_C=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -iE 'mongo' | head -1)
echo "Mongo 容器: ${MONGO_C:-未找到}"
if [ -n "$MONGO_C" ]; then
    echo "--- 数据库列表 ---"
    docker exec "$MONGO_C" mongosh --quiet --eval 'db.adminCommand({listDatabases:1}).databases.forEach(d=>print(d.name))' 2>/dev/null || docker exec "$MONGO_C" mongo --quiet --eval 'db.getMongo().getDBNames().forEach(print)' 2>/dev/null
fi

echo ""
echo "$SEP"
echo "DONE"
echo "$SEP"
