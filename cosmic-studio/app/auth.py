"""认证与权限：JWT(HMAC) + pbkdf2 密码 + 角色依赖注入。零第三方依赖。

角色层级：viewer(1) 只读 < editor(2) 编写/导入导出/版本 < admin(3) 全量（用户管理/规范修改/归档导入/LLM配置）。
扩展点：新接口声明 require_role("editor") 即挂权限；菜单按角色下发。
"""
import base64
import hashlib
import hmac
import json
import os
import secrets
import time

from fastapi import Depends, HTTPException, Request

from . import config, db

ROLE_RANK = {"viewer": 1, "editor": 2, "admin": 3}
TOKEN_TTL = 7 * 24 * 3600


def _secret() -> str:
    row = db.query(config.DB_STUDIO, "SELECT v FROM app_kv WHERE k='auth_secret'", one=True)
    if row:
        return row["v"]
    sec = secrets.token_hex(32)
    db.execute(config.DB_STUDIO, "INSERT IGNORE INTO app_kv (k, v) VALUES ('auth_secret', %s)", (sec,))
    return sec


def hash_password(password: str, salt: str | None = None) -> str:
    salt = salt or secrets.token_hex(8)
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), 60000)
    return f"{salt}${dk.hex()}"


def verify_password(password: str, stored: str) -> bool:
    try:
        salt, _ = stored.split("$", 1)
    except ValueError:
        return False
    return hmac.compare_digest(hash_password(password, salt), stored)


def make_token(user: dict) -> str:
    payload = {"uid": user["id"], "username": user["username"], "role": user["role"],
               "exp": int(time.time()) + TOKEN_TTL}
    body = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    sig = hmac.new(_secret().encode(), body.encode(), hashlib.sha256).hexdigest()
    return f"{body}.{sig}"


def parse_token(token: str) -> dict:
    try:
        body, sig = token.rsplit(".", 1)
        expect = hmac.new(_secret().encode(), body.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, expect):
            raise ValueError("bad signature")
        payload = json.loads(base64.urlsafe_b64decode(body + "=" * (-len(body) % 4)))
        if payload.get("exp", 0) < time.time():
            raise ValueError("expired")
        return payload
    except Exception as e:
        raise HTTPException(401, f"登录态无效: {e}") from e


def current_user(request: Request) -> dict:
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        raise HTTPException(401, "未登录")
    payload = parse_token(auth[7:])
    row = db.query(config.DB_STUDIO,
                   "SELECT id, username, role, display_name, enabled, menu_perms FROM users WHERE id=%s",
                   (payload["uid"],), one=True)
    if not row or not row["enabled"]:
        raise HTTPException(401, "用户不存在或已禁用")
    return dict(row)


def require_role(min_role: str):
    async def dep(user: dict = Depends(current_user)) -> dict:
        if ROLE_RANK.get(user["role"], 0) < ROLE_RANK[min_role]:
            raise HTTPException(403, f"需要 {min_role} 及以上权限")
        return user
    return dep
