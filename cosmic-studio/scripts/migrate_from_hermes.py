#!/usr/bin/env python3
"""存量迁移：Hermes SQLite → MySQL 两库 + studio 元数据。

  cosmic.db          → cosmic_active   （活跃编写数据）
  cosmic_archive.db  → cosmic_archive  （40 项目/3780 FP 归档）
  cosmic_vocab.db    → cosmic_studio.vocab_terms（status=confirmed, source=archive）
  attribute_pools.json → cosmic_studio.attr_pools

默认覆盖式导入（先清目标库）。可 --append 增量追加。
用法： python scripts/migrate_from_hermes.py --hermes-dir /root/hermes-workspace/cosmic/db [--append]
"""
import argparse
import json
import os
import sqlite3
import sys

import pymysql

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from app import config  # noqa: E402


def connect(db_name: str):
    return pymysql.connect(host=config.DB_HOST, port=config.DB_PORT,
                           user=config.DB_USER, password=config.DB_PASSWORD,
                           database=db_name, charset="utf8mb4", autocommit=False)


def wipe(cur):
    for t in ("screenshots", "sub_processes", "fps", "modules", "projects"):
        cur.execute(f"DELETE FROM {t}")


def migrate_dimension(sqlite_path: str, mysql_db: str, append: bool = False):
    src = sqlite3.connect(sqlite_path)
    src.row_factory = sqlite3.Row
    dst = connect(mysql_db)
    src_tables = {r[0] for r in src.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    has_subs = "cosmic_sub_processes" in src_tables
    has_sheets = "source_sheet" in [r[1] for r in src.execute("PRAGMA table_info(cosmic_projects)")]
    n = {"projects": 0, "modules": 0, "fps": 0, "subs": 0, "screenshots": 0}
    try:
        with dst.cursor() as cur:
            if not append:
                wipe(cur)
            sel_sheet = ", source_sheet" if has_sheets else ", '' AS source_sheet"
            for p in src.execute(f"SELECT *, {sel_sheet[2:]} FROM cosmic_projects ORDER BY id"):
                archived = p["archived_at"] if "archived_at" in p.keys() else None
                cur.execute("""
                    INSERT INTO projects (project_code, client_name, requirement_id, requirement_name,
                                          client_contract, batch_no, status, archived_at, source_sheet)
                    VALUES (%s,%s,%s,%s,'','',%s,%s,%s)
                """, (p["project_code"], p["client_name"], p["requirement_id"],
                      p["requirement_name"], "archived" if archived else "draft",
                      archived, p["source_sheet"]))
                pid, n["projects"] = cur.lastrowid, n["projects"] + 1
                for m in src.execute("SELECT * FROM cosmic_modules WHERE project_id=? ORDER BY sort_order", (p["id"],)):
                    cur.execute("INSERT INTO modules (project_id, level1, level2, level3, sort_order) VALUES (%s,%s,%s,%s,%s)",
                                (pid, m["level1"], m["level2"], m["level3"], m["sort_order"]))
                    mid, n["modules"] = cur.lastrowid, n["modules"] + 1
                    for f in src.execute("SELECT * FROM cosmic_fps WHERE module_id=? ORDER BY sort_order", (m["id"],)):
                        cur.execute("INSERT INTO fps (module_id, sort_order, functional_user, trigger_event, fp_name) VALUES (%s,%s,%s,%s,%s)",
                                    (mid, f["sort_order"], f["functional_user"], f["trigger_event"], f["fp_name"]))
                        fid, n["fps"] = cur.lastrowid, n["fps"] + 1
                        if not has_subs:
                            continue
                        for sp in src.execute("SELECT * FROM cosmic_sub_processes WHERE fp_id=? ORDER BY sort_order", (f["id"],)):
                            cur.execute("INSERT INTO sub_processes (fp_id, sort_order, description, data_move_type, data_group_name, data_attributes) VALUES (%s,%s,%s,%s,%s,%s)",
                                        (fid, sp["sort_order"], sp["description"], sp["data_move_type"],
                                         sp["data_group_name"], sp["data_attributes"]))
                            n["subs"] += 1
                        for sc in src.execute("SELECT * FROM cosmic_screenshots WHERE fp_id=? ORDER BY sort_order", (f["id"],)):
                            cur.execute("INSERT INTO screenshots (fp_id, sort_order, image_data, image_width, image_height) VALUES (%s,%s,%s,%s,%s)",
                                        (fid, sc["sort_order"], sc["image_data"], sc["image_width"], sc["image_height"]))
                            n["screenshots"] += 1
        dst.commit()
        print(f"✅ {os.path.basename(sqlite_path)} → {mysql_db}: {n}")
    except Exception:
        dst.rollback()
        raise
    finally:
        src.close()
        dst.close()


def migrate_vocab(sqlite_path: str):
    src = sqlite3.connect(sqlite_path)
    src.row_factory = sqlite3.Row
    dst = connect(config.DB_STUDIO)
    n = 0
    try:
        with dst.cursor() as cur:
            cur.execute("DELETE FROM vocab_terms")
            cur.execute("DELETE FROM vocab_categories")
            for c in src.execute("SELECT * FROM term_categories"):
                cur.execute("INSERT INTO vocab_categories (id, name, description) VALUES (%s,%s,%s)",
                            (c["id"], c["name"], c["description"] or ""))
            for t in src.execute("SELECT * FROM terms"):
                cur.execute("""
                    INSERT INTO vocab_terms (term, category_id, frequency, source, status)
                    VALUES (%s,%s,%s,'archive','confirmed')
                    ON DUPLICATE KEY UPDATE frequency=VALUES(frequency), category_id=VALUES(category_id)
                """, (t["term"], t["category_id"], t["frequency"]))
                n += 1
        dst.commit()
        print(f"✅ vocab_terms: {n} 条")
    finally:
        src.close()
        dst.close()


def migrate_pools(json_path: str):
    if not os.path.exists(json_path):
        print("⚠️ attribute_pools.json 不存在，跳过")
        return
    pools = json.load(open(json_path, encoding="utf-8"))
    dst = connect(config.DB_STUDIO)
    n = 0
    try:
        with dst.cursor() as cur:
            for group, fields in pools.items():
                cur.execute("""
                    INSERT INTO attr_pools (data_group, fields) VALUES (%s,%s)
                    ON DUPLICATE KEY UPDATE fields=VALUES(fields)
                """, (group, json.dumps(fields, ensure_ascii=False)))
                n += 1
        dst.commit()
        print(f"✅ attr_pools: {n} 组")
    finally:
        dst.close()


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--hermes-dir", default="/root/hermes-workspace/cosmic/db")
    ap.add_argument("--append", action="store_true", help="增量追加（默认清库重灌）")
    ap.add_argument("--skip-pools", action="store_true")
    args = ap.parse_args()

    d = args.hermes_dir
    migrate_dimension(os.path.join(d, "cosmic.db"), config.DB_ACTIVE, append=args.append)
    migrate_dimension(os.path.join(d, "cosmic_archive.db"), config.DB_ARCHIVE, append=args.append)
    migrate_vocab(os.path.join(d, "cosmic_vocab.db"))
    if not args.skip_pools:
        migrate_pools(os.path.join(d, "attribute_pools.json"))
    print("🎉 migrate done")
