"""版本管理：编写库导出快照 + sha256 指纹 + 版本链，禁覆盖上一版。"""
import hashlib
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
