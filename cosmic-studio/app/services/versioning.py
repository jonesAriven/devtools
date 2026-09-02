"""版本管理：编写库导出快照 + sha256 指纹 + 版本链，禁覆盖上一版。

每个版本除 xlsx 外同步落一份结构化 JSON（dump_project），供版本间 diff。
"""
import hashlib
import json
import logging
import os
import re
from datetime import datetime

from .. import config, db
from .xlsx_export import export_xlsx

# 版本 label 进文件名：仅允许安全字符，杜绝 ../ 之类的路径穿越写文件
_SAFE_LABEL = re.compile(r"[^\w\-]")


def _next_seq(project_id: int) -> int:
    row = db.query(config.DB_STUDIO, "SELECT COALESCE(MAX(seq),0)+1 AS n FROM versions WHERE project_id=%s",
                   (project_id,), one=True)
    return row["n"]


def dump_project(db_name: str, project_id: int) -> dict:
    """结构化项目树（版本 diff 的对比载体）。

    匹配键约定：模块=(level1,level2,level3)；FP=(所属模块键, fp_name)；
    子过程=(所属FP键, move_type+description)。与增量导入的业务主键同源。
    """
    rows = db.query(db_name, """
        SELECT m.id AS mid, m.level1, m.level2, m.level3, f.id AS fid, f.fp_name,
               f.functional_user, f.trigger_event,
               s.data_move_type, s.description AS sub_desc, s.data_group_name, s.data_attributes
        FROM modules m
        LEFT JOIN fps f ON f.module_id = m.id
        LEFT JOIN sub_processes s ON s.fp_id = f.id
        WHERE m.project_id = %s
        ORDER BY m.sort_order, f.sort_order, s.sort_order
    """, (project_id,))
    modules, m_idx, f_idx = [], {}, {}
    for r in rows:
        if r["mid"] not in m_idx:
            m_idx[r["mid"]] = {"level1": r["level1"], "level2": r["level2"], "level3": r["level3"],
                               "fps": []}
            modules.append(m_idx[r["mid"]])
        if r["fid"] is None:
            continue
        if r["fid"] not in f_idx:
            f_idx[r["fid"]] = {"fp_name": r["fp_name"], "functional_user": r["functional_user"],
                               "trigger_event": r["trigger_event"], "subs": []}
            m_idx[r["mid"]]["fps"].append(f_idx[r["fid"]])
        if r["data_move_type"] is not None:
            f_idx[r["fid"]]["subs"].append({
                "move_type": r["data_move_type"], "description": r["sub_desc"],
                "group_name": r["data_group_name"], "attributes": r["data_attributes"]})
    return {"modules": modules}


def load_version_json(version_id: int):
    """读版本的结构化 JSON（存 versions.json_snapshot 列）；旧版本为 NULL（diff 时提示不可比）。"""
    row = db.query(config.DB_STUDIO, "SELECT json_snapshot FROM versions WHERE id=%s", (version_id,), one=True)
    if not row or not row["json_snapshot"]:
        return None
    return json.loads(row["json_snapshot"])


