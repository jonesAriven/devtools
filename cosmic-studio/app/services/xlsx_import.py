"""xlsx 导入：增量 upsert / 全量覆盖，合并单元格传播逻辑移植 import_from_xlsx.py。

列映射（与 Hermes 导入脚本一致）：A=客户需求(不入库,仅覆盖模式解析项目信息)，
B/C/D=一二三级模块，E=功能用户，F=触发事件，G=功能过程，H=描述，I=数据移动，
J=数据组，K=数据属性；数据从第 5 行起，L-P 展示列不导入。

业务主键：模块=(project, level1+level2+level3)；FP=(module, fp_name)；
FP 命中主键时整体重写 functional_user/trigger_event + 子过程全删重插（防半新半旧）。
增量=按主键 upsert 保留库内其他数据（必须指定 project_id）；
覆盖=先备份再清（指定项目或整库）重灌，一个 xlsx = 一个项目。
"""
import io
import re

from openpyxl import load_workbook

from .. import db

REQ_LABEL_RE = re.compile(r"^【(.+?)】(.+)$")


def parse_xlsx_rows(xlsx_bytes: bytes) -> dict:
    """→ {requirement_label, rows:[{level1..3,user,event,fp_name,description,move_type,group_name,attributes}]}"""
    wb = load_workbook(io.BytesIO(xlsx_bytes), data_only=True)
    ws = wb.active
    prev = {c: "" for c in range(1, 17)}
    rows = []
    req_label = ""
    for r in range(5, ws.max_row + 1):
        vals = {}
        all_none = True
        for c in range(1, 12):
            v = _merged_value(ws, r, c, prev[c])
            prev[c] = v
            if v:
                all_none = False
            vals[c] = v
        if all_none:
            continue
        if not req_label and vals[1]:
            req_label = vals[1]
        rows.append({
            "level1": vals[2], "level2": vals[3], "level3": vals[4],
            "user": vals[5], "event": vals[6], "fp_name": vals[7],
            "description": vals[8], "move_type": (vals[9] or "").strip().upper()[:1],
            "group_name": vals[10], "attributes": vals[11],
        })
    return {"requirement_label": req_label, "rows": rows}


def _merged_value(ws, row, col, prev):
    v = ws.cell(row, col).value
    if v is not None:
        return str(v).strip()
    for mc in ws.merged_cells.ranges:
        if mc.min_row <= row <= mc.max_row and mc.min_col <= col <= mc.max_col:
            first = ws.cell(mc.min_row, col).value
            if first is not None:
                return str(first).strip()
    return prev


def group_tree(rows: list) -> list:
    """行序 → [模块[FP[子过程]]] 树，保持文件顺序。"""
    modules, cur_m, cur_f = [], None, None
    for r in rows:
        mkey = (r["level1"], r["level2"], r["level3"])
        fkey = (r["fp_name"], r["user"], r["event"])
        if mkey != cur_m:
            cur_m, cur_f = mkey, None
            modules.append({"level1": r["level1"], "level2": r["level2"],
                            "level3": r["level3"], "fps": []})
        if fkey != cur_f:
            cur_f = fkey
            modules[-1]["fps"].append({"fp_name": r["fp_name"], "user": r["user"],
                                       "event": r["event"], "subs": []})
        modules[-1]["fps"][-1]["subs"].append(r)
    return modules


def _parse_project_meta(parsed: dict, fallback: dict | None) -> dict:
    """从 A 列标签【需求编号】需求名 解析项目信息，回退到调用方传入值。"""
    label = parsed.get("requirement_label") or ""
    m = REQ_LABEL_RE.match(label)
    rid, rname = (m.group(1), m.group(2)) if m else ("", label or "导入项目")
    fb = fallback or {}
    return {
        "project_code": fb.get("project_code") or "imported",
        "client_name": fb.get("client_name") or "",
        "requirement_id": fb.get("requirement_id") or rid,
        "requirement_name": fb.get("requirement_name") or rname,
    }


