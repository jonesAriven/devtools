#!/bin/bash
# kb-knowledge 诊断脚本 第四轮：用正确凭据查 Mongo docId=7
set +e
SEP="=============================================="

# 写入查询JS到容器外临时文件
cat > /tmp/q.js <<'EOF'
print("=== 所有数据库 ===");
const dbs = db.adminCommand({listDatabases:1}).databases.map(d=>d.name);
print("DBs: " + dbs.join(", "));
dbs.forEach(dbName => {
  const d = db.getSiblingDB(dbName);
  let cols = [];
  try { cols = d.getCollectionNames(); } catch(e) { return; }
  cols.forEach(c => {
    if (c.indexOf("system.") === 0) return;
    let n = 0; try { n = d.getCollection(c).countDocuments({docId: 7}); } catch(e) {}
    let n2 = 0; try { n2 = d.getCollection(c).countDocuments({doc_id: 7}); } catch(e) {}
    if (n > 0 || n2 > 0) {
      print("HIT: " + dbName + "." + c + " docId=7:" + n + " doc_id=7:" + n2);
      const doc = n > 0 ? d.getCollection(c).findOne({docId: 7}) : d.getCollection(c).findOne({doc_id: 7});
      print("  fields: " + Object.keys(doc).join(", "));
      print("  isCurrent: " + doc.isCurrent + " version: " + doc.version);
      print("  content field exists: " + (doc.content !== undefined));
      print("  content length: " + (doc.content ? String(doc.content).length : 0));
      print("  content preview: " + (doc.content ? String(doc.content).substring(0,150) : "(empty)"));
    }
  });
});
print("=== 扫描完成 ===");
EOF

echo "$SEP"
echo "M. 用正确凭据 (kb/kb123456/admin) 查询 Mongo docId=7"
echo "$SEP"
echo "--- 通过 stdin 喂 JS 给 mongosh ---"
docker exec -i kb-mongo mongosh --quiet -u kb -p kb123456 --authenticationDatabase admin kb_knowledge < /tmp/q.js 2>&1

echo ""
echo "$SEP"
echo "N. 确认 kb-knowledge 运行 profile 和 Mongo URI 环境变量"
echo "$SEP"
docker exec kb-knowledge env 2>&1 | grep -iE 'SPRING_PROFILES|MONGO|MYSQL|REDIS|MEILI' | sort
echo "--- kb-mongo 初始化环境(确认root账号) ---"
docker inspect kb-mongo --format '{{range .Config.Env}}{{println .}}{{end}}' 2>&1 | grep -iE 'MONGO|ROOT|USER|PASS' | sort

echo ""
echo "$SEP"
echo "O. kb_knowledge 库所有集合与文档总数"
echo "$SEP"
docker exec -i kb-mongo mongosh --quiet -u kb -p kb123456 --authenticationDatabase admin kb_knowledge --eval 'db.getCollectionNames().forEach(c=>{if(c.indexOf("system")<0){print(c+": "+db.getCollection(c).countDocuments({}))}})' 2>&1

echo ""
echo "$SEP"
echo "DONE"
echo "$SEP"
