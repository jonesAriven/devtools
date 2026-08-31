"""项目树分页读取。

原 `xlsx_export.load_project_tree` 是 N+1 查询（1 次项目 + M 次模块FP + M*F 次子过程），
归档库 251 模块 / 3780 FP 时会打出 4000+ 条 SQL，页面必然卡死。

本模块改为「3 条查询 + 内存分组」，且 LIMIT 能真正下推：
  1. modules  WHERE project_id=? [AND keyword]  LIMIT/OFFSET   ← 分页发生在这一层
  2. fps      WHERE module_id IN (本页模块)
  3. sub_processes WHERE fp_id IN (本页 FP)

分页粒度 = 顶层模块，子级（FP / 子过程）跟随，保证树的语义完整。
"""

from .. import paging

DEFAULT_MODULE_PAGE_SIZE = 10


def _placeholders(n: int) -> str:
    return ",".join(["%s"] * n)


def load_paged(dim_db: str, project_id: int, page=1, page_size=DEFAULT_MODULE_PAGE_SIZE,
               keyword: str = "") -> dict | None:
    """返回 {project, modules, total, page, page_size, stats}；项目不存在返回 None。"""
    from .. import db

    proj = db.query(dim_db, "SELECT * FROM projects WHERE id=%s", (project_id,), one=True)
    if not proj:
        return None

    kw = (keyword or "").strip()
    if kw:
        like = f"%{kw}%"
        mod_where = ("WHERE project_id=%s AND "
                     "(level1 LIKE %s OR level2 LIKE %s OR level3 LIKE %s)")
        mod_params: tuple = (project_id, like, like, like)
    else:
        mod_where = "WHERE project_id=%s"
        mod_params = (project_id,)

    total = db.query(dim_db, f"SELECT COUNT(*) AS total FROM modules {mod_where}",
                     mod_params, one=True)["total"]
    page, page_size, offset = paging.normalize(page, page_size)
    mods = db.query(dim_db,
                    f"SELECT * FROM modules {mod_where} ORDER BY sort_order, id "
                    f"LIMIT %s OFFSET %s",
                    (*mod_params, page_size, offset))

    stats = db.query(dim_db, """
        SELECT
          (SELECT COUNT(*) FROM modules WHERE project_id=%s) AS module_count,
          (SELECT COUNT(*) FROM fps f JOIN modules m ON m.id=f.module_id
             WHERE m.project_id=%s) AS fp_count,
          (SELECT COUNT(*) FROM sub_processes sp JOIN fps f ON f.id=sp.fp_id
             JOIN modules m ON m.id=f.module_id WHERE m.project_id=%s) AS sub_count
    """, (project_id, project_id, project_id), one=True)

    if not mods:
        return {"project": proj, "modules": [], "total": total, "page": page,
                "page_size": page_size, "stats": stats}

    mids = [m["id"] for m in mods]
    fps = db.query(dim_db,
                   f"SELECT * FROM fps WHERE module_id IN ({_placeholders(len(mids))}) "
                   f"ORDER BY sort_order, id", tuple(mids))
    fids = [f["id"] for f in fps]
    subs = []
    if fids:
        subs = db.query(dim_db,
                        f"SELECT * FROM sub_processes WHERE fp_id IN "
                        f"({_placeholders(len(fids))}) ORDER BY sort_order, id",
                        tuple(fids))

    by_fp: dict[int, list] = {}
    for s in subs:
        by_fp.setdefault(s["fp_id"], []).append(s)
    by_mod: dict[int, list] = {}
    for f in fps:
        item = dict(f)
        item["subs"] = by_fp.get(f["id"], [])
        by_mod.setdefault(f["module_id"], []).append(item)
    for m in mods:
        m["fps"] = by_mod.get(m["id"], [])

    return {"project": proj, "modules": mods, "total": total, "page": page,
            "page_size": page_size, "stats": stats}
