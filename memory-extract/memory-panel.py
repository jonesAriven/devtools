#!/usr/bin/env python3
"""memory-panel.py — 记忆提炼管理面板 (Flask)

提供 Web 界面查看已处理的会话和提炼的知识条目。
API 和前端一体化，前后端分离。
"""

from __future__ import annotations

import json
import sqlite3
from datetime import datetime
from pathlib import Path

from flask import Flask, jsonify, request, render_template, send_from_directory

app = Flask(__name__)

# ── 配置 ──────────────────────────────────────────────────────────────
BASE_DIR = Path(__file__).parent
DB_PATH = BASE_DIR / "memory-extracts.db"
STATIC_DIR = BASE_DIR / "static"
TEMPLATE_DIR = BASE_DIR / "templates"

app.config["TEMPLATE_FOLDER"] = str(TEMPLATE_DIR)


def get_db() -> sqlite3.Connection:
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    return conn


def ts_to_str(ts: float | None) -> str:
    if ts is None or ts == 0:
        return "-"
    return datetime.fromtimestamp(ts).strftime("%Y-%m-%d %H:%M")


# ── API 路由 ──────────────────────────────────────────────────────────


@app.route("/api/sessions")
def api_sessions():
    conn = get_db()
    try:
        page = int(request.args.get("page", 1))
        per_page = int(request.args.get("per_page", 20))
        search = request.args.get("search", "").strip()
        offset = (page - 1) * per_page

        where = ""
        params = []
        if search:
            where = "WHERE s.title LIKE ? OR s.session_id LIKE ?"
            params = [f"%{search}%", f"%{search}%"]

        # 总数
        total = conn.execute(
            f"SELECT COUNT(*) FROM sessions s {where}", params
        ).fetchone()[0]

        # 数据
        rows = conn.execute(
            f"""SELECT s.*, 
                (SELECT COUNT(*) FROM extracts e WHERE e.session_id = s.session_id) as extract_count
                FROM sessions s {where}
                ORDER BY s.processed_at DESC
                LIMIT ? OFFSET ?""",
            params + [per_page, offset],
        ).fetchall()

        sessions = []
        for row in rows:
            s = dict(row)
            s["processed_at_str"] = ts_to_str(s.get("processed_at"))
            s["started_at_str"] = ts_to_str(s.get("started_at"))
            s["ended_at_str"] = ts_to_str(s.get("ended_at"))
            sessions.append(s)

        return jsonify({
            "sessions": sessions,
            "total": total,
            "page": page,
            "per_page": per_page,
            "total_pages": (total + per_page - 1) // per_page,
        })
    finally:
        conn.close()


@app.route("/api/sessions/<session_id>")
def api_session_detail(session_id):
    conn = get_db()
    try:
        row = conn.execute(
            "SELECT * FROM sessions WHERE session_id = ?", (session_id,)
        ).fetchone()
        if not row:
            return jsonify({"error": "not found"}), 404

        session = dict(row)
        session["processed_at_str"] = ts_to_str(session.get("processed_at"))
        session["started_at_str"] = ts_to_str(session.get("started_at"))
        session["ended_at_str"] = ts_to_str(session.get("ended_at"))

        extracts = conn.execute(
            "SELECT * FROM extracts WHERE session_id = ? ORDER BY created_at",
            (session_id,),
        ).fetchall()

        return jsonify({
            "session": session,
            "extracts": [dict(e) for e in extracts],
        })
    finally:
        conn.close()


@app.route("/api/extracts")
def api_extracts():
    conn = get_db()
    try:
        page = int(request.args.get("page", 1))
        per_page = int(request.args.get("per_page", 30))
        etype = request.args.get("type", "").strip()
        search = request.args.get("search", "").strip()
        offset = (page - 1) * per_page

        where_parts = []
        params = []
        if etype:
            where_parts.append("e.type = ?")
            params.append(etype)
        if search:
            where_parts.append("(e.content LIKE ? OR e.tags LIKE ?)")
            params.extend([f"%{search}%", f"%{search}%"])

        where = ("WHERE " + " AND ".join(where_parts)) if where_parts else ""

        total = conn.execute(
            f"SELECT COUNT(*) FROM extracts e {where}", params
        ).fetchone()[0]

        rows = conn.execute(
            f"""SELECT e.*, s.title as session_title
                FROM extracts e
                LEFT JOIN sessions s ON e.session_id = s.session_id
                {where}
                ORDER BY e.created_at DESC
                LIMIT ? OFFSET ?""",
            params + [per_page, offset],
        ).fetchall()

        extracts = []
        for row in rows:
            e = dict(row)
            e["created_at_str"] = ts_to_str(e.get("created_at"))
            extracts.append(e)

        return jsonify({
            "extracts": extracts,
            "total": total,
            "page": page,
            "per_page": per_page,
            "total_pages": (total + per_page - 1) // per_page,
        })
    finally:
        conn.close()


@app.route("/api/stats")
def api_stats():
    conn = get_db()
    try:
        stats = {
            "total_sessions": conn.execute("SELECT COUNT(*) FROM sessions").fetchone()[0],
            "total_extracts": conn.execute("SELECT COUNT(*) FROM extracts").fetchone()[0],
            "type_distribution": {},
        }
        # 类型分布
        for row in conn.execute(
            "SELECT type, COUNT(*) as cnt FROM extracts GROUP BY type ORDER BY cnt DESC"
        ).fetchall():
            stats["type_distribution"][row["type"]] = row["cnt"]

        # 最近处理时间
        last = conn.execute(
            "SELECT MAX(processed_at) FROM sessions"
        ).fetchone()[0]
        stats["last_processed_at"] = ts_to_str(last)
        stats["last_processed_at_ts"] = last

        return jsonify(stats)
    finally:
        conn.close()


# ── 前端路由 ──────────────────────────────────────────────────────────


@app.route("/")
def index():
    return render_template("index.html")


@app.route("/static/<path:filename>")
def serve_static(filename):
    return send_from_directory(str(STATIC_DIR), filename)


# ── 入口 ──────────────────────────────────────────────────────────────


def main():
    import argparse

    parser = argparse.ArgumentParser(description="记忆提炼管理面板")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8720)
    parser.add_argument("--debug", action="store_true")
    args = parser.parse_args()

    print(f"📊 记忆面板启动: http://{args.host}:{args.port}")
    print(f"   数据库: {DB_PATH}")
    app.run(host=args.host, port=args.port, debug=args.debug)


if __name__ == "__main__":
    main()