def import_xlsx(dim_db: str, xlsx_bytes: bytes, mode: str = "incremental",
                project_id: int | None = None, project_meta: dict | None = None) -> dict:
    parsed = parse_xlsx_rows(xlsx_bytes)
    rows = parsed["rows"]
    if not rows:
        raise ValueError("xlsx 无有效数据行（从第5行起读A-K列）")
    modules = group_tree(rows)
    report = {"mode": mode, "modules": len(modules),
              "fps": sum(len(m["fps"]) for m in modules),
              "subs": sum(len(f["subs"]) for m in modules for f in m["fps"]),
              "created": {"projects": 0, "modules": 0, "fps": 0, "subs": 0},
              "updated": {"modules": 0, "fps": 0}}

    with db.tx(dim_db) as cur:
        if mode == "overwrite":
            from .json_io import dump_dimension_backup
            report["backup"] = dump_dimension_backup(dim_db, tag="pre_overwrite")
            if project_id:
                cur.execute("SELECT id FROM projects WHERE id=%s", (project_id,))
                if not cur.fetchone():
                    raise ValueError(f"project_id={project_id} 不存在")
                _wipe(cur, project_id=project_id)
                target_pid = project_id
            else:
                _wipe(cur)
                meta = _parse_project_meta(parsed, project_meta)
                cur.execute("INSERT INTO projects (project_code, client_name, requirement_id, requirement_name, status) VALUES (%s,%s,%s,%s,%s)",
                            (meta["project_code"], meta["client_name"],
                             meta["requirement_id"], meta["requirement_name"], "draft"))
                target_pid = cur.lastrowid
                report["created"]["projects"] += 1
        else:
            if not project_id:
                raise ValueError("增量导入必须指定 project_id（写入哪个项目）")
            cur.execute("SELECT id FROM projects WHERE id=%s", (project_id,))
            if not cur.fetchone():
                raise ValueError(f"project_id={project_id} 不存在")
            target_pid = project_id

        for mod_order, mod in enumerate(modules, 1):
            cur.execute("SELECT id FROM modules WHERE project_id=%s AND level1=%s AND level2=%s AND level3=%s",
                        (target_pid, mod["level1"], mod["level2"], mod["level3"]))
            hit = cur.fetchone()
            if hit:
                mid = hit["id"]
                cur.execute("UPDATE modules SET sort_order=%s WHERE id=%s", (mod_order, mid))
                report["updated"]["modules"] += 1
            else:
                cur.execute("INSERT INTO modules (project_id, level1, level2, level3, sort_order) VALUES (%s,%s,%s,%s,%s)",
                            (target_pid, mod["level1"], mod["level2"], mod["level3"], mod_order))
                mid = cur.lastrowid
                report["created"]["modules"] += 1

            for fp_order, fp in enumerate(mod["fps"], 1):
                cur.execute("SELECT id FROM fps WHERE module_id=%s AND fp_name=%s", (mid, fp["fp_name"]))
                hit = cur.fetchone()
                if hit:
                    fid = hit["id"]
                    cur.execute("UPDATE fps SET functional_user=%s, trigger_event=%s, sort_order=%s WHERE id=%s",
                                (fp["user"], fp["event"], fp_order, fid))
                    cur.execute("DELETE FROM sub_processes WHERE fp_id=%s", (fid,))
                    cur.execute("DELETE FROM screenshots WHERE fp_id=%s", (fid,))
                    report["updated"]["fps"] += 1
                else:
                    cur.execute("INSERT INTO fps (module_id, sort_order, functional_user, trigger_event, fp_name) VALUES (%s,%s,%s,%s,%s)",
                                (mid, fp_order, fp["user"], fp["event"], fp["fp_name"]))
                    fid = cur.lastrowid
                    report["created"]["fps"] += 1
                for idx, sub in enumerate(fp["subs"], 1):
                    cur.execute("INSERT INTO sub_processes (fp_id, sort_order, description, data_move_type, data_group_name, data_attributes) VALUES (%s,%s,%s,%s,%s,%s)",
                                (fid, idx, sub["description"], sub["move_type"],
                                 sub["group_name"], sub["attributes"]))
                    report["created"]["subs"] += 1
        report["project_id"] = target_pid
    return report


def _wipe(cur, project_id: int | None = None):
    """清库（整维度或单项目），顺序：截图→子过程→FP→模块→项目。"""
    if project_id:
        cur.execute("DELETE FROM screenshots WHERE fp_id IN (SELECT f.id FROM fps f JOIN modules m ON m.id=f.module_id WHERE m.project_id=%s)", (project_id,))
        cur.execute("DELETE FROM sub_processes WHERE fp_id IN (SELECT f.id FROM fps f JOIN modules m ON m.id=f.module_id WHERE m.project_id=%s)", (project_id,))
        cur.execute("DELETE FROM fps WHERE module_id IN (SELECT id FROM modules WHERE project_id=%s)", (project_id,))
        cur.execute("DELETE FROM modules WHERE project_id=%s", (project_id,))
        cur.execute("DELETE FROM projects WHERE id=%s", (project_id,))
    else:
        for t in ("screenshots", "sub_processes", "fps", "modules", "projects"):
            cur.execute(f"DELETE FROM {t}")
