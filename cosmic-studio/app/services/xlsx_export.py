"""标准 COSMIC xlsx 导出：逐字节对照 Hermes generate_xlsx.py 的固定格式。

格式常量写死不改（列宽/行高/字体/填充/边框/合并模式）：
  表头 4 行 + 数据从第 5 行起，行高 60
  A-C 跨整个需求合并；D 按模块合并；E/F/G 按功能过程合并；L-M 按功能过程合并；
  H/I/J/K 每行独立（J 数据组不合并）
  每行写全 A-P 列值再 merge（合并区子过程行不可留空）
"""
import io
from datetime import datetime

from openpyxl import Workbook
from openpyxl.drawing.image import Image as XLImage
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side

from .. import config, db

COL_WIDTHS = {
    "A": 27.42, "B": 25.22, "C": 24.79, "D": 26.46,
    "E": 22.02, "F": 38.65, "G": 35.19, "H": 52.36,
    "I": 16.49, "J": 43.64, "K": 84.64, "L": 30.0,
    "M": 30.0, "N": 15.0, "O": 20.0, "P": 12.0,
}
ROW_HEIGHTS = {1: 22.05, 2: 23.85, 3: 19.7, 4: 19.7}
FONT_TITLE = Font(name="Noto Sans CJK SC", size=18, bold=True)
FONT_HEADER = Font(name="Noto Sans CJK SC", size=16, bold=True)
FONT_DATA = Font(name="Noto Sans CJK SC", size=11)
FONT_I_COL = Font(name="宋体", size=11)
FILL_GRAY = PatternFill(start_color="FFA6A6A6", end_color="FFA6A6A6", fill_type="solid")
ALIGN_HEADER = Alignment(horizontal="center", vertical="center", wrap_text=True)
ALIGN_DATA = Alignment(horizontal="general", vertical="center", wrap_text=True)
ALIGN_I_COL = Alignment(horizontal="center", vertical="center", wrap_text=True)
THIN_BORDER = Border(left=Side(style="thin"), right=Side(style="thin"),
                     top=Side(style="thin"), bottom=Side(style="thin"))
COL = {"A": 1, "B": 2, "C": 3, "D": 4, "E": 5, "F": 6, "G": 7,
       "H": 8, "I": 9, "J": 10, "K": 11, "L": 12, "M": 13,
       "N": 14, "O": 15, "P": 16}


def _apply_border(ws, min_row, max_row, min_col, max_col):
    for r in range(min_row, max_row + 1):
        for c in range(min_col, max_col + 1):
            ws.cell(r, c).border = THIN_BORDER


def load_project_tree(dim_db: str, project_id: int):
    """读出 项目→模块→FP→子过程 结构化树（与导出/JSON 共用）。"""
    proj = db.query(dim_db, "SELECT * FROM projects WHERE id=%s", (project_id,), one=True)
    if not proj:
        return None
    mods = db.query(dim_db, "SELECT * FROM modules WHERE project_id=%s ORDER BY sort_order", (project_id,))
    tree = dict(proj)
    tree["modules"] = []
    for mod in mods:
        m = dict(mod)
        m["fps"] = []
        fps = db.query(dim_db, "SELECT * FROM fps WHERE module_id=%s ORDER BY sort_order", (mod["id"],))
        for fp in fps:
            f = dict(fp)
            f["subs"] = db.query(dim_db,
                                 "SELECT * FROM sub_processes WHERE fp_id=%s ORDER BY sort_order", (fp["id"],))
            m["fps"].append(f)
        tree["modules"].append(m)
    return tree


