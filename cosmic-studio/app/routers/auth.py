"""认证/用户管理路由。"""
import base64
import hashlib
import json
import logging
import os
import secrets
import time
import urllib.parse
import urllib.request

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import RedirectResponse
from pydantic import BaseModel

from .. import config, db
from ..auth import hash_password, make_token, require_role, verify_password

logger = logging.getLogger(__name__)

# ===== SSO (OIDC authorization_code + PKCE) 配置 =====
OIDC_ISSUER = os.getenv("OIDC_ISSUER", "https://auth.marschat.online")
OIDC_CLIENT_ID = os.getenv("OIDC_CLIENT_ID", "cosmic-studio")
OIDC_CLIENT_SECRET = os.getenv("OIDC_CLIENT_SECRET", "")
OIDC_REDIRECT_URI = os.getenv("OIDC_REDIRECT_URI", "")  # 运行时从请求推导


def _build_redirect_uri(request: Request) -> str:
    """从请求上下文推导回调地址（避免硬编码域名）。"""
    if OIDC_REDIRECT_URI:
        return OIDC_REDIRECT_URI
    scheme = request.headers.get("x-forwarded-proto", request.url.scheme)
    host = request.headers.get("x-forwarded-host", request.url.hostname)
    port = request.url.port
    if port and port not in (80, 443) and ":" not in host:
        host = f"{host}:{port}"
    return f"{scheme}://{host}/sso-callback"


def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


def _sha256_b64url(text: str) -> str:
    digest = hashlib.sha256(text.encode()).digest()
    return _b64url_encode(digest)


def _verify_oidc_token(access_token: str) -> dict | None:
    """向 auth-center 的 /userinfo 发请求验签，返回 claims 或 None。"""
    try:
        url = f"{OIDC_ISSUER}/userinfo"
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {access_token}"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            if resp.status != 200:
                return None
            return json.loads(resp.read())
    except Exception as e:
        logger.warning("OIDC /userinfo 验证失败: %s", e)
        return None

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
    # 菜单级权限：None=跟随角色默认；数组=仅放行的菜单 key（空数组=全部隐藏）
    menu_perms: list[str] | None = None


def _perms_out(v) -> list[str] | None:
    """JSON 列 → 数组；NULL 保留 None（语义=跟随角色默认，与空数组区分）。"""
    if v is None:
        return None
    return db.json_list(v)


class BulkIdsIn(BaseModel):
    ids: list[int]


@r.post("/login")
def login(body: LoginIn):
    user = db.query(config.DB_STUDIO, "SELECT * FROM users WHERE username=%s", (body.username,), one=True)
    if not user or not user["enabled"] or not verify_password(body.password, user["password_hash"]):
        raise HTTPException(401, "用户名或密码错误")
    token = make_token(user)
    return {"token": token, "user": {"id": user["id"], "username": user["username"],
                                     "role": user["role"], "display_name": user["display_name"],
                                     "menu_perms": _perms_out(user.get("menu_perms"))}}


@r.get("/me")
def me(user: dict = Depends(require_role("viewer"))):
    return user


@r.get("/users")
def list_users(user: dict = Depends(require_role("admin"))):
    rows = db.query(config.DB_STUDIO,
                    "SELECT id, username, display_name, role, enabled, menu_perms, created_at "
                    "FROM users ORDER BY id")
    for r_ in rows:
        r_["menu_perms"] = _perms_out(r_["menu_perms"])
    return rows


@r.post("/users", status_code=201)
def create_user(body: UserIn, user: dict = Depends(require_role("admin"))):
    if body.role not in ("viewer", "editor", "admin"):
        raise HTTPException(422, "角色必须是 viewer/editor/admin")
    if not body.password:
        raise HTTPException(422, "初始密码不能为空")
    if db.query(config.DB_STUDIO, "SELECT id FROM users WHERE username=%s", (body.username,), one=True):
        raise HTTPException(409, "用户名已存在")
    perms_json = None if body.menu_perms is None else json.dumps(body.menu_perms, ensure_ascii=False)
    uid = db.execute(config.DB_STUDIO,
                     "INSERT INTO users (username, password_hash, display_name, role, enabled, menu_perms) "
                     "VALUES (%s,%s,%s,%s,%s,%s)",
                     (body.username, hash_password(body.password), body.display_name,
                      body.role, body.enabled, perms_json))
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
    # 菜单权限：显式传入才更新（None=恢复跟随角色默认）；不能把自己改成没有任何可用菜单
    if "menu_perms" in body.model_fields_set:
        perms = body.menu_perms
        if uid == user["id"] and perms is not None and not perms:
            raise HTTPException(422, "不能移除自己的全部菜单权限（会把自己锁在系统外）")
        sets.append("menu_perms=%s")
        params.append(None if perms is None else json.dumps(perms, ensure_ascii=False))
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


