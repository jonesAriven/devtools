"""维度路由工厂：active（编写）/ archive（归档）共用同一套 CRUD+导入导出+lint+derive。

archive 差异：写操作（建项目/模块/FP/子过程/derive/版本）仅限导入通道（导入本质是写，
由导入接口完成），因此 archive 只挂只读 + 导入导出端点；active 挂全部。

本文件原为「上帝路由」—— make_dimension_router 内联了 28 个端点 handler（564 行单函数）。
现拆分为模块级 handler 函数 + 工厂内 functools.partial 绑定闭包变量（db_name/dim/writable）注册，
签名 / 鉴权 / 请求响应模型 / 返回值完全不变，仅为可读性重构，无运行时行为变化。
"""
import functools
import os

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile
from fastapi.responses import FileResponse, Response
from pydantic import BaseModel, field_validator

from .. import config, db, paging
from ..auth import ROLE_RANK, require_role
from ..engines import derive, linter
from ..services import audit, json_io, project_copy, tree as tree_svc, versioning, xlsx_export, xlsx_import

# 项目列表可排序字段白名单（防止 SQL 注入）
PROJECT_SORTABLE = {
    "requirement_id": "t.requirement_id",
    "requirement_name": "t.requirement_name",
    "client_name": "t.client_name",
    "fp_count": "t.fp_count",
    "sub_count": "t.sub_count",
    "created_at": "t.created_at",
}
PROJECT_ORDER_DEFAULT = "t.requirement_id ASC, t.copy_no ASC"

# 项目列表内层 SQL（被 list_projects 使用，与维度无关，提到模块级常量）
# 列清单必须显式写：pt.* 会把 is_primary 带出来，和外层 p.is_primary 重名，
# MySQL 在派生表（本查询要包一层做 COUNT）里直接报 1060 Duplicate column name
# copy_no 必须基于全量 projects 计算，筛选只能加在外层，否则按关键词过滤后
# 「副本1」会被误标成「副本1」（而不是它真实的副本序号）
_PROJECTS_INNER = """
    SELECT pt.id, pt.project_code, pt.client_name, pt.requirement_id, pt.requirement_name,
           pt.client_contract, pt.batch_no, pt.status, pt.source_sheet,
           pt.created_at, pt.updated_at,
           pt.copy_no, pt.module_count, pt.fp_count, pt.sub_count,
           p.is_primary
    FROM (
      SELECT p.*,
        ROW_NUMBER() OVER (PARTITION BY p.requirement_id ORDER BY p.created_at, p.id) AS copy_no,
        (SELECT COUNT(*) FROM modules m WHERE m.project_id=p.id) AS module_count,
        (SELECT COUNT(*) FROM fps f JOIN modules m ON m.id=f.module_id WHERE m.project_id=p.id) AS fp_count,
        (SELECT COUNT(*) FROM sub_processes sp JOIN fps f ON f.id=sp.fp_id JOIN modules m ON m.id=f.module_id WHERE m.project_id=p.id) AS sub_count
      FROM projects p
    ) pt JOIN projects p ON p.id = pt.id
"""


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


# ───────────────────────────── 项目 ─────────────────────────────

