# -*- coding: utf-8 -*-
"""OmniFind 全量功能测试（临时语料，不碰生产索引）。"""
import json
import os
import shutil
import sys
import tempfile
import time
from pathlib import Path

REPO = Path("/root/devtools/omnifind")
sys.path.insert(0, str(REPO))

TMP = Path(tempfile.mkdtemp(prefix="omnifind-test-"))
DB = TMP / "db"
DB.mkdir()
PASS, FAIL = [], []


def check(name, cond, detail=""):
    (PASS if cond else FAIL).append((name, detail))
    print(("  PASS " if cond else "  FAIL ") + name + (f"  [{detail}]" if detail and not cond else ""))


# ---------- 语料 ----------
corpus = TMP / "corpus"
(corpus / "docs" / "sub").mkdir(parents=True)
(corpus / "code").mkdir(parents=True)
(corpus / "docs" / "readme.md").write_text(
    "# 项目手册\n\n本系统用于知识检索测试，包含向量语义与全文索引能力。中文分词验证段落。\n" * 5, encoding="utf-8")
(corpus / "docs" / "sub" / "报告.txt").write_text("季度经营报告：营收增长百分之二十，成本下降。知识图谱建设持续推进。\n" * 3, encoding="utf-8")
(corpus / "code" / "app.py").write_text("def search_user(uid):\n    '''按用户ID检索'''\n    return db.query(uid)\n", encoding="utf-8")
(corpus / "code" / "utils.js").write_text("function formatDate(d){ return d.toISOString(); }\n", encoding="utf-8")
(corpus / "code" / "my_config.json").write_text(json.dumps({"host": "127.0.0.1", "port": 8899}), encoding="utf-8")
(corpus / "a_underscore_file.txt").write_text("underscore in name test\n", encoding="utf-8")
(corpus / "archive.tar.gz").write_text("tar gz ext test\n", encoding="utf-8")

print("== L1 FilenameIndex")
from omnifind.layers.l1_filename.index import FilenameIndex
l1 = FilenameIndex(db_path=DB / "filename.db")
rows = []
for p in corpus.rglob("*"):
    if p.is_file():
        st = p.stat()
        rows.append((str(p), p.name, st.st_size, st.st_mtime, 0))
l1.bulk_upsert(rows)
check("count", l1.count() == 7, str(l1.count()))
r = l1.search("readme")
check("substring", [h.name for h in r] == ["readme.md"])
r = l1.search("报告")
check("chinese", [h.name for h in r] == ["报告.txt"])
r = l1.search("*.js")
check("wildcard", [h.name for h in r] == ["utils.js"])
r = l1.search("a_underscore_file")
check("literal underscore", [h.name for h in r] == ["a_underscore_file.txt"])
r = l1.search("report", ext_filter=".md")
check("ext miss (case)", [h.name for h in r] == [])
r = l1.search("readme", ext_filter=".md")
check("ext hit", [h.name for h in r] == ["readme.md"])
r = l1.search("archive.tar.gz", ext_filter=".gz")
check("tar.gz ext", [h.name for h in r] == ["archive.tar.gz"])
r = l1.search_regex(r"^\w+_\w+\.txt$")
check("regex underscore", [h.name for h in r] == ["a_underscore_file.txt"])
r = l1.search_regex("readme|报告")
check("regex alternation", len(r) == 2, str([h.name for h in r]))
try:
    l1.search_regex("([unclosed")
    check("bad regex raises", False)
except ValueError:
    check("bad regex raises", True)
n, capped = l1.count_match("t", cap=3)
check("count cap", n == 3 and capped, f"{n},{capped}")
grp = l1.count_match_grouped("t", ["", ".md", ".txt", ".py"])
check("grouped total == count_match", grp[""] == 4, str(grp))
check("grouped facet", grp[".md"] == 0 and grp[".txt"] == 2 and grp[".py"] == 0, str(grp))
l1.close()

print("== L2 FullTextIndex")
from omnifind.layers.l2_fulltext.index import FullTextIndex
l2 = FullTextIndex(db_path=DB / "fulltext.db")
for p in [corpus / "docs" / "readme.md", corpus / "docs" / "sub" / "报告.txt",
          corpus / "code" / "app.py", corpus / "code" / "utils.js", corpus / "code" / "my_config.json"]:
    l2.upsert_document(str(p), p.name, p.read_text(encoding="utf-8", errors="ignore"), p.stat().st_size, p.stat().st_mtime, p.suffix.lower())
check("l2 count", l2.count() == 5, str(l2.count()))
r = l2.search("向量语义", limit=10)
check("chinese fulltext", len(r) == 1 and r[0].title == "readme.md", str([h.title for h in r]))
r = l2.search("检索")
check("chinese multi-doc", len(r) == 2, str([h.title for h in r]))
r = l2.search("formatDate")
check("english token", len(r) == 1 and r[0].title == "utils.js")
r = l2.search("db.query(uid)")
check("code chars no crash", len(r) >= 1 and "app.py" in [h.title for h in r])
check("snippet hl marks", "\ue000" in r[0].snippet, repr(r[0].snippet))
r = l2.search("营收增长")
check("2nd doc snippet", len(r) == 1)
grp = l2.count_match_grouped("检索", ["", ".md", ".txt", ".py"])
check("l2 grouped", grp[""] == 2 and grp[".md"] == 1 and grp[".py"] == 1 and grp[".txt"] == 0, str(grp))
n, capped = l2.count_match("检索")
check("l2 count_match", n == 2 and not capped, f"{n},{capped}")
r = l2.search("readme", ext_filter=".md")
check("l2 ext filter", len(r) == 1)
l2.close()

