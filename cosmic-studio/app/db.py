"""PyMySQL 薄封装：dict 游标 + 事务提交，SQL 与 Hermes SQLite 版本保持近似以便移植对照。"""
import pymysql
from pymysql.cursors import DictCursor

from . import config


def connect(db: str) -> pymysql.connections.Connection:
    return pymysql.connect(
        host=config.DB_HOST, port=config.DB_PORT,
        user=config.DB_USER, password=config.DB_PASSWORD,
        database=db, charset="utf8mb4",
        cursorclass=DictCursor, autocommit=False,
    )


def query(db: str, sql: str, params=(), one: bool = False):
    conn = connect(db)
    try:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            rows = cur.fetchall()
        return (rows[0] if rows else None) if one else rows
    finally:
        conn.close()


def execute(db: str, sql: str, params=()) -> int:
    """单条写操作，返回 lastrowid；自动提交。"""
    conn = connect(db)
    try:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            rid = cur.lastrowid
        conn.commit()
        return rid
    finally:
        conn.close()


def executemany(db: str, sql: str, seq) -> int:
    conn = connect(db)
    try:
        with conn.cursor() as cur:
            n = cur.executemany(sql, seq)
        conn.commit()
        return n
    finally:
        conn.close()


class tx:
    """多语句事务：with tx(db) as cur: ... 自动 commit/rollback。"""

    def __init__(self, db: str):
        self.db = db

    def __enter__(self):
        self.conn = connect(self.db)
        self.cur = self.conn.cursor()
        return self.cur

    def __exit__(self, exc_type, exc, tb):
        try:
            if exc_type is None:
                self.conn.commit()
            else:
                self.conn.rollback()
        finally:
            self.conn.close()
        return False


def ping(db: str) -> bool:
    try:
        query(db, "SELECT 1")
        return True
    except Exception:
        return False
