"""JSON 导入导出：导入导出矩阵的 JSON 通道 + 覆盖前备份。"""
import json
import os
from datetime import datetime

from .. import config, db


def export_project_json(dim_db: str, project_id: int) -> dict | None:
    from .xlsx_export import load_project_tree
    tree = load_project_tree(dim_db, project_id)
    if not tree:
        return None
    out = {
        "project": {k: tree[k] for k in
                    ("project_code", "client_name", "requirement_id", "requirement_name",
                     "status") if k in tree},
        "modules": [{
            "level1": m["level1"], "level2": m["level2"], "level3": m["level3"],
            "fps": [{
                "fp_name": f["fp_name"], "functional_user": f["functional_user"],
                "trigger_event": f["trigger_event"],
                "subs": [{
                    "description": s["description"], "data_move_type": s["data_move_type"],
                    "data_group_name": s["data_group_name"], "data_attributes": s["data_attributes"],
                } for s in f["subs"]],
            } for f in m["fps"]],
        } for m in tree["modules"]],
    }
    return out


def export_dimension_json(dim_db: str) -> list:
    pids = [r["id"] for r in db.query(dim_db, "SELECT id FROM projects ORDER BY id")]
    return [export_project_json(dim_db, pid) for pid in pids]


def dump_dimension_backup(dim_db: str, tag: str = "backup") -> str:
    """覆盖导入前自动备份整维度到 JSON 文件，返回文件路径。"""
    os.makedirs(config.BACKUP_DIR, exist_ok=True)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    path = os.path.join(config.BACKUP_DIR, f"{dim_db}_{tag}_{ts}.json")
    data = export_dimension_json(dim_db)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    return path


def import_json(dim_db: str, payload: dict, mode: str = "incremental",
                project_id: int | None = None) -> dict:
    """JSON payload：{project:{...}, modules:[...]}。语义与 xlsx 导入一致。"""
    proj = payload.get("project") or {}
    mods_in = payload.get("modules") or []
    if not mods_in:
        raise ValueError("payload.modules 为空")

    report = {"mode": mode, "modules": len(mods_in),
              "fps": sum(len(m.get("fps", [])) for m in mods_in),
              "subs": sum(len(f.get("subs", [])) for m in mods_in for f in m.get("fps", [])),
              "created": {"projects": 0, "modules": 0, "fps": 0, "subs": 0},
              "updated": {"modules": 0, "fps": 0}}

    with db.tx(dim_db) as cur:
        if mode == "overwrite" and not project_id:
            report["backup"] = dump_dimension_backup(dim_db, tag="pre_overwrite")
            _wipe(cur)
            cur.execute("INSERT INTO projects (project_code, client_name, requirement_id, requirement_name, status) VALUES (%s,%s,%s,%s,%s)",
                        (proj.get("project_code", "imported"), proj.get("client_name", ""),
                         proj.get("requirement_id", ""), proj.get("requirement_name", "导入项目"), "draft"))
            target_pid = cur.lastrowid
            report["created"]["projects"] += 1
        else:
            if not project_id:
                raise ValueError("增量导入必须指定 project_id")
            cur.execute("SELECT id FROM projects WHERE id=%s", (project_id,))
            if not cur.fetchone():
                raise ValueError(f"project_id={project_id} 不存在")
            if mode == "overwrite":
                report["backup"] = dump_dimension_backup(dim_db, tag="pre_overwrite")
                _wipe(cur, project_id=project_id)
            target_pid = project_id

        for mod_order, mod in enumerate(mods_in, 1):
            cur.execute("SELECT id FROM modules WHERE project_id=%s AND level1=%s AND level2=%s AND level3=%s",
                        (target_pid, mod.get("level1", ""), mod.get("level2", ""), mod.get("level3", "")))
            hit = cur.fetchone()
            if hit:
                mid = hit["id"]
                cur.execute("UPDATE modules SET sort_order=%s WHERE id=%s", (mod_order, mid))
                report["updated"]["modules"] += 1
            else:
                cur.execute("INSERT INTO modules (project_id, level1, level2, level3, sort_order) VALUES (%s,%s,%s,%s,%s)",
                            (target_pid, mod.get("level1", ""), mod.get("level2", ""),
                             mod.get("level3", ""), mod_order))
                mid = cur.lastrowid
                report["created"]["modules"] += 1

            for fp_order, fp in enumerate(mod.get("fps", []), 1):
                cur.execute("SELECT id FROM fps WHERE module_id=%s AND fp_name=%s", (mid, fp["fp_name"]))
                hit = cur.fetchone()
                if hit:
                    fid = hit["id"]
                    cur.execute("UPDATE fps SET functional_user=%s, trigger_event=%s, sort_order=%s WHERE id=%s",
                                (fp.get("functional_user", ""), fp.get("trigger_event", ""), fp_order, fid))
                    cur.execute("DELETE FROM sub_processes WHERE fp_id=%s", (fid,))
                    report["updated"]["fps"] += 1
                else:
                    cur.execute("INSERT INTO fps (module_id, sort_order, functional_user, trigger_event, fp_name) VALUES (%s,%s,%s,%s,%s)",
                                (mid, fp_order, fp.get("functional_user", ""),
                                 fp.get("trigger_event", ""), fp["fp_name"]))
                    fid = cur.lastrowid
                    report["created"]["fps"] += 1
            for idx, sub in enumerate(fp.get("subs", []), 1):
                cur.execute("INSERT INTO sub_processes (fp_id, sort_order, description, data_move_type, data_group_name, data_attributes) VALUES (%s,%s,%s,%s,%s,%s)",
                            (fid, idx, sub.get("description", ""), sub.get("data_move_type", ""),
                             sub.get("data_group_name", ""), sub.get("data_attributes", "")))
                report["created"]["subs"] += 1
        report["project_id"] = target_pid
    return report


def _wipe(cur, project_id: int | None = None):
    from .xlsx_import import _wipe
    _wipe(cur, project_id)


def tree_to_rows(modules: list) -> list:
    """树 → 扁平行（供 group_tree 逆操作测试等场景）。"""
    rows = []
    for m in modules:
        for f in m.get("fps", []):
            for s in f.get("subs", []):
                rows.append({**s, **{k: m[k] for k in ("level1", "level2", "level3")}})
    return rows
