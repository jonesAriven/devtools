"""变更审计：编写库结构化数据的行级写操作流水。

设计：
- change_log 与业务写操作同事务写入（调用方传入事务 cur），要么全成要么全无；
- 只审计 cosmic_active（编写库）。归档库数据走人工导入通道，导入本身有
  import_jobs 记录，行级审计无意义；
- update 记字段级 diff（field_name/old/new），create/delete 记整行摘要（old/new
  存关键列 JSON），批量操作（导入/复制/快照）记 project 级单条摘要，避免流水爆炸。
"""
import json

from .. import config

# update 端点之外，这些"摘要型"动作也进时间线
SUMMARY_ACTIONS = ("copy", "import", "version", "diversify", "set_primary")


def ensure_schema():
    """启动期幂等迁移：change_log 建表 + 四张业务表加 last_modified 列。"""
    from .. import db
    db.execute(config.DB_ACTIVE, """
        CREATE TABLE IF NOT EXISTS change_log (
          id INT AUTO_INCREMENT PRIMARY KEY,
          project_id INT NOT NULL DEFAULT 0,
          target_type VARCHAR(8) NOT NULL DEFAULT 'project',
          target_id INT NOT NULL DEFAULT 0,
          action VARCHAR(16) NOT NULL,
          field_name VARCHAR(64) NOT NULL DEFAULT '',
          old_value TEXT,
          new_value TEXT,
          changed_by VARCHAR(64) NOT NULL DEFAULT '',
          changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          KEY idx_proj (project_id, id),
          KEY idx_target (target_type, target_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """)
    for table in ("projects", "modules", "fps", "sub_processes"):
        col = db.query(config.DB_ACTIVE,
                       "SELECT COUNT(*) AS n FROM information_schema.columns "
                       "WHERE table_schema=%s AND table_name=%s AND column_name='last_modified_by'",
                       (config.DB_ACTIVE, table), one=True)["n"]
        if not col:
            db.execute(config.DB_ACTIVE,
                       f"ALTER TABLE {table} ADD COLUMN last_modified_by VARCHAR(64) NULL, "
                       f"ADD COLUMN last_modified_at DATETIME NULL")
            db.execute(config.DB_ACTIVE,
                       f"UPDATE {table} SET last_modified_by='(存量迁移前)', last_modified_at=NOW() "
                       f"WHERE last_modified_by IS NULL")


def log(cur, project_id: int, target_type: str, target_id: int, action: str,
        field: str = "", old=None, new=None, by: str = ""):
    """在调用方事务内写一条审计。old/new 非标量时由调用方先 JSON 序列化。"""
    cur.execute(
        "INSERT INTO change_log (project_id, target_type, target_id, action, field_name, "
        "old_value, new_value, changed_by) VALUES (%s,%s,%s,%s,%s,%s,%s,%s)",
        (project_id, target_type, target_id, action, field,
         None if old is None else str(old)[:2000],
         None if new is None else str(new)[:2000], by or ""))


def diff_log(cur, project_id: int, target_type: str, target_id: int,
             before: dict, changes: dict, by: str, label_cols=("fp_name", "description")):
    """字段级 update 审计：只记真正发生变化的字段；无变化不写流水。

    before = 更新前的整行；changes = 本次要写的 {col: new_value}。
    顺带刷目标行的 last_modified_by/at（同事务）。
    """
    table = {"fp": "fps", "sub": "sub_processes", "module": "modules", "project": "projects"}[target_type]
    pk = {"fp": "id", "sub": "id", "module": "id", "project": "id"}[target_type]
    changed = 0
    for k, v in changes.items():
        old = before.get(k)
        if str(old) == str(v):
            continue
        log(cur, project_id, target_type, target_id, "update", field=k, old=old, new=v, by=by)
        changed += 1
    if changed:
        cur.execute(f"UPDATE {table} SET last_modified_by=%s, last_modified_at=NOW() WHERE {pk}=%s",
                    (by, target_id))
    return changed


def project_of_fp(cur, fid: int) -> int:
    cur.execute("SELECT m.project_id AS pid FROM fps f JOIN modules m ON m.id=f.module_id WHERE f.id=%s", (fid,))
    row = cur.fetchone()
    return row["pid"] if row else 0


def project_of_sub(cur, sid: int) -> int:
    cur.execute("SELECT m.project_id AS pid FROM sub_processes s JOIN fps f ON f.id=s.fp_id "
                "JOIN modules m ON m.id=f.module_id WHERE s.id=%s", (sid,))
    row = cur.fetchone()
    return row["pid"] if row else 0
