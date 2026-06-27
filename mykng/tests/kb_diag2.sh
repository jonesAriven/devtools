#!/bin/bash
# kb-knowledge 诊断脚本 第二轮：对比 JAR + Mongo 数据 + compose 配置（只读）
set +e
SEP="=============================================="

echo "$SEP"
echo "A. 两个 JAR 的 md5 与 DocServiceImpl 对比"
echo "$SEP"
echo "--- /app/kb-knowledge.jar (入口点实际运行, 21:20) ---"
docker exec kb-knowledge md5sum /app/kb-knowledge.jar 2>&1
echo "--- /app/app.jar (docker cp 误放, 22:48) ---"
docker exec kb-knowledge md5sum /app/app.jar 2>&1
echo ""
echo "--- 在 /app/kb-knowledge.jar 中定位 DocServiceImpl.class ---"
docker exec kb-knowledge sh -c 'unzip -l /app/kb-knowledge.jar 2>/dev/null | grep -iE "DocServiceImpl|DocController" | head -10'
echo "--- 在 /app/app.jar 中定位 DocServiceImpl.class ---"
docker exec kb-knowledge sh -c 'unzip -l /app/app.jar 2>/dev/null | grep -iE "DocServiceImpl|DocController" | head -10'

echo ""
echo "=== B. 反编译对比：/app/kb-knowledge.jar (运行中) 的 DocServiceImpl ==="
docker exec kb-knowledge sh -c 'cd /tmp && rm -rf R1 && mkdir R1 && cd R1 && unzip -o /app/kb-knowledge.jar "BOOT-INF/classes/com/kb/knowledge/service/impl/DocServiceImpl.class" >/dev/null 2>&1; F=$(find . -name "DocServiceImpl.class" | head -1); echo "提取文件: $F"; if [ -n "$F" ]; then echo "--- mongo/content 关键字符串 ---"; strings "$F" 2>/dev/null | grep -iE "findByDocId|docContent|MongoTemplate|MongoRepository|isCurrent|getById|contentRepository|ContentService|DocContent" | sort -u; echo "--- getById 方法相关字符串(全部) ---"; strings "$F" 2>/dev/null | grep -iE "getById|selectById|content" | sort -u | head -20; fi'

echo ""
echo "=== C. 反编译对比：/app/app.jar (新版未运行) 的 DocServiceImpl ==="
docker exec kb-knowledge sh -c 'cd /tmp && rm -rf R2 && mkdir R2 && cd R2 && unzip -o /app/app.jar "BOOT-INF/classes/com/kb/knowledge/service/impl/DocServiceImpl.class" >/dev/null 2>&1; F=$(find . -name "DocServiceImpl.class" | head -1); echo "提取文件: $F"; if [ -n "$F" ]; then echo "--- mongo/content 关键字符串 ---"; strings "$F" 2>/dev/null | grep -iE "findByDocId|docContent|MongoTemplate|MongoRepository|isCurrent|getById|contentRepository|ContentService|DocContent" | sort -u; echo "--- getById 方法相关字符串(全部) ---"; strings "$F" 2>/dev/null | grep -iE "getById|selectById|content" | sort -u | head -20; fi'

echo ""
echo "$SEP"
echo "D. 部署 JAR 检查: /root/kb-deploy/kb-knowledge/target/kb-knowledge.jar"
echo "$SEP"
ls -la /root/kb-deploy/kb-knowledge/target/kb-knowledge.jar 2>&1
md5sum /root/kb-deploy/kb-knowledge/target/kb-knowledge.jar 2>&1
echo "--- 该部署JAR 的 DocServiceImpl mongo 字符串 ---"
ssh_local_jar=/root/kb-deploy/kb-knowledge/target/kb-knowledge.jar
if [ -f "$ssh_local_jar" ]; then
  cd /tmp && rm -rf R3 && mkdir R3 && cd R3 && unzip -o "$ssh_local_jar" "BOOT-INF/classes/com/kb/knowledge/service/impl/DocServiceImpl.class" >/dev/null 2>&1; F=$(find . -name "DocServiceImpl.class" | head -1); echo "提取文件: $F"; if [ -n "$F" ]; then strings "$F" 2>/dev/null | grep -iE "findByDocId|docContent|MongoTemplate|isCurrent|getById|content" | sort -u | head -20; fi
fi

echo ""
echo "$SEP"
echo "E. docker-compose.yml 中 kb-knowledge 服务配置"
echo "$SEP"
grep -nA 25 'kb-knowledge:' /root/kb-deploy/docker-compose.yml 2>&1 | head -30

echo ""
echo "$SEP"
echo "F. Mongo 数据检查 (扫描所有库所有集合的 docId=7)"
echo "$SEP"
echo "--- mongosh 是否可用 ---"
docker exec kb-mongo which mongosh 2>&1
echo "--- 扫描 docId=7 ---"
docker exec kb-mongo mongosh --quiet --eval '
const dbs = db.adminCommand({listDatabases:1}).databases.map(d=>d.name);
print("所有数据库: " + dbs.join(", "));
dbs.forEach(dbName => {
  const d = db.getSiblingDB(dbName);
  let cols = [];
  try { cols = d.getCollectionNames(); } catch(e) {}
  cols.forEach(c => {
    if (c.indexOf("system.") === 0) return;
    let cnt = 0; try { cnt = d.getCollection(c).countDocuments({docId:7}); } catch(e) {}
    if (cnt > 0) {
      print("命中: " + dbName + "." + c + " docId=7 数量=" + cnt);
      const one = d.getCollection(c).findOne({docId:7});
      print("  样本字段: " + Object.keys(one).join(", "));
      const cur = d.getCollection(c).findOne({docId:7, isCurrent:true});
      print("  isCurrent=true 存在: " + (cur !== null));
      if (cur) print("  content 长度: " + (cur.content ? String(cur.content).length : "无content字段"));
    }
  });
});
print("扫描完成");
' 2>&1

echo ""
echo "$SEP"
echo "DONE"
echo "$SEP"
