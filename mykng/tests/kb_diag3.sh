#!/bin/bash
# kb-knowledge 诊断脚本 第三轮：Mongo认证查询 + 应用Mongo配置 + 全日志
set +e
SEP="=============================================="

echo "$SEP"
echo "G. 获取 Mongo 凭据 (.env / docker-compose)"
echo "$SEP"
echo "--- .env 中 MONGO 相关 ---"
grep -iE 'mongo' /root/kb-deploy/.env 2>&1
echo "--- docker-compose kb-mongo 服务段 ---"
grep -nA 18 'kb-mongo:' /root/kb-deploy/docker-compose.yml 2>&1 | head -22

echo ""
echo "$SEP"
echo "H. kb-knowledge 应用 Mongo 配置 (容器内 application.yml)"
echo "$SEP"
echo "--- 容器内 application.yml 路径 ---"
docker exec kb-knowledge find /app -name 'application*.yml' -o -name 'application*.yaml' -o -name 'application*.properties' 2>/dev/null | head -10
echo "--- 从运行JAR提取 application.yml ---"
docker exec kb-knowledge sh -c 'cd /tmp && rm -rf CFG && mkdir CFG && cd CFG && unzip -o /app/kb-knowledge.jar "BOOT-INF/classes/application*.yml" >/dev/null 2>&1; find . -name "application*.yml"' 
echo "--- application.yml 中 mongo/spring.data.mongodb 配置 ---"
docker exec kb-knowledge sh -c 'cd /tmp/CFG && for f in $(find . -name "application*.yml"); do echo "=== $f ==="; cat "$f" | grep -iE "mongo|data:|uri|host|port|database|username|password|auth|authentication" -A2 -B1; done' 2>&1
echo "--- application-prod.yml (若有) ---"
docker exec kb-knowledge sh -c 'cd /tmp/CFG && cat application-prod.yml 2>/dev/null | grep -iE "mongo|uri|host|port|database|username|password|auth" -A2 -B1' 2>&1

echo ""
echo "$SEP"
echo "I. Mongo 带认证查询 docId=7"
echo "$SEP"
# 从 .env 读凭据
MUSER=$(grep -iE 'MONGO_INITDB_ROOT_USERNAME|MONGO_USER' /root/kb-deploy/.env 2>/dev/null | head -1 | sed -E 's/.*=(.*)/\1/')
MPASS=$(grep -iE 'MONGO_INITDB_ROOT_PASSWORD|MONGO_PASS' /root/kb-deploy/.env 2>/dev/null | head -1 | sed -E 's/.*=(.*)/\1/')
echo "提取的 Mongo 用户: ${MUSER:-未找到}  密码: ${MPASS:+(已获取)}"
# 若.env没有，从docker-compose环境变量读
if [ -z "$MUSER" ]; then
  MUSER=$(grep -iE 'MONGO_INITDB_ROOT_USERNAME' /root/kb-deploy/docker-compose.yml 2>/dev/null | head -1 | sed -E 's/.*=//;s/[" ]//g')
  MPASS=$(grep -iE 'MONGO_INITDB_ROOT_PASSWORD' /root/kb-deploy/docker-compose.yml 2>/dev/null | head -1 | sed -E 's/.*=//;s/[" ]//g')
  echo "(从compose提取) Mongo 用户: ${MUSER:-未找到}  密码: ${MPASS:+(已获取)}"
fi

if [ -n "$MUSER" ] && [ -n "$MPASS" ]; then
  echo "--- 带 $MUSER 认证查询 ---"
  docker exec kb-mongo mongosh --quiet -u "$MUSER" -p "$MPASS" --authenticationDatabase admin --eval '
const dbs = db.adminCommand({listDatabases:1}).databases.map(d=>d.name);
print("所有数据库: " + dbs.join(", "));
dbs.forEach(dbName => {
  const d = db.getSiblingDB(dbName);
  let cols = [];
  try { cols = d.getCollectionNames(); } catch(e) { print("  跳过 "+dbName+": "+e.message); return; }
  cols.forEach(c => {
    if (c.indexOf("system.") === 0) return;
    let cnt = 0; try { cnt = d.getCollection(c).countDocuments({docId:7}); } catch(e) {}
    if (cnt > 0) {
      print("命中: " + dbName + "." + c + " docId=7 数量=" + cnt);
      const all = d.getCollection(c).find({docId:7}).toArray();
      all.forEach((doc,i) => {
        print("  ["+i+"] 字段: " + Object.keys(doc).join(", "));
        print("      isCurrent=" + doc.isCurrent + " version=" + doc.version + " content长度=" + (doc.content ? String(doc.content).length : "无content字段"));
      });
    }
  });
});
print("扫描完成");
' 2>&1
else
  echo "未提取到 Mongo 凭据，尝试无认证查询各库"
  for dbn in kb knowledge kb_knowledge kbdb; do
    echo "--- 尝试 db=$dbn ---"
    docker exec kb-mongo mongosh --quiet "$dbn" --eval 'db.getCollectionNames().forEach(c=>{const n=db.getCollection(c).countDocuments({docId:7}); if(n>0) print(c+": "+n)})' 2>&1 | head -5
  done
fi

echo ""
echo "$SEP"
echo "J. kb-knowledge 更全日志 (最近 300 行, 含 mongo/error/getById)"
echo "$SEP"
docker logs kb-knowledge --tail 300 2>&1 | grep -iE "mongo|connect|auth|denied|getById|findByDocId|content|null|error|exception|fail" | tail -40
echo "--- 启动期日志 (前 60 行, 看 Mongo 连接初始化) ---"
docker logs kb-knowledge 2>&1 | head -60 | grep -iE "mongo|data|connect|uri|error|exception|fail" | head -20

echo ""
echo "$SEP"
echo "K. 运行中JAR的 getById 方法是否真正调用 mongo (检查方法引用)"
echo "$SEP"
echo "--- 运行JAR DocServiceImpl 中 getById 附近字符串(完整) ---"
docker exec kb-knowledge sh -c 'cd /tmp/R1 && strings BOOT-INF/classes/com/kb/knowledge/service/impl/DocServiceImpl.class 2>/dev/null' | grep -iE "getById|findByDocId|selectById|docContent|setContent|getContent|orElse|orElseGet|null" | sort -u
echo "--- 新版JAR DocServiceImpl 中 getById 附近字符串(完整) ---"
docker exec kb-knowledge sh -c 'cd /tmp/R2 && strings BOOT-INF/classes/com/kb/knowledge/service/impl/DocServiceImpl.class 2>/dev/null' | grep -iE "getById|findByDocId|selectById|docContent|setContent|getContent|orElse|orElseGet|null" | sort -u

echo ""
echo "$SEP"
echo "L. DocController.getById 字符串 (确认路由)"
echo "$SEP"
docker exec kb-knowledge sh -c 'cd /tmp/R1 && unzip -o /app/kb-knowledge.jar "BOOT-INF/classes/com/kb/knowledge/controller/DocController.class" >/dev/null 2>&1; strings BOOT-INF/classes/com/kb/knowledge/controller/DocController.class 2>/dev/null' | grep -iE "getById|Mapping|/doc|content|DocVO|Response" | sort -u | head -20

echo ""
echo "$SEP"
echo "DONE"
echo "$SEP"