def list_projects(db_name, dim, writable, page: int = 1, page_size: int = 20,
                  keyword: str = "", sort: str = "", order: str = "asc",
                  group_by_req: bool = False):
    """分页项目列表 → {list,total,page,page_size}。

    keyword 命中：需求编号 / 需求名称 / 客户 / 项目编码。

    group_by_req=true：按「需求」分页（total = 需求数）。
    前端编写库是按需求分组的树表，若按副本数切页，同一个需求的副本会被
    劈到两页上、分组显示就断了。这里先取一页需求编号，再取这些需求的全部副本。
    """
    cond, params = ["1=1"], []
    kw = (keyword or "").strip()
    if kw:
        like = f"%{kw}%"
        cond.append("(t.requirement_id LIKE %s OR t.requirement_name LIKE %s "
                    "OR t.client_name LIKE %s OR t.project_code LIKE %s)")
        params.extend([like, like, like, like])
    where = " AND ".join(cond)

    col = PROJECT_SORTABLE.get(sort or "")
    order_sql = PROJECT_ORDER_DEFAULT
    if col:
        order_sql = f"{col} {'DESC' if order.lower() == 'desc' else 'ASC'}, t.id ASC"

    page_n, size_n, offset = paging.normalize(page, page_size)
    params = tuple(params)

    if group_by_req:
        total = db.query(db_name,
                         f"SELECT COUNT(*) AS total FROM (SELECT requirement_id FROM projects p "
                         f"WHERE {where.replace('t.', 'p.')} GROUP BY requirement_id) x",
                         params, one=True)["total"]
        reqs = db.query(db_name,
                        f"SELECT requirement_id FROM projects p "
                        f"WHERE {where.replace('t.', 'p.')} GROUP BY requirement_id "
                        f"ORDER BY requirement_id LIMIT %s OFFSET %s",
                        (*params, size_n, offset))
        if not reqs:
            return paging.wrap([], total, page_n, size_n)
        ph = ",".join(["%s"] * len(reqs))
        rows = db.query(db_name,
                        f"SELECT * FROM ({_PROJECTS_INNER}) AS t "
                        f"WHERE t.requirement_id IN ({ph}) ORDER BY {order_sql}",
                        tuple(r["requirement_id"] for r in reqs))
        return paging.wrap(rows, total, page_n, size_n)

    total = db.query(db_name,
                     f"SELECT COUNT(*) AS total FROM ({_PROJECTS_INNER}) AS t "
                     f"WHERE {where}", params, one=True)["total"]
    rows = db.query(db_name,
                    f"SELECT * FROM ({_PROJECTS_INNER}) AS t WHERE {where} "
                    f"ORDER BY {order_sql} LIMIT %s OFFSET %s",
                    (*params, size_n, offset))
    return paging.wrap(rows, total, page_n, size_n)


def copy_project(db_name, dim, writable, pid: int, user: dict = Depends(require_role("editor"))):
    """深拷贝副本：同需求新工作线（活数据，可继续编写）。仅编写库。

    实现统一委托 services.project_copy.copy_project（评审手动/自动修订也复用同一份）。
    """
    if not writable:
        raise HTTPException(403, "归档库只读")
    new_id = project_copy.copy_project(db_name, pid)
    with db.tx(db_name) as cur:
        audit.log(cur, new_id, "project", new_id, "copy",
                  new={"copied_from": pid}, by=user["username"])
    return new_id


def set_primary(db_name, dim, writable, pid: int, user: dict = Depends(require_role("editor"))):
    if not writable:
        raise HTTPException(403, "归档库只读")
    src = db.query(db_name, "SELECT requirement_id FROM projects WHERE id=%s", (pid,), one=True)
    if not src:
        raise HTTPException(404, "项目不存在")
    with db.tx(db_name) as cur:
        cur.execute("UPDATE projects SET is_primary=0 WHERE requirement_id=%s", (src["requirement_id"],))
        cur.execute("UPDATE projects SET is_primary=1, last_modified_by=%s, last_modified_at=NOW() WHERE id=%s",
                    (user["username"], pid))
        audit.log(cur, pid, "project", pid, "set_primary", field="is_primary",
                  old=0, new=1, by=user["username"])
    return {"primary": pid}


def diff_project(db_name, dim, writable, pid: int, against: int, user: dict = Depends(require_role("viewer"))):
    """副本间 FP 名集合对比。"""
    def fp_names(p):
        return {r["fp_name"] for r in db.query(db_name, """
            SELECT f.fp_name FROM fps f JOIN modules m ON m.id=f.module_id WHERE m.project_id=%s""", (p,))}
    a, b = fp_names(pid), fp_names(against)
    return {"only_in_this": sorted(a - b), "only_in_main": sorted(b - a),
            "common": len(a & b), "this_count": len(a), "main_count": len(b)}


def create_project(db_name, dim, writable, body: ProjectIn, user: dict = Depends(require_role("editor"))):
    if not writable:
        raise HTTPException(403, "归档库只读：归档数据请走导入通道")
    pid = db.execute(db_name, """
        INSERT INTO projects (project_code, client_name, requirement_id, requirement_name, client_contract, batch_no, status)
        VALUES (%s,%s,%s,%s,%s,%s,'draft')
    """, (body.project_code, body.client_name, body.requirement_id,
          body.requirement_name, body.client_contract, body.batch_no))
    with db.tx(db_name) as cur:
        audit.log(cur, pid, "project", pid, "create",
                  new={"requirement_id": body.requirement_id, "requirement_name": body.requirement_name},
                  by=user["username"])
    return {"id": pid}


def project_tree(db_name, dim, writable, pid: int, module_page: int = 1, module_page_size: int = 10,
                 keyword: str = ""):
    """分页项目树 → {project, modules, total, page, page_size, stats}。

    分页粒度 = 顶层模块（FP / 子过程跟随），keyword 命中模块一/二/三级名。
    原实现是 N+1 查询，改走 services/tree.py 的 3 查询 + LIMIT 下推版本。
    """
    data = tree_svc.load_paged(db_name, pid, module_page, module_page_size, keyword)
    if not data:
        raise HTTPException(404, "项目不存在")
    return data


