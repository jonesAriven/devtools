#!/usr/bin/env python3
"""memory-extract.py — 对话记忆自动提炼管线

从 Hermes state.db 扫新结束的会话，调 LLM 提炼结构化知识，
写入独立 SQLite 存储。不修改现有记忆架构，可独立部署。

用法:
  python3 memory-extract.py                    # 正常执行
  python3 memory-extract.py --force-session SESSION_ID  # 强制处理指定会话
  python3 memory-extract.py --dry-run                    # 只预览不写入
"""

from __future__ import annotations

import json
import os
import re
import sqlite3
import sys
import time
import urllib.request
import uuid
from pathlib import Path
from typing import Any

# ── 默认配置（可被 config.yaml 覆盖） ──────────────────────────────────
DEFAULT_CONFIG = {
    "state_db": str(Path.home() / ".hermes/state.db"),
    "extract_db": str(Path(__file__).parent / "memory-extracts.db"),
    "llm": {
        "base_url": "https://ark.cn-beijing.volces.com/api/plan/v3",
        "api_key": "ark-8a555b84-ce24-417f-81ce-621a6a9d236f-dbb6b",
        "model": "doubao-seed-2-0-lite-260428",
        "timeout": 120,
    },
    "schedule_minutes": 15,
}

# ── 工具函数 ──────────────────────────────────────────────────────────


def load_config() -> dict:
    config_path = Path(__file__).parent / "config.yaml"
    if not config_path.exists():
        print(f"[config] 未找到 {config_path}，使用默认配置", file=sys.stderr)
        return dict(DEFAULT_CONFIG)

    # 简单 YAML 解析（不引入 pyyaml 依赖）
    import re

    config = dict(DEFAULT_CONFIG)
    current_section = None
    yaml_lines = config_path.read_text().splitlines()
    for line in yaml_lines:
        line = line.split("#")[0].rstrip()  # 去注释
        if not line.strip():
            continue
        # section header
        m = re.match(r"^(\w+):\s*$", line)
        if m:
            current_section = m.group(1)
            if current_section not in config:
                config[current_section] = {}
            continue
        # key: value
        m = re.match(r"^\s{2}(\w+):\s*(.+)$", line)
        if m:
            key, val = m.group(1), m.group(2).strip()
            val = val.strip('"').strip("'")
            if current_section:
                config.setdefault(current_section, {})[key] = val
            else:
                # 尝试数字
                try:
                    config[key] = int(val)
                except ValueError:
                    config[key] = val
        # sub-section like panel:
        m = re.match(r"^\s{2}(\w+):\s*$", line)
        if m and current_section:
            config.setdefault(current_section, {})[m.group(1)] = {}
            current_section = m.group(1)
    return config


def http_post(url: str, body: dict, timeout: float = 30.0) -> dict:
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


# ── 数据库 ────────────────────────────────────────────────────────────


