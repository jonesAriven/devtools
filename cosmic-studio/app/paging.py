"""分页契约助手 —— 对齐 admin-crud-mandates Rule 1（分页强制）。

统一契约：
    GET  ...?page=1&page_size=20&keyword=
    → {"list": [...], "total": N, "page": 1, "page_size": 20}

硬约束：
  - page 默认 1；page_size 默认 20，硬上限 100；越界值一律夹紧而非报错
  - DB 层必须 LIMIT/OFFSET，禁止 fetch-all-then-slice
  - 计算型报告（lint）无法下推 SQL，退化为「输出切片」并显式注明
"""

DEFAULT_PAGE_SIZE = 20
MAX_PAGE_SIZE = 100


def normalize(page, page_size) -> tuple[int, int, int]:
    """返回 (page, page_size, offset)。"""
    try:
        page = int(page)
    except (TypeError, ValueError):
        page = 1
    try:
        page_size = int(page_size)
    except (TypeError, ValueError):
        page_size = DEFAULT_PAGE_SIZE
    page = max(1, page)
    page_size = min(max(1, page_size), MAX_PAGE_SIZE)
    return page, page_size, (page - 1) * page_size


def wrap(rows: list, total: int, page: int, page_size: int) -> dict:
    """统一返回体。"""
    return {"list": rows, "total": total, "page": page, "page_size": page_size}


def paginate(database: str, select_sql: str, count_sql: str, params: tuple,
             order_sql: str, page, page_size) -> dict:
    """两段式真分页：先 COUNT(*) 再 LIMIT/OFFSET。

    select_sql / count_sql 都不含 ORDER BY / LIMIT，由本函数拼装。
    """
    from . import db
    page, page_size, offset = normalize(page, page_size)
    total = db.query(database, count_sql, params, one=True)["total"]
    rows = db.query(database, f"{select_sql} {order_sql} LIMIT %s OFFSET %s",
                    (*params, page_size, offset))
    return wrap(rows, total, page, page_size)