def _purge_project(cur, pid: int, with_reviews: bool):
    """按外键依赖序清空一个项目的全部数据（单删/批量删除共用）。

    with_reviews：review_items 只存在于 active 库（归档库无此表），删编写库
    项目时连带清评审项，避免悬挂；归档库项目跳过。
    """
    if with_reviews:
        cur.execute("DELETE FROM review_items WHERE project_id=%s", (pid,))
    cur.execute("DELETE FROM screenshots WHERE fp_id IN (SELECT f.id FROM fps f JOIN modules m ON m.id=f.module_id WHERE m.project_id=%s)", (pid,))
    cur.execute("DELETE FROM sub_processes WHERE fp_id IN (SELECT f.id FROM fps f JOIN modules m ON m.id=f.module_id WHERE m.project_id=%s)", (pid,))
    cur.execute("DELETE FROM fps WHERE module_id IN (SELECT id FROM modules WHERE project_id=%s)", (pid,))
    cur.execute("DELETE FROM modules WHERE project_id=%s", (pid,))
    cur.execute("DELETE FROM projects WHERE id=%s", (pid,))


def delete_project(db_name, dim, writable, pid: int, confirm: str = "", user: dict = Depends(require_role("admin"))):
    # 归档库也允许删除：只读约束约束的是"内容编写"，删除是库级数据管理动作（admin 职权）
    if confirm != dim:
        raise HTTPException(428, f"删除需求需 confirm={dim} 二次确认")
    proj = db.query(db_name, "SELECT requirement_id, requirement_name FROM projects WHERE id=%s", (pid,), one=True)
    if not proj:
        raise HTTPException(404, f"需求不存在: id={pid}")
    with db.tx(db_name) as cur:
        _purge_project(cur, pid, with_reviews=writable)
        audit.log(cur, pid, "project", pid, "delete",
                  old={"requirement_id": proj["requirement_id"], "requirement_name": proj["requirement_name"]},
                  by=user["username"])
    return {"deleted": pid}


class BulkIdsIn(BaseModel):
    ids: list[int]
    confirm: str = ""


def bulk_delete_projects(db_name, dim, writable, body: BulkIdsIn, user: dict = Depends(require_role("admin"))):
    """批量删除需求（每个独立事务：单条失败不中断其余，失败原因逐条回报）。"""
    if body.confirm != dim:
        raise HTTPException(428, f"批量删除需 confirm={dim} 二次确认")
    deleted, failed = [], []
    for pid in body.ids:
        try:
            with db.tx(db_name) as cur:
                cur.execute("SELECT id FROM projects WHERE id=%s", (pid,))
                if not cur.fetchone():
                    raise HTTPException(404, f"需求不存在: id={pid}")
                _purge_project(cur, pid, with_reviews=writable)
                audit.log(cur, pid, "project", pid, "delete", old={"bulk": True}, by=user["username"])
            deleted.append(pid)
        except HTTPException as e:
            failed.append({"id": pid, "reason": e.detail})
    return {"deleted": deleted, "failed": failed,
            "deleted_count": len(deleted), "failed_count": len(failed)}


# ───────────────────────────── 模块 ─────────────────────────────

def create_module(db_name, dim, writable, pid: int, body: ModuleIn, user: dict = Depends(require_role("editor"))):
    if not writable:
        raise HTTPException(403, "归档库只读")
    proj = db.query(db_name, "SELECT id FROM projects WHERE id=%s", (pid,), one=True)
    if not proj:
        raise HTTPException(404, f"项目不存在: {pid}")
    n = db.query(db_name, "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM modules WHERE project_id=%s",
                 (pid,), one=True)["n"]
    mid = db.execute(db_name,
                     "INSERT INTO modules (project_id, level1, level2, level3, sort_order) VALUES (%s,%s,%s,%s,%s)",
                     (pid, body.level1, body.level2, body.level3, n))
    with db.tx(db_name) as cur:
        audit.log(cur, pid, "module", mid, "create",
                  new={"level1": body.level1, "level2": body.level2, "level3": body.level3},
                  by=user["username"])
    return {"id": mid}