# ===== SSO (OIDC) 路由 =====

class SsoCallbackIn(BaseModel):
    code: str
    state: str = ""
    redirect_uri: str = ""


@r.get("/sso/authorize")
def sso_authorize(request: Request, redirect: str = ""):
    """发起 OIDC authorization_code + PKCE 流程，重定向到 auth-center。"""
    verifier = _b64url_encode(secrets.token_bytes(32))
    challenge = _sha256_b64url(verifier)
    state = _b64url_encode(secrets.token_bytes(16))
    callback_uri = _build_redirect_uri(request)

    # 将 verifier/state 存入 app_kv（短期，60秒过期）
    state_key = f"sso_state:{state}"
    db.execute(config.DB_STUDIO,
               "INSERT INTO app_kv (k, v) VALUES (%s, %s) "
               "ON DUPLICATE KEY UPDATE v=%s",
               (state_key, json.dumps({"verifier": verifier, "redirect": redirect}),
                json.dumps({"verifier": verifier, "redirect": redirect})))

    params = urllib.parse.urlencode({
        "client_id": OIDC_CLIENT_ID,
        "redirect_uri": callback_uri,
        "response_type": "code",
        "scope": "openid profile",
        "state": state,
        "code_challenge": challenge,
        "code_challenge_method": "S256",
    })
    return RedirectResponse(f"{OIDC_ISSUER}/oauth2/authorize?{params}")


@r.post("/sso/callback")
def sso_callback(body: SsoCallbackIn, request: Request):
    """前端 SSO 回调页用 code+verifier 换 token，后端验签后建立本地会话。"""
    callback_uri = body.redirect_uri or _build_redirect_uri(request)

    # 从 app_kv 取 verifier
    state_key = f"sso_state:{body.state}"
    row = db.query(config.DB_STUDIO, "SELECT v FROM app_kv WHERE k=%s", (state_key,), one=True)
    if not row:
        raise HTTPException(400, "SSO state 已过期或不匹配，请重新发起登录")
    saved = json.loads(row["v"])
    verifier = saved["verifier"]
    redirect = saved.get("redirect", "")

    # 清理 state
    db.execute(config.DB_STUDIO, "DELETE FROM app_kv WHERE k=%s", (state_key,))

    # 向 auth-center 换 token
    token_data = urllib.parse.urlencode({
        "grant_type": "authorization_code",
        "code": body.code,
        "redirect_uri": callback_uri,
        "client_id": OIDC_CLIENT_ID,
        "code_verifier": verifier,
    })
    if OIDC_CLIENT_SECRET:
        token_data += f"&client_secret={urllib.parse.quote(OIDC_CLIENT_SECRET)}"

    try:
        token_req = urllib.request.Request(
            f"{OIDC_ISSUER}/oauth2/token",
            data=token_data.encode(),
            headers={"Content-Type": "application/x-www-form-urlencoded"},
            method="POST",
        )
        with urllib.request.urlopen(token_req, timeout=10) as resp:
            tokens = json.loads(resp.read())
    except Exception as e:
        raise HTTPException(401, f"换取令牌失败: {e}")

    access_token = tokens.get("access_token", "")
    if not access_token:
        raise HTTPException(401, "认证中心未返回 access_token")

    # 验签：通过 /userinfo 获取用户信息
    claims = _verify_oidc_token(access_token)
    if not claims:
        raise HTTPException(401, "access_token 验签失败")

    username = claims.get("preferred_username") or claims.get("sub") or claims.get("name", "")
    if not username:
        raise HTTPException(401, "token 中无用户名声明")

    # 映射到本地用户：优先同名，回退到第一个 admin
    user = db.query(config.DB_STUDIO,
                    "SELECT * FROM users WHERE username=%s AND enabled=1",
                    (username,), one=True)
    if not user:
        user = db.query(config.DB_STUDIO,
                       "SELECT * FROM users WHERE role='admin' AND enabled=1 ORDER BY id LIMIT 1",
                       one=True)
    if not user:
        raise HTTPException(403, "未找到可映射的用户账号")

    token = make_token(user)
    return {
        "token": token,
        "user": {
            "id": user["id"],
            "username": user["username"],
            "role": user["role"],
            "display_name": user["display_name"],
            "menu_perms": _perms_out(user.get("menu_perms")),
        },
        "redirect": redirect or "/",
    }


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
