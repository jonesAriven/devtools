"""维度路由工厂：active（编写）/ archive（归档）共用同一套 CRUD+导入导出+lint+derive。

archive 差异：写操作（建项目/模块/FP/子过程/derive/版本）仅限导入通道（导入本质是写，
由导入接口完成），因此 archive 只挂只读 + 导入导出端点；active 挂全部。
"""
import os

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile
from fastapi.responses import FileResponse, Response
from pydantic import BaseModel, field_validator

from .. import config, db
from ..auth import ROLE_RANK, require_role
from ..engines import derive, linter
from ..services import json_io, versioning, xlsx_export, xlsx_import


class ProjectIn(BaseModel):
    project_code: str = ""
    client_name: str = ""
    requirement_id: str
    requirement_name: str
    client_contract: str = ""
    batch_no: str = ""

    @field_validator("requirement_id", "requirement_name")
    @classmethod
    def _not_blank(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("需求编号/名称不能为空")
        return v


class ModuleIn(BaseModel):
    level1: str
    level2: str
    level3: str


class FPIn(BaseModel):
    module_id: int
    name: str
    user: str | None = None
    event: str | None = None


class FPUpdate(BaseModel):
    name: str | None = None
    user: str | None = None
    event: str | None = None


class SubIn(BaseModel):
    move_type: str
    desc: str | None = None
    group_name: str | None = None
    attributes: str


class VersionIn(BaseModel):
    label: str = ""
    changelog: str = ""
    author: str = ""


def make_dimension_router(dim: str, db_name: str, writable: bool) -> APIRouter:
    # 路由级默认：所有端点要求登录（viewer 起）；写端点在签名上再升权
    r = APIRouter(prefix=f"/api/{dim}", tags=[dim],
                  dependencies=[Depends(require_role("viewer"))])

    # ── 项目 ──
    @r.get("/projects")
    def list_projects():
        return db.query(db_name, """
            SELECT p.*,
              (SELECT COUNT(*) FROM modules m WHERE m.project_id=p.id) AS module_count,
              (SELECT COUNT(*) FROM fps f JOIN modules m ON m.id=f.module_id WHERE m.project_id=p.id) AS fp_count,
              (SELECT COUNT(*) FROM sub_processes sp JOIN fps f ON f.id=sp.fp_id JOIN modules m ON m.id=f.module_id WHERE m.project_id=p.id) AS sub_count
            FROM projects p ORDER BY p.id
        """)

    @r.post("/projects", status_code=201)
    def create_project(body: ProjectIn, user: dict = Depends(require_role("editor"))):
        if not writable:
            raise HTTPException(403, "归档库只读：归档数据请走导入通道")
        pid = db.execute(db_name, """
            INSERT INTO projects (project_code, client_name, requirement_id, requirement_name, client_contract, batch_no, status)
            VALUES (%s,%s,%s,%s,%s,%s,'draft')
        """, (body.project_code, body.client_name, body.requirement_id,
              body.requirement_name, body.client_contract, body.batch_no))
        return {"id": pid}

    @r.get("/projects/{pid}/tree")
    def project_tree(pid: int):
        tree = xlsx_export.load_project_tree(db_name, pid)
        if not tree:
            raise HTTPException(404, "项目不存在")
        return tree

    @r.delete("/projects/{pid}")
    def delete_project(pid: int, confirm: str = "", user: dict = Depends(require_role("admin"))):
        if not writable:
            raise HTTPException(403, "归档库只读")
        if confirm != dim:
            raise HTTPException(428, f"删除项目需 confirm={dim} 二次确认")
        with db.tx(db_name) as cur:
            cur.execute("DELETE FROM screenshots WHERE fp_id IN (SELECT f.id FROM fps f JOIN modules m ON m.id=f.module_id WHERE m.project_id=%s)", (pid,))
            cur.execute("DELETE FROM sub_processes WHERE fp_id IN (SELECT f.id FROM fps f JOIN modules m ON m.id=f.module_id WHERE m.project_id=%s)", (pid,))
            cur.execute("DELETE FROM fps WHERE module_id IN (SELECT id FROM modules WHERE project_id=%s)", (pid,))
            cur.execute("DELETE FROM modules WHERE project_id=%s", (pid,))
            cur.execute("DELETE FROM projects WHERE id=%s", (pid,))
        return {"deleted": pid}

    # ── 模块 ──
    @r.post("/projects/{pid}/modules", status_code=201)
    def create_module(pid: int, body: ModuleIn, user: dict = Depends(require_role("editor"))):
        if not writable:
            raise HTTPException(403, "归档库只读")
        n = db.query(db_name, "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM modules WHERE project_id=%s",
                     (pid,), one=True)["n"]
        mid = db.execute(db_name,
                         "INSERT INTO modules (project_id, level1, level2, level3, sort_order) VALUES (%s,%s,%s,%s,%s)",
                         (pid, body.level1, body.level2, body.level3, n))
        return {"id": mid}

    @r.delete("/modules/{mid}")
    def delete_module(mid: int, cascade: bool = False, user: dict = Depends(require_role("editor"))):
        if not writable:
            raise HTTPException(403, "归档库只读")
        fps = db.query(db_name, "SELECT id FROM fps WHERE module_id=%s", (mid,))
        if fps and not cascade:
            raise HTTPException(409, f"模块下有 {len(fps)} 个FP，需 cascade=true")
        with db.tx(db_name) as cur:
            for f in fps:
                cur.execute("DELETE FROM screenshots WHERE fp_id=%s", (f["id"],))
                cur.execute("DELETE FROM sub_processes WHERE fp_id=%s", (f["id"],))
            cur.execute("DELETE FROM fps WHERE module_id=%s", (mid,))
            cur.execute("DELETE FROM modules WHERE id=%s", (mid,))
        return {"deleted": True}

    # ── FP ──
    @r.post("/projects/{pid}/fps", status_code=201)
    def create_fp(pid: int, body: FPIn, user: dict = Depends(require_role("editor"))):
        if not writable:
            raise HTTPException(403, "归档库只读")
        mod = db.query(db_name, "SELECT * FROM modules WHERE id=%s AND project_id=%s",
                       (body.module_id, pid), one=True)
        if not mod:
            raise HTTPException(404, "模块不存在")
        for w in linter.forbidden_words():
            if w in body.name:
                raise HTTPException(422, f"FP名含禁词'{w}': {body.name}")
        verbs = derive.allowed_verbs()
        if not any(body.name.startswith(v) for v in verbs):
            raise HTTPException(422, f"FP名应以动词开头: {verbs}")
        user, event = body.user, body.event
        if user is None or event is None:
            fu = derive.derive_functional_user(mod["level3"])
            initiator = derive.initiator_of(fu)
            user = user or fu
            event = event or derive.derive_trigger_event(body.name, initiator)
        n = db.query(db_name, "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM fps WHERE module_id=%s",
                     (body.module_id,), one=True)["n"]
        with db.tx(db_name) as cur:
            cur.execute("INSERT INTO fps (module_id, sort_order, functional_user, trigger_event, fp_name) VALUES (%s,%s,%s,%s,%s)",
                        (body.module_id, n, user, event, body.name))
            fid = cur.lastrowid
            # 标准子过程自动展开（EW/ERX），属性留空待填
            for i, (mt, desc, group) in enumerate(
                    derive.standard_subs_for(body.name, derive.initiator_of(user)), 1):
                cur.execute("INSERT INTO sub_processes (fp_id, sort_order, description, data_move_type, data_group_name, data_attributes) VALUES (%s,%s,%s,%s,%s,'')",
                            (fid, i, desc, mt, group))
        return {"id": fid, "user": user, "event": event}

    @r.put("/fps/{fid}")
    def update_fp(fid: int, body: FPUpdate, user: dict = Depends(require_role("editor"))):
        if not writable:
            raise HTTPException(403, "归档库只读")
        sets, params = [], []
        for col, v in (("fp_name", body.name), ("functional_user", body.user), ("trigger_event", body.event)):
            if v is not None:
                sets.append(f"{col}=%s")
                params.append(v)
        if not sets:
            raise HTTPException(422, "无更新字段")
        params.append(fid)
        db.execute(db_name, f"UPDATE fps SET {', '.join(sets)} WHERE id=%s", tuple(params))
        return {"updated": True}

    @r.delete("/fps/{fid}")
    def delete_fp(fid: int, cascade: bool = False, user: dict = Depends(require_role("editor"))):
        if not writable:
            raise HTTPException(403, "归档库只读")
        subs = db.query(db_name, "SELECT COUNT(*) AS n FROM sub_processes WHERE fp_id=%s", (fid,), one=True)
        if subs["n"] and not cascade:
            raise HTTPException(409, f"FP下有 {subs['n']} 个子过程，需 cascade=true")
        with db.tx(db_name) as cur:
            cur.execute("DELETE FROM screenshots WHERE fp_id=%s", (fid,))
            cur.execute("DELETE FROM sub_processes WHERE fp_id=%s", (fid,))
            cur.execute("DELETE FROM fps WHERE id=%s", (fid,))
        return {"deleted": True}

    # ── 子过程 ──
    @r.post("/fps/{fid}/subs", status_code=201)
    def create_sub(fid: int, body: SubIn, user: dict = Depends(require_role("editor"))):
        if not writable:
            raise HTTPException(403, "归档库只读")
        fp = db.query(db_name, "SELECT f.*, m.level3 FROM fps f JOIN modules m ON m.id=f.module_id WHERE f.id=%s",
                      (fid,), one=True)
        if not fp:
            raise HTTPException(404, "FP不存在")
        if body.move_type not in derive.allowed_sub_moves():
            raise HTTPException(422, f"move_type 必须是 {derive.allowed_sub_moves()}")
        expected = derive.expected_ewx(fp["fp_name"])
        if expected and body.move_type not in expected:
            raise HTTPException(422, f"{fp['fp_name']} 是{fp['fp_name'][:2]}类FP，只允许{list(expected)}子过程")
        if "," in body.attributes:
            raise HTTPException(422, "数据属性分隔符必须用、")
        fields = [f.strip() for f in body.attributes.split("、") if f.strip()]
        if len(fields) < 3:
            raise HTTPException(422, f"数据属性至少3个字段，当前{len(fields)}个")
        desc, group = body.desc, body.group_name
        if desc is None or group is None:
            fu = derive.derive_functional_user(fp["level3"])
            d_desc, d_group = derive.derive_sub_columns(fp["fp_name"], derive.initiator_of(fu), body.move_type)
            desc = desc or d_desc
            group = group or d_group
        n = db.query(db_name, "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM sub_processes WHERE fp_id=%s",
                     (fid,), one=True)["n"]
        sid = db.execute(db_name, "INSERT INTO sub_processes (fp_id, sort_order, description, data_move_type, data_group_name, data_attributes) VALUES (%s,%s,%s,%s,%s,%s)",
                         (fid, n, desc, body.move_type, group, body.attributes))
        return {"id": sid, "description": desc, "group_name": group}

    @r.put("/subs/{sid}")
    def update_sub(sid: int, body: dict, user: dict = Depends(require_role("editor"))):
        if not writable:
            raise HTTPException(403, "归档库只读")
        allowed = {k: v for k, v in body.items()
                   if k in ("description", "data_move_type", "data_group_name", "data_attributes")}
        if not allowed:
            raise HTTPException(422, "无有效更新字段")
        sets = ", ".join(f"{k}=%s" for k in allowed)
        db.execute(db_name, f"UPDATE sub_processes SET {sets} WHERE id=%s", (*allowed.values(), sid))
        return {"updated": True}

    @r.delete("/subs/{sid}")
    def delete_sub(sid: int, user: dict = Depends(require_role("editor"))):
        if not writable:
            raise HTTPException(403, "归档库只读")
        db.execute(db_name, "DELETE FROM sub_processes WHERE id=%s", (sid,))
        return {"deleted": True}

    # ── 推导 & 门禁 ──
    @r.post("/projects/{pid}/derive")
    def derive_all(pid: int, fix: bool = False, user: dict = Depends(require_role("viewer"))):
        if fix and ROLE_RANK.get(user["role"], 0) < ROLE_RANK["editor"]:
            raise HTTPException(403, "fix 修复需要 editor 及以上权限")
        issues = derive.derive_all(db_name, pid, fix=fix)
        return {"issues": issues, "count": len(issues), "fixed": fix}

    @r.get("/projects/{pid}/lint")
    def lint(pid: int, no_archive: bool = False):
        report = linter.lint_project(db_name, pid, include_archive_similarity=not no_archive)
        return report

    # ── 导入导出 ──
    @r.post("/import/xlsx")
    async def import_xlsx_ep(file: UploadFile = File(...),
                             mode: str = Query("incremental", pattern="^(incremental|overwrite)$"),
                             project_id: int | None = None,
                             project_code: str = "", client_name: str = "",
                             requirement_id: str = "", requirement_name: str = "",
                             confirm: str = "", user: dict = Depends(require_role("editor"))):
        if mode == "overwrite" and not project_id:
            if ROLE_RANK.get(user["role"], 0) < ROLE_RANK["admin"]:
                raise HTTPException(403, "整库覆盖导入需要 admin 权限")
            if confirm != dim:
                raise HTTPException(428, "整库覆盖导入需 confirm=<dimension> 二次确认")
        if mode == "overwrite" and project_id and ROLE_RANK.get(user["role"], 0) < ROLE_RANK["admin"]:
            raise HTTPException(403, "覆盖导入需要 admin 权限")
        meta = {k: v for k, v in dict(project_code=project_code, client_name=client_name,
                                      requirement_id=requirement_id,
                                      requirement_name=requirement_name).items() if v}
        report = xlsx_import.import_xlsx(db_name, await file.read(), mode=mode,
                                         project_id=project_id, project_meta=meta)
        job = _record_job(dim, mode, file.filename, report)
        report["job_id"] = job
        return report

    @r.post("/import/json")
    async def import_json_ep(payload: dict,
                             mode: str = Query("incremental", pattern="^(incremental|overwrite)$"),
                             project_id: int | None = None, confirm: str = "",
                             user: dict = Depends(require_role("editor"))):
        if mode == "overwrite" and not project_id:
            if ROLE_RANK.get(user["role"], 0) < ROLE_RANK["admin"]:
                raise HTTPException(403, "整库覆盖导入需要 admin 权限")
            if confirm != dim:
                raise HTTPException(428, "整库覆盖导入需 confirm=<dimension> 二次确认")
        report = json_io.import_json(db_name, payload, mode=mode, project_id=project_id)
        job = _record_job(dim, mode, "payload.json", report)
        report["job_id"] = job
        return report

    @r.get("/projects/{pid}/export/xlsx")
    def export_xlsx_ep(pid: int, author: str = ""):
        try:
            content, meta = xlsx_export.export_xlsx(db_name, pid, author=author)
        except ValueError as e:
            raise HTTPException(404, str(e))
        proj = db.query(db_name, "SELECT requirement_id, requirement_name FROM projects WHERE id=%s",
                        (pid,), one=True)
        from urllib.parse import quote
        fname = f"{proj['requirement_id']}_{proj['requirement_name'][:20]}_cosmic.xlsx"
        return Response(content, media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        headers={"Content-Disposition":
                                 f"attachment; filename*=UTF-8''{quote(fname)}"})

    @r.get("/projects/{pid}/export/json")
    def export_json_ep(pid: int):
        data = json_io.export_project_json(db_name, pid)
        if not data:
            raise HTTPException(404, "项目不存在")
        return data

    @r.get("/import/jobs")
    def import_jobs(limit: int = 20):
        return db.query(config.DB_STUDIO,
                        "SELECT * FROM import_jobs WHERE dimension=%s ORDER BY id DESC LIMIT %s",
                        (dim, limit))

    # ── 版本（active 专属；归档库版本无意义）──
    if writable:
        @r.post("/projects/{pid}/versions", status_code=201)
        def snapshot_version(pid: int, body: VersionIn, user: dict = Depends(require_role("editor"))):
            try:
                return versioning.snapshot(db_name, pid, body.label, body.changelog, body.author)
            except ValueError as e:
                raise HTTPException(404, str(e))

        @r.get("/projects/{pid}/versions")
        def list_versions_ep(pid: int):
            return versioning.list_versions(db_name, pid)

        @r.get("/versions/{vid}/download")
        def download_version(vid: int):
            path = versioning.get_version_file(vid)
            if not path:
                raise HTTPException(404, "版本文件不存在")
            return FileResponse(path, filename=os.path.basename(path))

    return r


def _record_job(dim: str, mode: str, filename: str, report: dict) -> int:
    import json as _json
    slim = {k: report.get(k) for k in ("mode", "modules", "fps", "subs", "created", "updated", "project_id")}
    return db.execute(config.DB_STUDIO, """
        INSERT INTO import_jobs (dimension, mode, filename, status, report, created_at)
        VALUES (%s,%s,%s,'done',%s,NOW())
    """, (dim, mode, filename, _json.dumps(slim, ensure_ascii=False)))