def delete_module(db_name, dim, writable, mid: int, cascade: bool = False, user: dict = Depends(require_role("editor"))):
    if not writable:
        raise HTTPException(403, "归档库只读")
    fps = db.query(db_name, "SELECT id FROM fps WHERE module_id=%s", (mid,))
    if fps and not cascade:
        raise HTTPException(409, f"模块下有 {len(fps)} 个FP，需 cascade=true")
    mrow = db.query(db_name, "SELECT project_id FROM modules WHERE id=%s", (mid,), one=True)
    pid_of_module = mrow["project_id"] if mrow else 0
    with db.tx(db_name) as cur:
        for f in fps:
            cur.execute("DELETE FROM screenshots WHERE fp_id=%s", (f["id"],))
            cur.execute("DELETE FROM sub_processes WHERE fp_id=%s", (f["id"],))
        cur.execute("DELETE FROM fps WHERE module_id=%s", (mid,))
        cur.execute("DELETE FROM modules WHERE id=%s", (mid,))
        audit.log(cur, pid_of_module, "module", mid, "delete",
                  old={"fps": len(fps), "cascade": cascade}, by=user["username"])
    return {"deleted": True}


# ───────────────────────────── FP ─────────────────────────────

def create_fp(db_name, dim, writable, pid: int, body: FPIn, user: dict = Depends(require_role("editor"))):
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
    if len(body.name) > 200:
        raise HTTPException(422, f"FP名过长（{len(body.name)}字），上限200字")
    dup = db.query(db_name, "SELECT id FROM fps WHERE module_id=%s AND fp_name=%s",
                   (body.module_id, body.name), one=True)
    if dup:
        raise HTTPException(409, f"该模块下已存在同名功能过程「{body.name}」（id={dup['id']}），请改名或直接编辑")
    fu, evt = body.user, body.event
    if fu is None or evt is None:
        _fu = derive.derive_functional_user(mod["level3"])
        initiator = derive.initiator_of(_fu)
        fu = fu or _fu
        evt = evt or derive.derive_trigger_event(body.name, initiator)
    n = db.query(db_name, "SELECT COALESCE(MAX(sort_order),0)+1 AS n FROM fps WHERE module_id=%s",
                 (body.module_id,), one=True)["n"]
    with db.tx(db_name) as cur:
        cur.execute("INSERT INTO fps (module_id, sort_order, functional_user, trigger_event, fp_name) VALUES (%s,%s,%s,%s,%s)",
                    (body.module_id, n, fu, evt, body.name))
        fid = cur.lastrowid
        # 标准子过程自动展开（EW/ERX），属性留空待填
        for i, (mt, desc, group) in enumerate(
                derive.standard_subs_for(body.name, derive.initiator_of(fu)), 1):
            cur.execute("INSERT INTO sub_processes (fp_id, sort_order, description, data_move_type, data_group_name, data_attributes) VALUES (%s,%s,%s,%s,%s,'')",
                        (fid, i, desc, mt, group))
        audit.log(cur, audit.project_of_fp(cur, fid), "fp", fid, "create",
                  new={"fp_name": body.name, "functional_user": fu, "trigger_event": evt},
                  by=user["username"])
    return {"id": fid, "user": fu, "event": evt}


def update_fp(db_name, dim, writable, fid: int, body: FPUpdate, user: dict = Depends(require_role("editor"))):
    if not writable:
        raise HTTPException(403, "归档库只读")
    fp = db.query(db_name, "SELECT id, module_id FROM fps WHERE id=%s", (fid,), one=True)
    if not fp:
        raise HTTPException(404, "功能过程不存在")
    if body.name is not None:
        name = body.name
        if len(name) > 200:
            raise HTTPException(422, f"FP名过长（{len(name)}字），上限200字")
        for w in linter.forbidden_words():
            if w in name:
                raise HTTPException(422, f"FP名含禁词'{w}': {name}")
        if not any(name.startswith(v) for v in derive.allowed_verbs()):
            raise HTTPException(422, f"FP名应以动词开头: {derive.allowed_verbs()}")
        dup = db.query(db_name, "SELECT id FROM fps WHERE module_id=%s AND fp_name=%s AND id!=%s",
                       (fp["module_id"], name, fid), one=True)
        if dup:
            raise HTTPException(409, f"该模块下已存在同名功能过程「{name}」（id={dup['id']}），请改名或直接编辑")
    before = db.query(db_name, "SELECT * FROM fps WHERE id=%s", (fid,), one=True)
    changes = {}
    for col, v in (("fp_name", body.name), ("functional_user", body.user), ("trigger_event", body.event)):
        if v is not None:
            changes[col] = v
    if not changes:
        raise HTTPException(422, "无更新字段")
    with db.tx(db_name) as cur:
        pid = audit.project_of_fp(cur, fid)
        audit.diff_log(cur, pid, "fp", fid, before, changes, by=user["username"])
        sets = ", ".join(f"{k}=%s" for k in changes)
        cur.execute(f"UPDATE fps SET {sets} WHERE id=%s", (*changes.values(), fid))
    return {"updated": True}


