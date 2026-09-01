"""项目深拷贝：同需求新工作线（活数据，可继续编写）。

抽成单实现，供两处复用：
1. 编写库「复制」按钮（dimension.copy_project 端点委托此处）
2. 评审手动/自动修订完成后，自动新开一个可编辑项目副本（reviews.manual_done / auto_fix）

深拷贝范围：projects → modules → fps → sub_processes → screenshots，
全部在单个事务内写入，避免 N+1 连接（fix P1/P3）。
"""
from fastapi import HTTPException

from .. import db


def copy_project(db_name: str, src_pid: int) -> dict:
    """把项目 src_pid 深拷贝为新 pid，命名「原名-副本N」（同 requirement_id 递增）。

    返回 {"id": new_pid, "name": "..."}。源项目不存在则 404。
    """
    src = db.query(db_name, "SELECT * FROM projects WHERE id=%s", (src_pid,), one=True)
    if not src:
        raise HTTPException(404, "项目不存在")
    # 一次性预载源树（IN 查询，复用单一连接），再在单个 tx 内写入，
    # 避免 with tx 内用 db.query 逐层裸读开 N+1 连接（fix P1/P3）
    modules = db.query(db_name, "SELECT * FROM modules WHERE project_id=%s ORDER BY sort_order", (src_pid,))
    mod_ids = [m["id"] for m in modules]
    fps = (db.query(db_name,
                    f"SELECT * FROM fps WHERE module_id IN ({','.join(['%s'] * len(mod_ids)) or '0'}) "
                    f"ORDER BY sort_order", tuple(mod_ids))
           if mod_ids else [])
    fp_ids = [f["id"] for f in fps]
    subs = (db.query(db_name,
                    f"SELECT * FROM sub_processes WHERE fp_id IN ({','.join(['%s'] * len(fp_ids)) or '0'}) "
                    f"ORDER BY sort_order", tuple(fp_ids))
            if fp_ids else [])
    shots = (db.query(db_name,
                     f"SELECT * FROM screenshots WHERE fp_id IN ({','.join(['%s'] * len(fp_ids)) or '0'}) "
                     f"ORDER BY sort_order", tuple(fp_ids))
             if fp_ids else [])
    fps_by_mod, subs_by_fp, shots_by_fp = {}, {}, {}
    for f in fps:
        fps_by_mod.setdefault(f["module_id"], []).append(f)
    for s in subs:
        subs_by_fp.setdefault(s["fp_id"], []).append(s)
    for sc in shots:
        shots_by_fp.setdefault(sc["fp_id"], []).append(sc)
    copies = db.query(db_name, "SELECT COUNT(*) AS n FROM projects WHERE requirement_id=%s",
                      (src["requirement_id"],), one=True)["n"]
    with db.tx(db_name) as cur:
        cur.execute("""INSERT INTO projects (project_code, client_name, requirement_id, requirement_name,
                       client_contract, batch_no, status, source_sheet)
                       VALUES (%s,%s,%s,%s,%s,%s,'draft',%s)""",
                    (src["project_code"], src["client_name"], src["requirement_id"],
                     f"{src['requirement_name']}-副本{copies + 1}",
                     src["client_contract"], src["batch_no"], src["source_sheet"]))
        new_pid = cur.lastrowid
        for m in modules:
            cur.execute("""INSERT INTO modules (project_id, level1, level2, level3, sort_order)
                           VALUES (%s,%s,%s,%s,%s)""",
                        (new_pid, m["level1"], m["level2"], m["level3"], m["sort_order"]))
            new_mid = cur.lastrowid
            for f in fps_by_mod.get(m["id"], []):
                cur.execute("""INSERT INTO fps (module_id, sort_order, functional_user, trigger_event, fp_name)
                               VALUES (%s,%s,%s,%s,%s)""",
                            (new_mid, f["sort_order"], f["functional_user"], f["trigger_event"], f["fp_name"]))
                new_fid = cur.lastrowid
                for s in subs_by_fp.get(f["id"], []):
                    cur.execute("""INSERT INTO sub_processes (fp_id, sort_order, description, data_move_type,
                                   data_group_name, data_attributes) VALUES (%s,%s,%s,%s,%s,%s)""",
                                (new_fid, s["sort_order"], s["description"], s["data_move_type"],
                                 s["data_group_name"], s["data_attributes"]))
                for sc in shots_by_fp.get(f["id"], []):
                    cur.execute("""INSERT INTO screenshots (fp_id, sort_order, image_data, image_width, image_height)
                                   VALUES (%s,%s,%s,%s,%s)""",
                                (new_fid, sc["sort_order"], sc["image_data"], sc["image_width"], sc["image_height"]))
    return {"id": new_pid, "name": f"{src['requirement_name']}-副本{copies + 1}"}
