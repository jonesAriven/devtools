"""直接调用新导入服务 bulk_import_workbook_bytes 对线上归档库做全量批量覆盖导入。

与 HTTP 端点 /api/archive/import/workbook 走的完全是同一函数、同一事务、同一备份逻辑，
只是不经过 vite 代理（绕过 273MB 多 part 上传超时）。
"""
import os
import sys
import time
import json

REPO = r"D:\huliang\java\ideaworkspace\cosmic-studio"
sys.path.insert(0, REPO)
# 备份目录：dump_dimension_backup 写到 DATA_DIR/backups
os.environ.setdefault("DATA_DIR", r"C:\Users\13871\WorkBuddy\2026-08-30-22-14-43\backup")

SRC = r"C:\Users\13871\Downloads\cosmic工时及质量跟踪 (2).xlsx"
DIM = "cosmic_archive"


def main():
    t0 = time.time()
    print(f"[info] 读取原始文件: {SRC}")
    with open(SRC, "rb") as f:
        data = f.read()
    print(f"[info] 文件大小: {len(data)/1024/1024:.1f} MB, 耗时 {time.time()-t0:.1f}s")

    from app.services.xlsx_import import bulk_import_workbook_bytes
    print("[info] 调用 bulk_import_workbook_bytes(...) —— 含自动备份 + 逐项目重建")
    rep = bulk_import_workbook_bytes(DIM, data, backup_tag="pre_bulk_workbook_20260831")
    print("[done] 耗时 %.1fs" % (time.time() - t0))
    print(json.dumps(rep, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
