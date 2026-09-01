"""LLM 共享客户端。所有调用 LLM 的路由（chat / reviews.auto-fix …）必须走这里，不许各处复制 urllib。

要点：
- 强制带 ``Accept: application/json`` 头。火山方舟（ark.cn-beijing.volces.com）等 OpenAI 兼容
  网关对裸 POST 缺 Accept 头会返回 **406 Not Acceptable**，原代码两处都漏掉该头 → 一调就 406。
- HTTPError 错误透出友好：先尝试按 OpenAI 错误格式提取 ``error.message``，再用 body 前 300
  字节兜底，避免把整段二进制 / HTML 直接甩给前端。
"""
import json
import urllib.error
import urllib.request

from fastapi import HTTPException

DEFAULT_TIMEOUT = 120


def _resolve_chat_url(base_url: str) -> str:
    """把用户填的 base_url 解析为完整的 /chat/completions 端点。

    方舟 / 各类 OpenAI 兼容网关的 base_url 写法不统一，必须兼容以下填法：

    1. ``https://ark.cn-beijing.volces.com/api/v3``
       → 补拼 → ``…/api/v3/chat/completions``（方舟在线推理标准地址）
    2. ``https://ark.cn-beijing.volces.com/api/plan/v3``
       → 补拼 → ``…/api/plan/v3/chat/completions``（方舟 agent-plan 标准地址）
    3. ``https://ark.cn-beijing.volces.com/api/v3/chat/completions``
       （用户直接填了完整路径，含 /chat/completions 结尾）
       → 原样使用，不再重复拼，避免变成 ``…/chat/completions/chat/completions``

    历史包袱：早期代码总是死拼 ``base_url + "/chat/completions"``，第 2/3 种填法
    会拼出不存在的路径（404）。这里用「结尾是否已含 /chat/completions」判定，
    让用户无论怎么填都能命中正确端点。
    """
    u = (base_url or "").rstrip("/")
    if u.endswith("/chat/completions"):
        return u
    return u + "/chat/completions"


def chat_completion(cfg: dict, messages: list, *, tools: list | None = None,
                    timeout: int = DEFAULT_TIMEOUT, temperature: float = 0.2) -> dict:
    """OpenAI 兼容 /chat/completions 调用。返回已 parse 的 JSON 响应。

    base_url 兼容方舟 /api/v3、/api/plan/v3、以及直接以 /chat/completions 结尾的填法，
    由 ``_resolve_chat_url`` 统一处理。

    Raises:
        HTTPException(502, "...") 包装所有上游错误（含 4xx / 5xx / 网络 / 超时）。
        HTTPException(502, "LLM 端点不可达…") 包装 DNS / 连接 / 超时。
    """
    url = _resolve_chat_url(cfg["base_url"])
    payload = {"model": cfg["model"], "messages": messages, "temperature": temperature}
    if tools:
        payload["tools"] = tools
    body = json.dumps(payload, ensure_ascii=False).encode()
    headers = {
        "Content-Type": "application/json",
        # 火山方舟 / 部分自建网关对没 Accept 头的 POST 一律 406——见模块顶部注释
        "Accept": "application/json",
        "Authorization": f"Bearer {cfg['api_key']}",
    }
    req = urllib.request.Request(url, data=body, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        # 常见用户配置错误给出具体指引，避免只丢一个"返回 4xx"
        if e.code in (401, 403):
            raise HTTPException(502, f"LLM 鉴权失败（{e.code}）：请检查 系统管理→LLM配置 的 API Key")
        if e.code == 404:
            raise HTTPException(502, f"LLM 端点路径不存在（404）：当前 URL = {url}。"
                                       f"Base URL 应填到网关前缀（如 https://ark.cn-beijing.volces.com/api/v3 "
                                       f"或 …/api/plan/v3），代码会自动补 /chat/completions；"
                                       f"若已填完整路径请确保以 /chat/completions 结尾。")
        if e.code == 400:
            raise HTTPException(502, f"LLM 拒绝请求（400，可能是模型名/请求体不合法）：{_extract_error_msg(e, url)}")
        if e.code in (406, 415):
            # 历史包袱：方舟等网关对缺 Accept 的 POST 返回 406，已在 headers里设了 Accept；
            # 仍出现说明上游真的不接受 JSON，请用户换网关或换模型
            raise HTTPException(502, f"LLM 不接受请求格式（{e.code}）：{_extract_error_msg(e, url)}")
        raise HTTPException(502, _extract_error_msg(e, url))
    except urllib.error.URLError as e:
        raise HTTPException(502, f"LLM 端点不可达（{e.reason}），请检查 系统管理→LLM配置 的 Base URL")


def _extract_error_msg(e: urllib.error.HTTPError, url: str) -> str:
    """把 LLM 错误响应解析成可读短消息：优先 OpenAI 风格的 error.message，否则 body 前 300 字符。"""
    try:
        body_bytes = e.read()
    except Exception:
        body_bytes = b""
    text = body_bytes.decode(errors="replace")[:300].strip()
    if not text:
        return f"LLM {url} 返回 {e.code}（无响应体）"
    # OpenAI / 火山方舟错误响应：{"error":{"message":"…","type":"…","code":"…"}}
    try:
        obj = json.loads(text)
        if isinstance(obj, dict) and isinstance(obj.get("error"), dict):
            err = obj["error"]
            m = err.get("message") or err.get("type") or err.get("code") or text
            return f"LLM {url} 返回 {e.code}: {m[:300]}"
    except json.JSONDecodeError:
        pass
    return f"LLM {url} 返回 {e.code}: {text[:300]}"