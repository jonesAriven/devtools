"""PyMySQL 薄封装：dict 游标 + 事务提交，SQL 与 Hermes SQLite 版本保持近似以便移植对照。"""
import json
import queue
import threading

import pymysql
from pymysql.cursors import DictCursor

from . import config


def json_list(v) -> list:
    """JSON 列取值的统一反序列化入口。

    ⚠️ PyMySQL **不会**自动解码 JSON 列，取回来是 JSON 文本的 str。
    踩过的坑：直接拿这个 str 当 list 用（list(str) / rng.sample(str, 4) / for x in str）
    会把它拆成单个字符，写进业务列的就是 `[`、`"`、`、` 这种垃圾。
    凡是从 JSON 列读出来的值，一律先过这一层。
    """
    if v is None:
        return []
    if isinstance(v, (list, tuple)):
        return list(v)
    if isinstance(v, (bytes, bytearray)):
        v = v.decode("utf-8", "replace")
    if isinstance(v, str):
        s = v.strip()
        if not s:
            return []
        try:
            parsed = json.loads(s)
        except ValueError:
            return []
        return parsed if isinstance(parsed, list) else [parsed]
    return []


def connect(db: str) -> pymysql.connections.Connection:
    return pymysql.connect(
        host=config.DB_HOST, port=config.DB_PORT,
        user=config.DB_USER, password=config.DB_PASSWORD,
        database=db, charset="utf8mb4",
        cursorclass=DictCursor, autocommit=False,
        connect_timeout=5, read_timeout=60, write_timeout=60,
    )


# ── 轻量连接池：按库名分池，避免每请求新建 TCP+认证握手 ──
# queue.Queue 做空闲连接池；取出时 ping 保活，失效则重建。无新增第三方依赖。
# FastAPI 同步端点在线程池执行，单连接同一时刻只被一个线程持有，线程安全。
_POOLS: dict = {}
_POOLS_LOCK = threading.Lock()
_MAX_IDLE = 10  # 每库最多保留的空闲连接数


def _pool(db: str) -> "queue.Queue":
    with _POOLS_LOCK:
        p = _POOLS.get(db)
        if p is None:
            p = queue.Queue()
            _POOLS[db] = p
        return p


def _acquire(db: str) -> pymysql.connections.Connection:
    p = _pool(db)
    try:
        conn = p.get_nowait()
    except queue.Empty:
        return connect(db)
    try:
        conn.ping(reconnect=False)  # 存活校验；掉线则抛异常并重建
    except Exception:
        try:
            conn.close()
        except Exception:
            pass
        return connect(db)
    return conn


def _release(db: str, conn: pymysql.connections.Connection) -> None:
    p = _pool(db)
    try:
        if p.qsize() < _MAX_IDLE:
            p.put_nowait(conn)
        else:
            conn.close()
    except Exception:
        try:
            conn.close()
        except Exception:
            pass


def query(db: str, sql: str, params=(), one: bool = False):
    conn = _acquire(db)
    try:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            rows = cur.fetchall()
        return (rows[0] if rows else None) if one else rows
    finally:
        _release(db, conn)


def execute(db: str, sql: str, params=()) -> int:
    """单条写操作，返回 lastrowid；自动提交。"""
    conn = _acquire(db)
    try:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            rid = cur.lastrowid
        conn.commit()
        return rid
    finally:
        _release(db, conn)


def executemany(db: str, sql: str, seq) -> int:
    conn = _acquire(db)
    try:
        with conn.cursor() as cur:
            n = cur.executemany(sql, seq)
        conn.commit()
        return n
    finally:
        _release(db, conn)


class tx:
    """多语句事务：with tx(db) as cur: ... 自动 commit/rollback。"""

    def __init__(self, db: str):
        self.db = db

    def __enter__(self):
        self.conn = _acquire(self.db)
        self.cur = self.conn.cursor()
        return self.cur

    def __exit__(self, exc_type, exc, tb):
        try:
            if exc_type is None:
                self.conn.commit()
            else:
                self.conn.rollback()
        finally:
            _release(self.db, self.conn)
        return False


def ping(db: str) -> bool:
    try:
        query(db, "SELECT 1")
        return True
    except Exception:
        return False