def delete_fp(db_name, dim, writable, fid: int, cascade: bool = False, user: dict = Depends(require_role("editor"))):
    if not writable:
        raise HTTPException(403, "归档库只读")
    fp = db.query(db_name, "SELECT id, fp_name FROM fps WHERE id=%s", (fid,), one=True)
    if not fp:
        raise HTTPException(404, "功能过程不存在")
    subs = db.query(db_name, "SELECT COUNT(*) AS n FROM sub_processes WHERE fp_id=%s", (fid,), one=True)
    if subs["n"] and not cascade:
        raise HTTPException(409, f"FP下有 {subs['n']} 个子过程，需 cascade=true")
    with db.tx(db_name) as cur:
        cur.execute("DELETE FROM screenshots WHERE fp_id=%s", (fid,))
        cur.execute("DELETE FROM sub_processes WHERE fp_id=%s", (fid,))
        cur.execute("DELETE FROM fps WHERE id=%s", (fid,))
        audit.log(cur, audit.project_of_fp(cur, fid), "fp", fid, "delete",
                  old={"fp_name": fp.get("fp_name", ""), "subs": subs["n"], "cascade": cascade},
                  by=user["username"])
    return {"deleted": True}


# ───────────────────────────── 子过程 ─────────────────────────────

def create_sub(db_name, dim, writable, fid: int, body: SubIn, user: dict = Depends(require_role("editor"))):
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
    if body.desc and len(body.desc) > 500:
        raise HTTPException(422, f"子过程描述过长（{len(body.desc)}字），上限500字")
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
    with db.tx(db_name) as cur:
        cur.execute("INSERT INTO sub_processes (fp_id, sort_order, description, data_move_type, data_group_name, data_attributes) VALUES (%s,%s,%s,%s,%s,%s)",
                    (fid, n, desc, body.move_type, group, body.attributes))
        sid = cur.lastrowid
        audit.log(cur, audit.project_of_sub(cur, sid), "sub", sid, "create",
                  new={"fp_id": fid, "description": desc, "move_type": body.move_type},
                  by=user["username"])
    return {"id": sid, "description": desc, "group_name": group}


def diversify_fp_attrs(db_name, dim, writable, fid: int, user: dict = Depends(require_role("editor"))):
    """按字段池对该 FP 全部子过程执行属性差异化（md5(fp_id) 种子，可复现）。"""
    if not writable:
        raise HTTPException(403, "归档库只读")
    if not derive.auto_diversify_fp(db_name, fid):
        raise HTTPException(422, "字段池未收录该数据组（请到规范中心/词库侧补充字段池）")
    with db.tx(db_name) as cur:
        audit.log(cur, audit.project_of_fp(cur, fid), "fp", fid, "diversify",
                  new={"note": "按字段池差异化全部子过程数据属性"}, by=user["username"])
    return {"diversified": True}


def update_sub(db_name, dim, writable, sid: int, body: dict, user: dict = Depends(require_role("editor"))):
    if not writable:
        raise HTTPException(403, "归档库只读")
    sub = db.query(db_name, "SELECT * FROM sub_processes WHERE id=%s", (sid,), one=True)
    if not sub:
        raise HTTPException(404, f"子过程不存在: {sid}")
    allowed = {k: v for k, v in body.items()
               if k in ("description", "data_move_type", "data_group_name", "data_attributes")}
    # 复用 create_sub 的字段校验，避免裸 dict 绕过规则写入脏数据
    if "data_move_type" in allowed and allowed["data_move_type"] not in derive.allowed_sub_moves():
        raise HTTPException(422, f"move_type 必须是 {derive.allowed_sub_moves()}")
    if "data_attributes" in allowed:
        if "," in allowed["data_attributes"]:
            raise HTTPException(422, "数据属性分隔符必须用、")
        fields = [f.strip() for f in allowed["data_attributes"].split("、") if f.strip()]
        if len(fields) < 3:
            raise HTTPException(422, f"数据属性至少3个字段，当前{len(fields)}个")
    if not allowed:
        raise HTTPException(422, "无有效更新字段")
    with db.tx(db_name) as cur:
        pid = audit.project_of_sub(cur, sid)
        audit.diff_log(cur, pid, "sub", sid, sub, allowed, by=user["username"])
        sets = ", ".join(f"{k}=%s" for k in allowed)
        cur.execute(f"UPDATE sub_processes SET {sets} WHERE id=%s", (*allowed.values(), sid))
    return {"updated": True}


