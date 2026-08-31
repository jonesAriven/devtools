#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""词库分类 v1→v2 迁移：旧分类重映射到四维度，保留术语的 status/source/frequency。

v2 维度（固定 id，与 app/engines/vocab_miner.py 的 CAT_* 一致）：
    id5  原子业务词元   ← 数据属性字段(id5) 原地改名 + jieba 原子
    id2  业务对象       ← 高频/中频/低频业务名词(id1/2/3)
    id11 结构参考       ← 三级模块名(id4)/数据组名(id6)/FP名参考(id11)
    id9  功能维度       ← 触发器模式(id9)/用户角色(id10)

本脚本只动 category_id 与 vocab_categories 表；vocab_terms 的词频/状态/来源不动。
可重复执行（幂等）：分类已存在则改名，术语已在新维度则 UPDATE 无影响，废弃分类删除无影响。

执行：docker exec cosmic-api python scripts/migrate_vocab_taxonomy.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import config, db  # noqa: E402

# 旧分类 id -> 新分类 id（仅列出会变的；5/2/11/9 原地保留）
# 动词模式(7)→功能维度(9)；当前项目特有(12)→结构参考(11)
OLD_TO_NEW = {1: 2, 3: 2, 4: 11, 6: 11, 7: 9, 10: 9, 12: 11}
OBSOLETE = [1, 3, 4, 6, 7, 8, 10, 12]

NEW_CATS = [
    (5, "原子业务词元", "从数据属性字段与业务对象/数据组名切分出的原子业务名词（词库主体）"),
    (2, "业务对象", "FP 名去动词后的中层业务对象（参考级，非原子）"),
    (11, "结构参考", "整条 FP 名 / 数据组名 / 三级模块名，导航/结构用，非词库"),
    (9, "功能维度", "用户角色 / 触发器模式"),
]


def _dist():
    return {r["category_id"]: r["n"]
            for r in db.query(config.DB_STUDIO,
                              "SELECT category_id, COUNT(*) AS n FROM vocab_terms GROUP BY category_id")}


def main():
    print("== 迁移前 ==")
    before = _dist()
    print("  terms by cat:", before)
    print("  categories:", [(r["id"], r["name"])
                            for r in db.query(config.DB_STUDIO,
                                              "SELECT id, name FROM vocab_categories ORDER BY id")])

    # 1) 确保四维度分类存在（存在则改名）
    for cid, name, desc in NEW_CATS:
        db.execute(config.DB_STUDIO, """
            INSERT INTO vocab_categories (id, name, description) VALUES (%s, %s, %s)
            ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description)
        """, (cid, name, desc))

    # 2) 重映射术语 category_id（原地保留的 id 不出现在 OLD_TO_NEW 里）
    for old, new in OLD_TO_NEW.items():
        db.execute(config.DB_STUDIO,
                   "UPDATE vocab_terms SET category_id=%s WHERE category_id=%s", (new, old))

    # 3) 删除废弃分类（其术语已在第 2 步迁走，无悬空外键——category_id 本就无 FK）
    db.execute(config.DB_STUDIO,
               f"DELETE FROM vocab_categories WHERE id IN ({','.join(map(str, OBSOLETE))})")

    print("== 迁移后 ==")
    print("  terms by cat:", _dist())
    print("  categories:", [(r["id"], r["name"])
                            for r in db.query(config.DB_STUDIO,
                                              "SELECT id, name FROM vocab_categories ORDER BY id")])
    print("  total terms:", db.query(config.DB_STUDIO,
                                     "SELECT COUNT(*) AS n FROM vocab_terms", one=True)["n"])


if __name__ == "__main__":
    main()