def init_db(db_path: str) -> sqlite3.Connection:
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    conn.execute("""
        CREATE TABLE IF NOT EXISTS sessions (
            session_id TEXT PRIMARY KEY,
            title TEXT,
            source TEXT,
            user_id TEXT,
            model TEXT,
            started_at REAL,
            ended_at REAL,
            message_count INTEGER,
            input_tokens INTEGER DEFAULT 0,
            output_tokens INTEGER DEFAULT 0,
            processed_at REAL,
            extract_count INTEGER DEFAULT 0,
            conversation_summary TEXT
        )
    """)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS extracts (
            id TEXT PRIMARY KEY,
            session_id TEXT NOT NULL,
            type TEXT NOT NULL DEFAULT 'general',
            content TEXT NOT NULL,
            tags TEXT DEFAULT '',
            category TEXT DEFAULT '',
            context TEXT DEFAULT '',
            created_at REAL NOT NULL,
            FOREIGN KEY (session_id) REFERENCES sessions(session_id)
        )
    """)
    conn.execute("""
        CREATE INDEX IF NOT EXISTS idx_extracts_session
        ON extracts(session_id)
    """)
    conn.execute("""
        CREATE INDEX IF NOT EXISTS idx_extracts_type
        ON extracts(type)
    """)
    conn.commit()
    return conn


def get_unprocessed_sessions(
    state_db_path: str, extract_db_path: str, limit: int = 5
) -> list[dict]:
    """获取 state.db 中已结束但未处理过的会话"""
    state_conn = sqlite3.connect(state_db_path)
    state_conn.row_factory = sqlite3.Row

    extract_conn = sqlite3.connect(extract_db_path)

    # 已处理过的 session_id
    processed = set(
        row[0] for row in extract_conn.execute("SELECT session_id FROM sessions")
    )
    extract_conn.close()

    rows = state_conn.execute(
        """
        SELECT id, title, source, user_id, model, started_at, ended_at,
               message_count, input_tokens, output_tokens
        FROM sessions
        WHERE ended_at IS NOT NULL
          AND source NOT LIKE 'cron%'
          AND source NOT LIKE 'webhook%'
          AND message_count >= 4
        ORDER BY ended_at DESC
        LIMIT ?
        """,
        (limit + 50,),  # 多取一些，过滤掉已处理的
    ).fetchall()

    state_conn.close()

    unprocessed = []
    for row in rows:
        sid = row["id"]
        if sid in processed:
            continue
        unprocessed.append(dict(row))
        if len(unprocessed) >= limit:
            break

    return unprocessed


def get_session_messages(
    state_db_path: str, session_id: str, max_len: int = 200
) -> list[dict]:
    """获取会话的关键消息（用户+助手，只保留有意义的内容，去工具调用）"""
    conn = sqlite3.connect(state_db_path)
    conn.row_factory = sqlite3.Row
    rows = conn.execute(
        """
        SELECT role, content, timestamp
        FROM messages
        WHERE session_id = ?
          AND active = 1
          AND role IN ('user', 'assistant')
          AND content IS NOT NULL
          AND content != ''
          AND content NOT LIKE '[This response was interrupted%'
        ORDER BY timestamp
        """,
        (session_id,),
    ).fetchall()
    conn.close()

    messages = []
    for row in rows:
        content = row["content"]
        # 跳过纯工具调用的助手回复（内容为空或只有工具调用）
        if len(content) < 3:
            continue
        # 截断过长消息
        if len(content) > max_len:
            content = content[:max_len] + "..."
        messages.append({
            "role": row["role"],
            "content": content,
            "ts": row["timestamp"],
        })
    return messages


# ── LLM 提炼 ──────────────────────────────────────────────────────────


def build_extract_prompt(messages: list[dict]) -> str:
    """构建提炼 prompt"""
    conversation = []
    for m in messages:
        prefix = "用户" if m["role"] == "user" else "助手"
        conversation.append(f"{prefix}: {m['content']}")

    chat_text = "\n\n".join(conversation)

    prompt = f"""你是知识提取助手。从以下对话提取有长期价值的知识，严格输出纯 JSON 数组，不要任何其他文字。

格式：[{{"type":"decision|lesson|architecture|preference|fact|detail|general","content":"中文完整内容","tags":"逗号分隔标签","category":"运维/开发/架构/配置"}}]

规则：
- 只输出 JSON 数组，禁止解释/思考/前缀
- 每条独立完整，不依赖上下文
- 保留端口/路径/命令/版本等细节
- 无价值内容跳过

对话：
{chat_text}