def delete_sub(db_name, dim, writable, sid: int, user: dict = Depends(require_role("editor"))):
    if not writable:
        raise HTTPException(403, "归档库只读")
    sub = db.query(db_name, "SELECT * FROM sub_processes WHERE id=%s", (sid,), one=True)
    if not sub:
        raise HTTPException(404, "子过程不存在")
    with db.tx(db_name) as cur:
        cur.execute("DELETE FROM sub_processes WHERE id=%s", (sid,))
        audit.log(cur, audit.project_of_sub(cur, sid), "sub", sid, "delete",
                  old={"description": sub.get("description"), "move_type": sub.get("data_move_type")},
                  by=user["username"])
    return {"deleted": True}


# ───────────────────────────── 推导 & 门禁 ─────────────────────────────

def derive_all(db_name, dim, writable, pid: int, fix: bool = False, user: dict = Depends(require_role("viewer"))):
    if fix and ROLE_RANK.get(user["role"], 0) < ROLE_RANK["editor"]:
        raise HTTPException(403, "fix 修复需要 editor 及以上权限")
    issues = derive.derive_all(db_name, pid, fix=fix)
    return {"issues": issues, "count": len(issues), "fixed": fix}


def lint_project(db_name, dim, writable, pid: int, no_archive: bool = False, page: int = 1, page_size: int = 20,
                 severity: str = "", keyword: str = ""):
    """质量门禁报告（分页）→ {summary, counts, list, total, page, page_size}。

    ⚠️ 与 /projects 不同：lint 是**计算型报告**（Jaccard / 相似度两两比对），
    LIMIT 无法下推到 SQL，只能对已算出的 issues 在 API 层切片。
    分页解决的是「几千条 issue 一次性塞进 DOM 把浏览器打挂」，不减少服务端计算量。
    """
    report = linter.lint_project(db_name, pid, include_archive_similarity=not no_archive)
    errors = list(report.get("errors") or [])
    warns = list(report.get("warnings") or [])
    merged = [dict(i, level="error") for i in errors] + [dict(i, level="warn") for i in warns]
    if severity in ("error", "warn"):
        merged = [i for i in merged if i["level"] == severity]
    kw = (keyword or "").strip().lower()
    if kw:
        merged = [i for i in merged if any(
            kw in str(i.get(f, "")).lower() for f in ("check", "ref", "message"))]
    page_n, size_n, offset = paging.normalize(page, page_size)
    return {
        "summary": report.get("summary") or {},
        "counts": {"error": len(errors), "warn": len(warns)},
        **paging.wrap(merged[offset:offset + size_n], len(merged), page_n, size_n),
    }


# ───────────────────────────── 导入导出 ─────────────────────────────

def import_template(db_name, dim, writable):
    """导入模板（COSMIC 表头 + 示例行 + 填写说明 sheet），两维度通用。"""
    from urllib.parse import quote
    content = xlsx_export.build_import_template()
    return Response(content, media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    headers={"Content-Disposition":
                             f"attachment; filename*=UTF-8''{quote('cosmic导入模板.xlsx')}"})