def export_xlsx(dim_db: str, project_id: int, author: str = "") -> tuple[bytes, dict]:
    tree = load_project_tree(dim_db, project_id)
    if not tree:
        raise ValueError(f"project_id={project_id} not found")
    if not author:
        author = config.DEFAULT_INITIATOR

    module_list = tree["modules"]
    if not any(fp["subs"] for mod in module_list for fp in mod["fps"]):
        raise ValueError("项目无子过程数据，无法导出")

    wb = Workbook()
    ws = wb.active
    ws.title = "COSMIC"

    total_data_rows = sum(len(fp["subs"]) for mod in module_list for fp in mod["fps"])
    first_row, last_row = 5, 5 + total_data_rows - 1
    max_col = 16

    # 表头 1-4 行
    ws.merge_cells("A1:P1")
    c = ws.cell(1, 1, "通用软件评估模型")
    c.font, c.fill, c.alignment = FONT_TITLE, FILL_GRAY, ALIGN_HEADER
    for rng, text in (("A2:E2", "度量策略阶段"), ("F2:K2", "映射阶段"), ("L2:P2", "度量阶段")):
        ws.merge_cells(rng)
        cell = ws.cell(2, COL[rng[0]], text)
        cell.font, cell.fill, cell.alignment = FONT_HEADER, FILL_GRAY, ALIGN_HEADER
    headers_3 = {"A": "客户需求", "B": "功能用户需求", "E": "功能用户", "F": "触发事件",
                 "G": "功能过程", "H": "子过程描述", "I": "数据移动类型", "J": "数据组",
                 "K": "数据属性", "L": "功能过程截图\n（可以放多张）",
                 "N": "cosmic编写人", "O": "评审意见", "P": "是否修改"}
    for col, text in headers_3.items():
        c = ws.cell(3, COL[col], text)
        c.font, c.fill, c.alignment = FONT_HEADER, FILL_GRAY, ALIGN_HEADER
    ws.merge_cells("B3:D3")
    for col in ["A", "E", "F", "G", "H", "I", "J", "K", "N", "O", "P"]:
        ws.merge_cells(f"{col}3:{col}4")
    ws.merge_cells("L3:M4")
    for col, text in {"B": "一级模块", "C": "二级模块", "D": "三级模块"}.items():
        c = ws.cell(4, COL[col], text)
        c.font, c.fill, c.alignment = FONT_HEADER, FILL_GRAY, ALIGN_HEADER
    _apply_border(ws, 1, 4, 1, max_col)
    for col_letter, width in COL_WIDTHS.items():
        ws.column_dimensions[col_letter].width = width
    for row_num, height in ROW_HEIGHTS.items():
        ws.row_dimensions[row_num].height = height

    # N 列编写人：写在 FP 首行（后随 E/F/G 合并逻辑独立，N 列每 FP 一值）
    row = first_row
    for mod in module_list:
        d_start = row
        for fp in mod["fps"]:
            e_start = row
            ws.cell(row, COL["N"], author)
            for sub in fp["subs"]:
                a_val = f"【{tree['requirement_id']}】{tree['requirement_name']}"
                for col, val in (("A", a_val), ("B", mod["level1"]), ("C", mod["level2"]),
                                 ("D", mod["level3"]), ("E", fp["functional_user"]),
                                 ("F", fp["trigger_event"]), ("G", fp["fp_name"]),
                                 ("H", sub["description"]), ("I", sub["data_move_type"]),
                                 ("J", sub["data_group_name"]), ("K", sub["data_attributes"])):
                    ws.cell(row, COL[col], val)
                for ci in range(1, max_col + 1):
                    cell = ws.cell(row, ci)
                    cell.font, cell.alignment, cell.border = FONT_DATA, ALIGN_DATA, THIN_BORDER
                ws.cell(row, COL["I"]).font = FONT_I_COL
                ws.cell(row, COL["I"]).alignment = ALIGN_I_COL
                ws.row_dimensions[row].height = 60.0
                row += 1
            fp_end = row - 1
            if fp_end > e_start:
                for col in ("E", "F", "G"):
                    ws.merge_cells(f"{col}{e_start}:{col}{fp_end}")
                ws.merge_cells(f"L{e_start}:M{fp_end}")
                ws.merge_cells(f"N{e_start}:N{fp_end}")
        d_end = row - 1
        if d_end > d_start:
            ws.merge_cells(f"D{d_start}:D{d_end}")
    if last_row > first_row:
        for col in ("A", "B", "C"):
            ws.merge_cells(f"{col}{first_row}:{col}{last_row}")

    meta = {"rows": total_data_rows, "modules": len(module_list),
            "fps": sum(len(m["fps"]) for m in module_list)}
    _embed_screenshots(dim_db, ws, tree, first_row)

    buf = io.BytesIO()
    wb.save(buf)
    return buf.getvalue(), meta


def _embed_screenshots(dim_db, ws, tree, first_row):
    """FP 有截图记录时嵌入 L 列（每 FP 取第一张）。"""
    total = 0
    row = first_row
    for mod in tree["modules"]:
        for fp in mod["fps"]:
            img = db.query(dim_db, "SELECT image_data, image_width, image_height FROM screenshots "
                                   "WHERE fp_id=%s ORDER BY sort_order LIMIT 1", (fp["id"],), one=True)
            if img:
                import io as _io
                xl = XLImage(_io.BytesIO(img["image_data"]))
                xl.width = min(img["image_width"], 500)
                xl.height = min(img["image_height"], 300)
                ws.add_image(xl, f"L{row}")
                total += 1
                target = max(60, xl.height + 10)
                for r in range(row, row + len(fp["subs"])):
                    ws.row_dimensions[r].height = target
            row += len(fp["subs"])
    return total