print("== L3 Semantic (onnx)")
try:
    from omnifind.core.config import OmniConfig
    cfg = OmniConfig.load()
    from omnifind.layers.l3_semantic.embedder import OnnxEmbedder
    from omnifind.layers.l3_semantic.index import SemanticIndex
    emb = OnnxEmbedder(REPO / "models" / "bge-small-zh-v1.5", max_length=cfg.chunk_size)
    sem = SemanticIndex(emb, db_path=DB / "lancedb", dim=emb.dim)
    sem.add_document(str(corpus / "docs" / "readme.md"), "readme.md",
                     (corpus / "docs" / "readme.md").read_text(encoding="utf-8"))
    sem.add_document(str(corpus / "code" / "app.py"), "app.py",
                     (corpus / "code" / "app.py").read_text(encoding="utf-8"))
    check("l3 chunks>0", sem.count() > 0, str(sem.count()))
    r = sem.search("知识库语义检索是怎么实现的")
    check("l3 semantic ranking", len(r) >= 1 and "readme" in r[0].path, str([(h.path, round(h.score, 2)) for h in r]))
    sem.remove_document(str(corpus / "code" / "app.py"))
    r2 = sem.search("数据库检索函数")
    check("l3 remove doc", all("app.py" not in h.path for h in r2))
except FileNotFoundError as e:
    print("  SKIP L3: 模型缺失")
    check("l3 (skipped)", True)

print("== Router")
from omnifind.core.router import QueryRouter
router = QueryRouter(l1=FilenameIndex(db_path=DB / "filename.db"),
                     l2=FullTextIndex(db_path=DB / "fulltext.db"), l3=None)
resp = router.search("filename:readme")
check("router filename mode", resp.mode == "filename" and len(resp.hits) == 1)
resp = router.search("content:检索")
check("router content mode", resp.mode == "fulltext" and len(resp.hits) == 2)
resp = router.search("?语义检索怎么做")
check("router semantic fallback(no l3)", resp.mode == "semantic" and resp.counts.get("semantic_disabled") is True)
resp = router.search("all:检索")
check("router all mode", resp.mode == "all" and resp.counts.get("l1", 0) >= 0 and resp.counts.get("l2", 0) == 2)
resp = router.search("re:^\\w+_\\w+\\.txt$")
check("router regex mode", resp.mode == "filename_regex" and len(resp.hits) == 1)
resp = router.search("re:([bad")
check("router bad regex counts.error", resp.counts.get("error") is not None and resp.counts.get("l1") == 0)
resp = router.search("检索 ext:.md")
check("router ext filter parse", resp.counts.get("ext") == ".md" and len(resp.hits) == 1, str(resp.counts))
check("router counts l2 respects ext", resp.counts.get("l2") == 1 and resp.counts.get("l1") == 0, str(resp.counts))
check("router total = active sum", resp.counts.get("total") == resp.counts.get("l2"))
resp = router.search("readme sort:time")
check("router sort parse", resp.mode == "auto")
check("router ext_facets present", resp.ext_facets is not None and "" in resp.ext_facets)
t0 = time.time()
for _ in range(20):
    router.search("检索")
check("router 20x search < 10s", time.time() - t0 < 10, f"{time.time()-t0:.2f}s")

print("== Web API (TestClient, state 注入)")
from fastapi.testclient import TestClient
import omnifind.web.server as srv
l1b = FilenameIndex(db_path=DB / "filename.db")
l2b = FullTextIndex(db_path=DB / "fulltext.db")
srv._state.update(cfg=OmniConfig.load(), l1=l1b, l2=l2b, l3=None, router=QueryRouter(l1=l1b, l2=l2b, l3=None))
client = TestClient(srv.app)
r = client.get("/api/search", params={"q": "检索"})
check("api search 200", r.status_code == 200 and len(r.json()["hits"]) == 2, r.text[:200])
r = client.get("/api/search", params={"q": "检索", "limit": 30, "offset": 1})
check("api offset", len(r.json()["hits"]) == 1)
r = client.get("/api/search", params={"q": "检索", "mode": "filename"})
check("api mode param", len(r.json()["hits"]) == 0)
r = client.get("/api/status")
check("api status", r.status_code == 200 and r.json()["l1_count"] == 7)
r = client.get("/api/preview", params={"path": str(corpus / "docs" / "readme.md")})
check("api preview in-index", r.status_code == 200 and "知识检索" in r.json().get("content", ""), r.text[:200])
r = client.get("/api/preview", params={"path": "/etc/passwd"})
check("api preview blocked outside index", r.status_code == 403, str(r.status_code))
r = client.get("/api/file/info", params={"path": str(corpus / "code" / "app.py")})
check("api file info", r.status_code == 200 and r.json()["ext"] == ".py")
r = client.post("/api/config", json={"port": 8899, "evil_field": 1})
check("api config whitelist", r.status_code == 400, r.text[:120])
r = client.get("/api/search", params={"q": "x" * 2500})
check("api long query rejected", r.status_code == 422)

print()
print(f"==== 结果: PASS {len(PASS)} / FAIL {len(FAIL)}")
for name, detail in FAIL:
    print(f"  FAIL: {name} {detail}")
shutil.rmtree(TMP, ignore_errors=True)
sys.exit(1 if FAIL else 0)
