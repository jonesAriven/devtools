import zipfile, re, sys, os

jar = r'd:\huliang\java\ideaworkspace\devtools\mykng\kb-knowledge\target\kb-knowledge.jar'
print("本地JAR:", jar, "存在:", os.path.exists(jar))
if not os.path.exists(jar):
    sys.exit()
print("大小:", os.path.getsize(jar), "字节")
print("修改时间:", __import__('datetime').datetime.fromtimestamp(os.path.getmtime(jar)))

z = zipfile.ZipFile(jar)
names = z.namelist()
targets = [n for n in names if n.endswith('DocServiceImpl.class') or n.endswith('DocController.class')]
print("\n找到的目标class:")
for n in targets:
    print("  ", n)

kw = [b'findByDocId', b'docContent', b'MongoTemplate', b'MongoRepository', b'isCurrent',
      b'getById', b'contentRepository', b'ContentService', b'DocContent', b'selectById', b'content']

for n in targets:
    data = z.read(n)
    print("\n=== ", n, " (", len(data), " bytes) ===")
    # 提取 UTF-8 字符串
    try:
        text = data.decode('utf-8', errors='ignore')
    except:
        text = ''
    found = set()
    for k in kw:
        if k.decode('utf-8', errors='ignore') in text:
            found.add(k.decode())
    # 用正则找可打印字符串中包含关键词的
    for m in re.finditer(rb'[\x20-\x7e]{4,}', data):
        s = m.group().decode('ascii', errors='ignore')
        for k in kw:
            if k.decode() in s and s not in found:
                found.add(s)
    print("命中关键词/字符串:")
    for s in sorted(found):
        print("   *", s)
    if not found:
        print("   (无 mongo/content 相关命中)")

# 搜索 source 路径确认
print("\n=== 本地源码 DocServiceImpl.java 确认 ===")
src_candidates = []
for root, dirs, files in os.walk(r'd:\huliang\java\ideaworkspace\devtools\mykng\kb-knowledge'):
    for f in files:
        if f == 'DocServiceImpl.java':
            src_candidates.append(os.path.join(root, f))
for s in src_candidates:
    print("源码文件:", s)
    with open(s, 'r', encoding='utf-8', errors='ignore') as fh:
        txt = fh.read()
    for k in ['findByDocId', 'MongoTemplate', 'mongoTemplate', 'isCurrent', 'docContentRepository', 'getById', 'content']:
        if k in txt:
            print("   源码含:", k)