async def import_xlsx_ep(db_name, dim, writable, file: UploadFile = File(...),
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
    try:
        report = xlsx_import.import_xlsx(db_name, await file.read(), mode=mode,
                                         project_id=project_id, project_meta=meta)
    except ValueError as e:
        raise HTTPException(422, str(e))
    job = _record_job(dim, mode, file.filename, report)
    with db.tx(db_name) as cur:
        audit.log(cur, project_id or 0, "project", project_id or 0, "import",
                  new={"mode": mode, "file": file.filename,
                       "projects": report.get("projects"), "fps": report.get("fps")},
                  by=user["username"])
    report["job_id"] = job
    return report


async def import_workbook_ep(db_name, dim, writable, file: UploadFile = File(...),
                       confirm: str = "",
                       user: dict = Depends(require_role("editor"))):
    """批量覆盖导入：一个多 sheet 工作簿自动识别数据 sheet，逐项目匹配/追加归档库。
    admin 权限 + confirm=<dimension> 二次确认。"""
    if ROLE_RANK.get(user["role"], 0) < ROLE_RANK["admin"]:
        raise HTTPException(403, "批量覆盖导入需要 admin 权限")
    if confirm != dim:
        raise HTTPException(428, "批量覆盖导入需 confirm=<dimension> 二次确认")
    try:
        report = xlsx_import.bulk_import_workbook_bytes(db_name, await file.read(),
                                                        backup_tag="pre_bulk_workbook")
    except ValueError as e:
        raise HTTPException(422, str(e))
    job = _record_job(dim, "bulk_overwrite", file.filename, report)
    report["job_id"] = job
    return report


async def import_json_ep(db_name, dim, writable, payload: dict,
                   mode: str = Query("incremental", pattern="^(incremental|overwrite)$"),
                   project_id: int | None = None, confirm: str = "",
                   user: dict = Depends(require_role("editor"))):
    if mode == "overwrite" and not project_id:
        if ROLE_RANK.get(user["role"], 0) < ROLE_RANK["admin"]:
            raise HTTPException(403, "整库覆盖导入需要 admin 权限")
        if confirm != dim:
            raise HTTPException(428, "整库覆盖导入需 confirm=<dimension> 二次确认")
    if mode == "overwrite" and project_id and ROLE_RANK.get(user["role"], 0) < ROLE_RANK["admin"]:
        raise HTTPException(403, "覆盖导入需要 admin 权限")
    try:
        report = json_io.import_json(db_name, payload, mode=mode, project_id=project_id)
    except ValueError as e:
        raise HTTPException(422, str(e))
    job = _record_job(dim, mode, "payload.json", report)
    report["job_id"] = job
    return report


def export_xlsx_ep(db_name, dim, writable, pid: int, author: str = ""):
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


def export_json_ep(db_name, dim, writable, pid: int):
    data = json_io.export_project_json(db_name, pid)
    if not data:
        raise HTTPException(404, "项目不存在")
    return data


def import_jobs(db_name, dim, writable, limit: int = 20):
    limit = max(1, min(int(limit), 100))
    return db.query(config.DB_STUDIO,
                    "SELECT * FROM import_jobs WHERE dimension=%s ORDER BY id DESC LIMIT %s",
                    (dim, limit))


# ───────────────────────────── 版本（active 专属；归档库版本无意义）─────────────────────────────

def list_changes(db_name, dim, writable, pid: int, target_type: str = "", target_id: int = 0,
                 page: int = 1, page_size: int = 30, user: dict = Depends(require_role("viewer"))):
    """需求变更流水（change_log）分页 → 谁在何时对哪行做了什么。"""
    if dim != "active":
        raise HTTPException(404, "变更流水仅编写库提供")
    cond, params = ["project_id=%s"], [pid]
    if target_type:
        cond.append("target_type=%s")
        params.append(target_type)
    if target_id:
        cond.append("target_id=%s")
        params.append(target_id)
    where = " AND ".join(cond)
    return paging.paginate(
        config.DB_ACTIVE,
        f"SELECT id, target_type, target_id, action, field_name, old_value, new_value, "
        f"changed_by, changed_at FROM change_log WHERE {where}",
        f"SELECT COUNT(*) AS total FROM change_log WHERE {where}",
        tuple(params), "ORDER BY id DESC", page, page_size)


def version_diff(db_name, dim, writable, vid_a: int, vid_b: int,
                 user: dict = Depends(require_role("viewer"))):
    """两个版本快照的结构化 diff（a=旧，b=新）。

    依赖 versions.json_snapshot（2026-09 起的快照才有）；任一侧缺失时报 409。
    """
    if dim != "active":
        raise HTTPException(404, "版本对比仅编写库提供")
    ja, jb = versioning.load_version_json(vid_a), versioning.load_version_json(vid_b)
    if ja is None or jb is None:
        missing = vid_a if ja is None else vid_b
        raise HTTPException(409, f"版本 #{missing} 没有结构化快照（早于 JSON 快照功能的旧版本），无法对比")
    meta = {r["id"]: r for r in db.query(
        config.DB_STUDIO,
        "SELECT id, seq, label, created_at FROM versions WHERE id IN (%s,%s)", (vid_a, vid_b))}
    return {
        "a": meta.get(vid_a), "b": meta.get(vid_b),
        **versioning.diff_versions(ja, jb),
    }


def snapshot_version(db_name, dim, writable, pid: int, body: VersionIn, user: dict = Depends(require_role("editor"))):
    try:
        snap = versioning.snapshot(db_name, pid, body.label, body.changelog, body.author)
    except ValueError as e:
        raise HTTPException(404, str(e))
    with db.tx(db_name) as cur:
        audit.log(cur, pid, "project", pid, "version",
                  new={"version_id": snap["id"], "seq": snap["seq"], "label": snap["label"]},
                  by=user["username"])
    return snap


def list_versions_ep(db_name, dim, writable, pid: int):
    return versioning.list_versions(db_name, pid)


def download_version(db_name, dim, writable, vid: int, user: dict = Depends(require_role("viewer"))):
    # 越权枚举防护：版本存于 cosmic_studio，按 dimension 列归属当前库；
    # 跨维度 / 不存在的 vid 一律 404（fix architect P2）
    row = db.query(config.DB_STUDIO,
                   "SELECT id, dimension FROM versions WHERE id=%s", (vid,), one=True)
    if not row or row["dimension"] != db_name:
        raise HTTPException(404, "版本文件不存在")
    path = versioning.get_version_file(vid)
    if not path:
        raise HTTPException(404, "版本文件不存在")
    return FileResponse(path, filename=os.path.basename(path))


# ───────────────────────────── 路由工厂 ─────────────────────────────

def make_dimension_router(dim: str, db_name: str, writable: bool) -> APIRouter:
    # 路由级默认：所有端点要求登录（viewer 起）；写端点在签名上再升权
    r = APIRouter(prefix=f"/api/{dim}", tags=[dim],
                  dependencies=[Depends(require_role("viewer"))])

    # 闭包变量（db_name/dim/writable）通过 functools.partial 绑定到各模块级 handler，
    # FastAPI 的 inspect.signature 会自动剥离已绑定参数，路径/Query/Body/Depends 解析不变。
    bind = lambda h: functools.partial(h, db_name, dim, writable)

    # ── 项目 ──
    r.get("/projects")(bind(list_projects))
    r.post("/projects/{pid}/copy", status_code=201)(bind(copy_project))
    r.put("/projects/{pid}/primary")(bind(set_primary))
    r.get("/projects/{pid}/diff")(bind(diff_project))
    r.post("/projects", status_code=201)(bind(create_project))
    r.get("/projects/{pid}/tree")(bind(project_tree))
    r.delete("/projects/{pid}")(bind(delete_project))
    r.post("/projects/bulk-delete")(bind(bulk_delete_projects))

    # ── 模块 ──
    r.post("/projects/{pid}/modules", status_code=201)(bind(create_module))
    r.delete("/modules/{mid}")(bind(delete_module))

    # ── FP ──
    r.post("/projects/{pid}/fps", status_code=201)(bind(create_fp))
    r.put("/fps/{fid}")(bind(update_fp))
    r.delete("/fps/{fid}")(bind(delete_fp))

    # ── 子过程 ──
    r.post("/fps/{fid}/subs", status_code=201)(bind(create_sub))
    r.post("/fps/{fid}/diversify")(bind(diversify_fp_attrs))
    r.put("/subs/{sid}")(bind(update_sub))
    r.delete("/subs/{sid}")(bind(delete_sub))

    # ── 推导 & 门禁 ──
    r.post("/projects/{pid}/derive")(bind(derive_all))
    r.get("/projects/{pid}/lint")(bind(lint_project))

    # ── 导入导出 ──
    r.get("/import/template")(bind(import_template))
    r.post("/import/xlsx")(bind(import_xlsx_ep))
    r.post("/import/workbook")(bind(import_workbook_ep))
    r.post("/import/json")(bind(import_json_ep))
    r.get("/projects/{pid}/export/xlsx")(bind(export_xlsx_ep))
    r.get("/projects/{pid}/export/json")(bind(export_json_ep))
    r.get("/import/jobs")(bind(import_jobs))

    # ── 版本（active 专属；归档库版本无意义）──
    if writable:
        r.post("/projects/{pid}/versions", status_code=201)(bind(snapshot_version))
        r.get("/projects/{pid}/versions")(bind(list_versions_ep))
        r.get("/versions/{vid}/download")(bind(download_version))
        r.get("/projects/{pid}/changes")(bind(list_changes))
        r.get("/versions/{vid_a}/diff/{vid_b}")(bind(version_diff))

    return r


def _record_job(dim: str, mode: str, filename: str, report: dict) -> int:
    import json as _json
    slim = {k: report.get(k) for k in ("mode", "modules", "fps", "subs", "created", "updated", "project_id")}
    return db.execute(config.DB_STUDIO, """
        INSERT INTO import_jobs (dimension, mode, filename, status, report, created_at)
        VALUES (%s,%s,%s,'done',%s,NOW())
    """, (dim, mode, filename, _json.dumps(slim, ensure_ascii=False)))
