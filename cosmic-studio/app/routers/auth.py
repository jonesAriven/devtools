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
    sets.append("enabled=%s")
    params.append(body.enabled)
    params.append(uid)
    db.execute(config.DB_STUDIO, f"UPDATE users SET {', '.join(sets)} WHERE id=%s", tuple(params))
    return {"updated": True}