JSON输出："""

    return prompt


def call_llm(config: dict, prompt: str) -> str:
    """调用 LLM 提炼"""
    llm_cfg = config["llm"]
    body = {
        "model": llm_cfg["model"],
        "messages": [
            {"role": "system", "content": "你是一个知识提取助手，从对话中提取结构化知识。"},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.1,
        "max_tokens": 4096,
    }
    url = f"{llm_cfg['base_url'].rstrip('/')}/chat/completions"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {llm_cfg['api_key']}",
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    timeout = int(llm_cfg.get("timeout", 120))
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        result = json.loads(resp.read().decode("utf-8"))
    content = result["choices"][0]["message"].get("content") or ""
    if not content:
        rc = result["choices"][0]["message"].get("reasoning_content")
        if isinstance(rc, str):
            content = rc
    return content


def parse_extracts(llm_output: str) -> list[dict]:
    """解析 LLM 返回的 JSON"""
    text = llm_output.strip()

    # 尝试提取 JSON 数组（可能在 markdown 代码块中）
    if "```json" in text:
        text = text.split("```json")[1].split("```")[0].strip()
    elif "```" in text:
        text = text.split("```")[1].split("```")[0].strip()

    try:
        items = json.loads(text)
        if isinstance(items, list):
            return items
        return []
    except json.JSONDecodeError:
        # 尝试从原始输出中正则提取 JSON 数组
        m = re.search(r'\[[\s\S]*\]', llm_output)
        if m:
            try:
                return json.loads(m.group(0))
            except Exception:
                pass
        print(f"[parse] JSON 解析失败，原始输出: {llm_output[:200]}", file=sys.stderr)
        return []


def summarize_conversation(messages: list[dict]) -> str:
    """生成简短会话摘要（用于面板展示）"""
    user_msgs = [m for m in messages if m["role"] == "user"]
    if not user_msgs:
        return "（无有效消息）"
    # 取前 3 条用户消息概括
    topics = []
    for m in user_msgs[:3]:
        text = m["content"].strip()
        if len(text) > 60:
            text = text[:60] + "…"
        topics.append(text)
    return " | ".join(topics)


# ── 核心流程 ──────────────────────────────────────────────────────────


def process_session(
    config: dict, session: dict, dry_run: bool = False
) -> int:
    """处理单个会话，返回提取的条目数"""
    session_id = session["id"]
    title = session.get("title") or session_id

    print(f"  → 获取消息...", file=sys.stderr)
    messages = get_session_messages(config["state_db"], session_id)

    if len(messages) < 4:
        print(f"  ⏭ 跳过：消息太少 ({len(messages)} 条)", file=sys.stderr)
        return 0

    print(f"  → 调用 LLM 提炼 ({len(messages)} 条消息)...", file=sys.stderr)
    prompt = build_extract_prompt(messages)
    try:
        llm_out = call_llm(config, prompt)
    except Exception as e:
        print(f"  ❌ LLM 调用失败: {e}", file=sys.stderr)
        return 0

    extracts = parse_extracts(llm_out)
    if not extracts:
        print(f"  ⚠️ 未提取到知识条目", file=sys.stderr)
        extracts = []

    summary = summarize_conversation(messages)

    if dry_run:
        print(f"\n  📋 会话: {title} ({session_id})")
        print(f"  📝 摘要: {summary}")
        print(f"  📊 提取 {len(extracts)} 条:")
        for i, e in enumerate(extracts, 1):
            print(f"    {i}. [{e.get('type','?')}] {e.get('content','')[:80]}")
        return len(extracts)

    # 写入数据库
    db_path = config["extract_db"]
    conn = sqlite3.connect(db_path)
    try:
        now = time.time()
        conn.execute(
            """INSERT OR REPLACE INTO sessions
               (session_id, title, source, user_id, model, started_at, ended_at,
                message_count, input_tokens, output_tokens, processed_at,
                extract_count, conversation_summary)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                session_id,
                title,
                session.get("source"),
                session.get("user_id"),
                session.get("model"),
                session.get("started_at"),
                session.get("ended_at"),
                session.get("message_count"),
                session.get("input_tokens", 0),
                session.get("output_tokens", 0),
                now,
                len(extracts),
                summary,
            ),
        )

        for e in extracts:
            eid = str(uuid.uuid4())
            conn.execute(
                """INSERT INTO extracts
                   (id, session_id, type, content, tags, category, context, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    eid,
                    session_id,
                    e.get("type", "general"),
                    e.get("content", ""),
                    e.get("tags", ""),
                    e.get("category", ""),
                    json.dumps({"session_id": session_id, "title": title}, ensure_ascii=False),
                    now,
                ),
            )

        conn.commit()
        print(f"  ✅ 写入 {len(extracts)} 条", file=sys.stderr)
    finally:
        conn.close()
    return len(extracts)


def main() -> int:
    config = load_config()

    # 解析参数
    dry_run = "--dry-run" in sys.argv
    force_session = None
    reset = "--reset" in sys.argv
    custom_limit = None
    for arg in sys.argv:
        if arg.startswith("--force-session="):
            force_session = arg.split("=", 1)[1]
        elif arg.startswith("--limit="):
            custom_limit = int(arg.split("=", 1)[1])

    # 确保 DB 已初始化
    init_db(config["extract_db"])

    if reset:
        print("🧹 清空现有数据...", file=sys.stderr)
        conn = sqlite3.connect(config["extract_db"])
        conn.execute("DELETE FROM extracts")
        conn.execute("DELETE FROM sessions")
        conn.commit()
        conn.close()
        print("✅ 已清空", file=sys.stderr)

    if force_session:
        # 处理指定会话
        state_conn = sqlite3.connect(config["state_db"])
        state_conn.row_factory = sqlite3.Row
        row = state_conn.execute(
            "SELECT id, title, source, user_id, model, started_at, ended_at, "
            "message_count, input_tokens, output_tokens FROM sessions WHERE id = ?",
            (force_session,),
        ).fetchone()
        state_conn.close()
        if not row:
            print(f"❌ 未找到会话: {force_session}", file=sys.stderr)
            return 1
        session = dict(row)
        print(f"🔧 强制处理: {session.get('title') or session['id']}", file=sys.stderr)
        process_session(config, session, dry_run=dry_run)
        return 0

    # 正常模式：扫未处理会话
    sessions = get_unprocessed_sessions(
        config["state_db"], config["extract_db"], limit=custom_limit or 5
    )
    if not sessions:
        print(f"✅ 无新会话需要处理", file=sys.stderr)
        return 0

    print(f"📦 发现 {len(sessions)} 个未处理会话", file=sys.stderr)
    total = 0
    for session in sessions:
        title = session.get("title") or session["id"]
        print(f"\n{'='*50}", file=sys.stderr)
        print(f"📄 [{title[:50]}]", file=sys.stderr)
        n = process_session(config, session, dry_run=dry_run)
        total += n

    print(f"\n{'='*50}", file=sys.stderr)
    print(f"✅ 完成：共处理 {len(sessions)} 个会话，提取 {total} 条知识", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())