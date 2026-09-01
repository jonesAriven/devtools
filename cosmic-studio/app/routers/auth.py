"""认证/用户管理路由。"""
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from .. import config, db
from ..auth import hash_password, make_token, require_role, verify_password

r = APIRouter(prefix="/api/auth", tags=["auth"])


class LoginIn(BaseModel):
    username: str
    password: str


class UserIn(BaseModel):
    username: str
    password: str = ""
    display_name: str = ""
    role: str = "viewer"
    enabled: bool = True


class BulkIdsIn(BaseModel):
    ids: list[int]


@r.post("/login")
def login(body: LoginIn):
    user = db.query(config.DB_STUDIO, "SELECT * FROM users WHERE username=%s", (body.username,), one=True)
    if not user or not user["enabled"] or not verify_password(body.password, user["password_hash"]):
        raise HTTPException(401, "用户名或密码错误")
    token = make_token(user)
    return {"token": token, "user": {"id": user["id"], "username": user["username"],
                                     "role": user["role"], "display_name": user["display_name"]}}


@r.get("/me")
def me(user: dict = Depends(require_role("viewer"))):
    return user


@r.get("/users")
def list_users(user: dict = Depends(require_role("admin"))):
    return db.query(config.DB_STUDIO,
                    "SELECT id, username, display_name, role, enabled, created_at FROM users ORDER BY id")


@r.post("/users", status_code=201)
def create_user(body: UserIn, user: dict = Depends(require_role("admin"))):
    if body.role not in ("viewer", "editor", "admin"):
        raise HTTPException(422, "角色必须是 viewer/editor/admin")
    if not body.password:
        raise HTTPException(422, "初始密码不能为空")
    if db.query(config.DB_STUDIO, "SELECT id FROM users WHERE username=%s", (body.username,), one=True):
        raise HTTPException(409, "用户名已存在")
    uid = db.execute(config.DB_STUDIO,
                     "INSERT INTO users (username, password_hash, display_name, role, enabled) VALUES (%s,%s,%s,%s,%s)",
                     (body.username, hash_password(body.password), body.display_name, body.role, body.enabled))
    return {"id": uid}


@r.put("/users/{uid}")
def update_user(uid: int, body: UserIn, user: dict = Depends(require_role("admin"))):
    sets, params = [], []
    if body.password:
        sets.append("password_hash=%s")
        params.append(hash_password(body.password))
    if body.display_name is not None:
        sets.append("display_name=%s")
        params.append(body.display_name)
    if body.role:
        if body.role not in ("viewer", "editor", "admin"):
            raise HTTPException(422, "角色必须是 viewer/editor/admin")
        if uid == user["id"] and body.role != "admin":
            raise HTTPException(422, "不能降级自己")
        sets.append("role=%s")
        params.append(body.role)
    # 禁止管理员把自己禁用：self 一旦 enabled=False，下一次请求 current_user 即 401 且无吊销通道
    if uid == user["id"] and body.enabled is False:
        raise HTTPException(422, "不能禁用当前登录的账号（想交权请改用角色切换）")
    sets.append("enabled=%s")
    params.append(body.enabled)
    params.append(uid)
    db.execute(config.DB_STUDIO, f"UPDATE users SET {', '.join(sets)} WHERE id=%s", tuple(params))
    return {"updated": True}


# ── 删除用户 ──
# 说明：全库只有 cosmic_studio.chat_logs.user_id 指向 users，且该列 NOT NULL、无外键。
# 硬删用户会留下 user_id 悬挂的行，因此这里一并清掉该用户的对话日志（对话记录归属个人，
# 账号都没了没理由留着），并在返回里报告清理条数。
def _delete_user(uid: int, operator_id: int) -> dict:
    target = db.query(config.DB_STUDIO,
                      "SELECT id, username, role, enabled FROM users WHERE id=%s",
                      (uid,), one=True)
    if not target:
        raise HTTPException(404, f"用户不存在: id={uid}")
    if uid == operator_id:
        raise HTTPException(409, "不能删除当前登录的账号（想停用请改用「启用」开关）")
    # 注：正常路径下这个分支几乎打不到 —— 操作者自己是已启用的 admin，
    # others 至少为 1。留着是为了兜住「会话期间操作者账号被停用」这类边界，
    # 保证任何情况下系统都不会被删成零个可用管理员。
    if target["role"] == "admin" and target["enabled"]:
        others = db.query(config.DB_STUDIO,
                          "SELECT COUNT(*) AS n FROM users "
                          "WHERE role='admin' AND enabled=1 AND id<>%s",
                          (uid,), one=True)["n"]
        if others == 0:
            raise HTTPException(409, f"不能删除最后一个可用的管理员（{target['username']}）")
    with db.tx(config.DB_STUDIO) as cur:
        cur.execute("DELETE FROM chat_logs WHERE user_id=%s", (uid,))
        logs = cur.rowcount
        cur.execute("DELETE FROM users WHERE id=%s", (uid,))
    return {"id": uid, "username": target["username"], "chat_logs": logs}


@r.delete("/users/{uid}")
def delete_user(uid: int, user: dict = Depends(require_role("admin"))):
    return {"deleted": _delete_user(uid, user["id"])}


@r.post("/users/bulk-delete")
def bulk_delete_users(body: BulkIdsIn, user: dict = Depends(require_role("admin"))):
    """批量删除（主要用来清理成批的测试账号）。

    逐个独立判定：某一条命中保护规则只记失败原因，不中断其余删除。
    """
    deleted, failed = [], []
    for uid in body.ids:
        try:
            deleted.append(_delete_user(uid, user["id"]))
        except HTTPException as e:
            failed.append({"id": uid, "reason": e.detail})
    return {"deleted": deleted, "failed": failed,
            "deleted_count": len(deleted), "failed_count": len(failed)}