def diff_versions(a: dict, b: dict) -> dict:
    """两份项目树 diff → 模块/FP/子过程三层增删改 + 字段级差异。a=旧版本，b=新版本。"""

    def sub_key(s):
        return (s["move_type"], s["description"] or "")

    fp_diffs, sub_added, sub_removed, sub_modified = [], [], [], []
    a_fps, b_fps = {}, {}
    for m in a["modules"]:
        for f in m["fps"]:
            a_fps[(m["level1"], m["level2"], m["level3"], f["fp_name"])] = f
    for m in b["modules"]:
        for f in m["fps"]:
            b_fps[(m["level1"], m["level2"], m["level3"], f["fp_name"])] = f

    added = sorted(set(b_fps) - set(a_fps))
    removed = sorted(set(a_fps) - set(b_fps))
    for key in sorted(set(a_fps) & set(b_fps)):
        fa, fb = a_fps[key], b_fps[key]
        fd = []
        for col in ("functional_user", "trigger_event"):
            if (fa.get(col) or "") != (fb.get(col) or ""):
                fd.append({"field": col, "old": fa.get(col), "new": fb.get(col)})
        a_subs = {sub_key(s): s for s in fa["subs"]}
        b_subs = {sub_key(s): s for s in fb["subs"]}
        for k in sorted(set(b_subs) - set(a_subs)):
            sub_added.append({"fp": key[3], **b_subs[k]})
        for k in sorted(set(a_subs) - set(b_subs)):
            sub_removed.append({"fp": key[3], **a_subs[k]})
        for k in sorted(set(a_subs) & set(b_subs)):
            sa, sb = a_subs[k], b_subs[k]
            sd = []
            for col in ("group_name", "attributes"):
                if (sa.get(col) or "") != (sb.get(col) or ""):
                    sd.append({"field": col, "old": sa.get(col), "new": sb.get(col)})
            if sd:
                sub_modified.append({"fp": key[3], "move_type": k[0],
                                     "description": k[1], "diffs": sd})
        if fd:
            fp_diffs.append({"module": " / ".join(x or "" for x in key[:3]),
                             "fp_name": key[3], "diffs": fd})

    a_mods = {(m["level1"], m["level2"], m["level3"]) for m in a["modules"]}
    b_mods = {(m["level1"], m["level2"], m["level3"]) for m in b["modules"]}
    fmt = lambda k: " / ".join(x or "" for x in k)
    return {
        "summary": {"modules_added": len(b_mods - a_mods), "modules_removed": len(a_mods - b_mods),
                    "fps_added": len(added), "fps_removed": len(removed),
                    "fps_modified": len(fp_diffs),
                    "subs_added": len(sub_added), "subs_removed": len(sub_removed),
                    "subs_modified": len(sub_modified)},
        "modules_added": [fmt(k) for k in sorted(b_mods - a_mods)],
        "modules_removed": [fmt(k) for k in sorted(a_mods - b_mods)],
        "fps_added": [{"module": fmt(k[:3]), "fp_name": k[3]} for k in added],
        "fps_removed": [{"module": fmt(k[:3]), "fp_name": k[3]} for k in removed],
        "fps_modified": fp_diffs,
        "subs_added": sub_added, "subs_removed": sub_removed, "subs_modified": sub_modified,
    }


def snapshot(dim_db: str, project_id: int, label: str = "", changelog: str = "",
             author: str = "") -> dict:
    content, meta = export_xlsx(dim_db, project_id, author=author)
    seq = _next_seq(project_id)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    if not label:
        label = f"v{seq}"
    label = _SAFE_LABEL.sub("-", (label or "")[:40])  # 防穿越 + 限长
    os.makedirs(config.VERSIONS_DIR, exist_ok=True)
    fname = f"{dim_db}_p{project_id}_{label}_{ts}.xlsx"
    path = os.path.join(config.VERSIONS_DIR, fname)
    with open(path, "wb") as f:
        f.write(content)
    sha = hashlib.sha256(content).hexdigest()
    vid = db.execute(config.DB_STUDIO, """
        INSERT INTO versions (dimension, project_id, seq, label, sha256, file_path, file_size, changelog, created_at)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,NOW())
    """, (dim_db, project_id, seq, label, sha, path, len(content), changelog))
    # 结构化项目树随版本入库（versions.json_snapshot）：版本 diff 的对比载体。
    # 旧版本此列为 NULL，diff 时提示不可比。
    try:
        tree_json = json.dumps(dump_project(dim_db, project_id), ensure_ascii=False)
        if tree_json:
            db.execute(config.DB_STUDIO,
                       "UPDATE versions SET json_snapshot=%s WHERE id=%s", (tree_json, vid))
    except Exception:
        logging.exception("version json dump failed (xlsx snapshot 仍成功)")
    return {"id": vid, "seq": seq, "label": label, "sha256": sha,
            "file": fname, "size": len(content), **meta}


def list_versions(dim_db: str, project_id: int) -> list:
    return db.query(config.DB_STUDIO,
                    "SELECT id, seq, label, sha256, file_path, file_size, changelog, created_at "
                    "FROM versions WHERE dimension=%s AND project_id=%s ORDER BY seq DESC",
                    (dim_db, project_id))


def get_version_file(version_id: int) -> str | None:
    row = db.query(config.DB_STUDIO, "SELECT file_path, sha256 FROM versions WHERE id=%s",
                   (version_id,), one=True)
    if not row or not os.path.exists(row["file_path"]):
        return None
    return row["file_path"]
