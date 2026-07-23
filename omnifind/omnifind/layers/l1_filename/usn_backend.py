"""
L1 文件名层 —— Windows USN Change Journal 后端（纯 ctypes 实现）。

设计要点
--------
1. 直接调 kernel32 的 CreateFileW / DeviceIoControl / CloseHandle，
   不依赖 pywin32，减少 PyInstaller 打包体积（省 ~15MB）。
2. 两阶段构建：先枚举收集全部 (frn, parent_frn, name, is_dir, mtime, attrs)
   到内存，再一次拼绝对路径。NTFS USN 枚举不保证父目录先于子文件出现，
   一次遍历拼路径会漏。
3. 增量监听走 FSCTL_READ_USN_JOURNAL，阻塞式读新记录，reason 位过滤。

权限
----
读 USN journal 需要 SeManageVolumePrivilege，普通 Administrator 也不带，
必须以 SYSTEM 权限运行（Windows 服务用"本地系统账户"启动即可）。
非 SYSTEM 运行会在 CreateFileW 阶段返回 INVALID_HANDLE_VALUE (=-1)，
错误码 5 (Access Denied)。

独立验证入口
------------
python -m omnifind.layers.l1_filename.usn_backend --drive C: --limit 100

在 Windows 上以管理员/SYSTEM 权限跑，能打印 100 条真实文件路径即验证通过。
"""
from __future__ import annotations

import argparse
import ctypes
import ctypes.wintypes as wt
import os
import sys
from typing import Iterator, Optional


# ---------- Win32 常量 ----------
GENERIC_READ = 0x80000000
FILE_SHARE_READ = 0x00000001
FILE_SHARE_WRITE = 0x00000002
OPEN_EXISTING = 3
INVALID_HANDLE_VALUE = ctypes.c_void_p(-1).value

# FSCTL codes（CTL_CODE 宏计算好的常量）
FSCTL_QUERY_USN_JOURNAL = 0x000900F4
FSCTL_ENUM_USN_DATA = 0x000900B3
FSCTL_READ_USN_JOURNAL = 0x000900BB

# 文件属性
FILE_ATTRIBUTE_DIRECTORY = 0x00000010
FILE_ATTRIBUTE_HIDDEN = 0x00000002
FILE_ATTRIBUTE_SYSTEM = 0x00000004

# USN Reason 位（增量监听用）
USN_REASON_FILE_CREATE = 0x00000100
USN_REASON_FILE_DELETE = 0x00000200
USN_REASON_RENAME_NEW_NAME = 0x00002000
USN_REASON_RENAME_OLD_NAME = 0x00001000
USN_REASON_DATA_OVERWRITE = 0x00000001
USN_REASON_DATA_EXTEND = 0x00000002
USN_REASON_DATA_TRUNCATION = 0x00000004
USN_REASON_CLOSE = 0x80000000

USN_REASON_INTERESTING = (
    USN_REASON_FILE_CREATE
    | USN_REASON_FILE_DELETE
    | USN_REASON_RENAME_NEW_NAME
    | USN_REASON_DATA_OVERWRITE
    | USN_REASON_DATA_EXTEND
    | USN_REASON_DATA_TRUNCATION
)


# ---------- 结构体 ----------
class USN_JOURNAL_DATA_V0(ctypes.Structure):
    """FSCTL_QUERY_USN_JOURNAL 返回的日志元信息。"""
    _fields_ = [
        ("UsnJournalID", ctypes.c_ulonglong),
        ("FirstUsn", ctypes.c_longlong),
        ("NextUsn", ctypes.c_longlong),
        ("LowestValidUsn", ctypes.c_longlong),
        ("MaxUsn", ctypes.c_longlong),
        ("MaximumSize", ctypes.c_ulonglong),
        ("AllocationDelta", ctypes.c_ulonglong),
    ]


class MFT_ENUM_DATA_V0(ctypes.Structure):
    """FSCTL_ENUM_USN_DATA 的输入参数。"""
    _fields_ = [
        ("StartFileReferenceNumber", ctypes.c_ulonglong),
        ("LowUsn", ctypes.c_longlong),
        ("HighUsn", ctypes.c_longlong),
    ]


class READ_USN_JOURNAL_DATA_V0(ctypes.Structure):
    """FSCTL_READ_USN_JOURNAL 的输入参数（增量监听用）。"""
    _fields_ = [
        ("StartUsn", ctypes.c_longlong),
        ("ReasonMask", ctypes.c_ulong),
        ("ReturnOnlyOnClose", ctypes.c_ulong),
        ("Timeout", ctypes.c_ulonglong),
        ("BytesToWaitFor", ctypes.c_ulonglong),
        ("UsnJournalID", ctypes.c_ulonglong),
    ]


# USN_RECORD_V2 布局（我们按字节手工解析，不定义 Structure，因为末尾 FileName 变长）
# offset  size  field
# 0       4     RecordLength
# 4       2     MajorVersion (=2)
# 6       2     MinorVersion (=0)
# 8       8     FileReferenceNumber
# 16      8     ParentFileReferenceNumber
# 24      8     Usn
# 32      8     TimeStamp (Windows FILETIME, 100ns since 1601-01-01)
# 40      4     Reason
# 44      4     SourceInfo
# 48      4     SecurityId
# 52      4     FileAttributes
# 56      2     FileNameLength (bytes)
# 58      2     FileNameOffset
# 60+     var   FileName (UTF-16LE)


# ---------- Win32 API 绑定 ----------
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True) if sys.platform == "win32" else None


def _win_call(fn_name):
    """获取 kernel32 函数，非 Windows 直接抛错。"""
    if kernel32 is None:
        raise RuntimeError("USN backend 仅支持 Windows")
    return getattr(kernel32, fn_name)


def _open_volume(drive_letter: str) -> int:
    """打开卷句柄。drive_letter 形如 'C:'（不带反斜杠）。返回 HANDLE。"""
    if kernel32 is None:
        raise RuntimeError("USN backend 仅支持 Windows")
    # 卷路径必须是 \\.\C: 形式
    path = rf"\\.\{drive_letter.rstrip(chr(92)).rstrip(':')}:"
    CreateFileW = kernel32.CreateFileW
    CreateFileW.argtypes = [
        wt.LPCWSTR, wt.DWORD, wt.DWORD, ctypes.c_void_p,
        wt.DWORD, wt.DWORD, wt.HANDLE,
    ]
    CreateFileW.restype = wt.HANDLE
    handle = CreateFileW(
        path,
        GENERIC_READ,
        FILE_SHARE_READ | FILE_SHARE_WRITE,
        None,
        OPEN_EXISTING,
        0,
        None,
    )
    if handle == INVALID_HANDLE_VALUE or handle is None:
        err = ctypes.get_last_error()
        raise OSError(
            err,
            f"CreateFileW({path}) 失败，错误码 {err}。"
            f"提示：{'需要管理员/SYSTEM 权限' if err == 5 else 'Windows 错误查 net helpmsg ' + str(err)}",
        )
    return handle


def _query_usn_journal(handle: int) -> USN_JOURNAL_DATA_V0:
    """查询 USN journal 元信息。"""
    out = USN_JOURNAL_DATA_V0()
    bytes_returned = wt.DWORD(0)
    DeviceIoControl = kernel32.DeviceIoControl
    DeviceIoControl.argtypes = [
        wt.HANDLE, wt.DWORD, ctypes.c_void_p, wt.DWORD,
        ctypes.c_void_p, wt.DWORD, ctypes.POINTER(wt.DWORD), ctypes.c_void_p,
    ]
    DeviceIoControl.restype = wt.BOOL
    ok = DeviceIoControl(
        handle, FSCTL_QUERY_USN_JOURNAL,
        None, 0,
        ctypes.byref(out), ctypes.sizeof(out),
        ctypes.byref(bytes_returned), None,
    )
    if not ok:
        err = ctypes.get_last_error()
        raise OSError(err, f"FSCTL_QUERY_USN_JOURNAL 失败，错误码 {err}")
    return out


def _enum_usn_records(handle: int, journal_id: int, buffer_size: int = 1 << 20) -> Iterator[dict]:
    """
    枚举卷上所有 USN 记录（等价于全量 MFT 扫描）。
    每条 yield: {frn, parent_frn, name, is_dir, attrs, timestamp_100ns, usn}
    """
    start_frn = ctypes.c_ulonglong(0)
    buf = (ctypes.c_ubyte * buffer_size)()
    bytes_returned = wt.DWORD(0)

    DeviceIoControl = kernel32.DeviceIoControl
    DeviceIoControl.argtypes = [
        wt.HANDLE, wt.DWORD, ctypes.c_void_p, wt.DWORD,
        ctypes.c_void_p, wt.DWORD, ctypes.POINTER(wt.DWORD), ctypes.c_void_p,
    ]
    DeviceIoControl.restype = wt.BOOL

    while True:
        med = MFT_ENUM_DATA_V0()
        med.StartFileReferenceNumber = start_frn.value
        med.LowUsn = 0
        med.HighUsn = 0x7FFF_FFFF_FFFF_FFFF  # MAX_LONGLONG

        ok = DeviceIoControl(
            handle, FSCTL_ENUM_USN_DATA,
            ctypes.byref(med), ctypes.sizeof(med),
            buf, buffer_size,
            ctypes.byref(bytes_returned), None,
        )
        if not ok:
            err = ctypes.get_last_error()
            # 38 = ERROR_HANDLE_EOF，枚举结束
            if err == 38:
                return
            raise OSError(err, f"FSCTL_ENUM_USN_DATA 失败，错误码 {err}")

        n = bytes_returned.value
        if n < 8:
            return

        # 前 8 字节是下一次的 StartFileReferenceNumber
        next_frn = int.from_bytes(bytes(buf[0:8]), "little")
        start_frn = ctypes.c_ulonglong(next_frn)

        # 从 offset 8 开始是 USN_RECORD 数组
        off = 8
        while off < n:
            record_len = int.from_bytes(bytes(buf[off:off + 4]), "little")
            if record_len < 60 or off + record_len > n:
                break
            major = int.from_bytes(bytes(buf[off + 4:off + 6]), "little")
            if major != 2:
                # 只处理 V2 记录，其它跳过
                off += record_len
                continue

            frn = int.from_bytes(bytes(buf[off + 8:off + 16]), "little")
            parent_frn = int.from_bytes(bytes(buf[off + 16:off + 24]), "little")
            usn = int.from_bytes(bytes(buf[off + 24:off + 32]), "little", signed=True)
            timestamp = int.from_bytes(bytes(buf[off + 32:off + 40]), "little")
            attrs = int.from_bytes(bytes(buf[off + 52:off + 56]), "little")
            name_len = int.from_bytes(bytes(buf[off + 56:off + 58]), "little")
            name_off = int.from_bytes(bytes(buf[off + 58:off + 60]), "little")

            name_bytes = bytes(buf[off + name_off:off + name_off + name_len])
            try:
                name = name_bytes.decode("utf-16-le", errors="replace")
            except Exception:
                off += record_len
                continue

            yield {
                "frn": frn,
                "parent_frn": parent_frn,
                "name": name,
                "is_dir": bool(attrs & FILE_ATTRIBUTE_DIRECTORY),
                "attrs": attrs,
                "timestamp_100ns": timestamp,
                "usn": usn,
            }
            off += record_len

        # 若这批只出了 8 字节（无记录）说明结束
        if n <= 8:
            return


def _filetime_to_unix(ft_100ns: int) -> float:
    """Windows FILETIME (100ns since 1601-01-01 UTC) → Unix epoch 秒。"""
    if ft_100ns <= 0:
        return 0.0
    return ft_100ns / 10_000_000 - 11_644_473_600


def scan_volume(drive_letter: str, root_filter: Optional[str] = None) -> Iterator[tuple]:
    """
    扫一个卷，yield (full_path, name, size, mtime_unix, is_dir_int)。

    Args:
        drive_letter: 'C' 或 'C:' 都行
        root_filter: 只返回以此路径开头的项（大小写不敏感），加速筛选
    """
    letter = drive_letter.rstrip(":").rstrip("\\").upper()
    if not letter:
        raise ValueError("drive_letter 不能为空")

    handle = _open_volume(letter)
    try:
        journal = _query_usn_journal(handle)

        # 阶段一：收集所有节点
        records: dict[int, dict] = {}
        for rec in _enum_usn_records(handle, journal.UsnJournalID):
            # 过滤系统隐藏
            if rec["attrs"] & (FILE_ATTRIBUTE_SYSTEM | FILE_ATTRIBUTE_HIDDEN):
                continue
            records[rec["frn"]] = rec

        # 阶段二：拼绝对路径（父路径缓存，避免重复递归）
        drive_root = f"{letter}:\\"
        path_cache: dict[int, str] = {}

        def resolve(frn: int) -> Optional[str]:
            if frn in path_cache:
                return path_cache[frn]
            rec = records.get(frn)
            if rec is None:
                # 父不在 records 里说明它是卷根节点
                return drive_root.rstrip("\\")
            parent = resolve(rec["parent_frn"])
            if parent is None:
                path_cache[frn] = os.path.join(drive_root, rec["name"])
            else:
                path_cache[frn] = os.path.join(parent, rec["name"])
            return path_cache[frn]

        rf_lower = root_filter.lower() if root_filter else None
        for frn, rec in records.items():
            path = resolve(frn)
            if not path:
                continue
            if rf_lower and not path.lower().startswith(rf_lower):
                continue
            # NTFS 元数据文件名以 $ 开头，跳过
            if rec["name"].startswith("$") and rec["parent_frn"] not in records:
                continue
            yield (
                path,
                rec["name"],
                0,  # 大小 USN 不带，需要时后置补 GetFileAttributesEx
                _filetime_to_unix(rec["timestamp_100ns"]),
                1 if rec["is_dir"] else 0,
            )
    finally:
        if handle and handle != INVALID_HANDLE_VALUE:
            kernel32.CloseHandle(handle)


# ---------- 集成到 OmniFind FilenameBackend 契约 ----------
try:
    from omnifind.layers.l1_filename.index import FilenameBackend  # noqa
except Exception:
    FilenameBackend = object  # type: ignore


class UsnBackend(FilenameBackend):
    """OmniFind 的 L1 文件名后端 —— USN 版。"""

    def __init__(self, cfg, index):
        self.cfg = cfg
        self.index = index

    def build(self) -> int:
        total = 0
        batch: list[tuple] = []
        for root in (self.cfg.scan_roots or []):
            drive = os.path.splitdrive(root)[0]
            if not drive:
                continue
            try:
                for entry in scan_volume(drive, root):
                    batch.append(entry)
                    if len(batch) >= 5000:
                        self.index.bulk_upsert(batch)
                        total += len(batch)
                        batch.clear()
            except OSError as e:
                print(f"[USN] 扫描 {drive} 失败: {e}", file=sys.stderr)
        if batch:
            self.index.bulk_upsert(batch)
            total += len(batch)
        return total

    def watch(self) -> None:
        raise NotImplementedError("增量 watch 下一阶段实现")


# ---------- 独立验证入口 ----------
def _cli():
    ap = argparse.ArgumentParser(
        prog="omnifind.layers.l1_filename.usn_backend",
        description="Windows USN 后端独立验证。需管理员/SYSTEM 权限。",
    )
    ap.add_argument("--drive", required=True, help="盘符，如 C: 或 D:")
    ap.add_argument("--root", default=None, help="只输出此路径前缀下的文件（可选）")
    ap.add_argument("--limit", type=int, default=50, help="最多打印几条（默认 50，0=不限）")
    ap.add_argument("--count-only", action="store_true", help="只统计条数不打印")
    args = ap.parse_args()

    if sys.platform != "win32":
        print("[ERROR] 本脚本只能在 Windows 上运行", file=sys.stderr)
        sys.exit(2)

    import time
    t0 = time.time()
    n = 0
    files = 0
    dirs = 0
    for path, name, size, mtime, is_dir in scan_volume(args.drive, args.root):
        n += 1
        if is_dir:
            dirs += 1
        else:
            files += 1
        if not args.count_only and (args.limit == 0 or n <= args.limit):
            kind = "DIR " if is_dir else "FILE"
            print(f"{kind} {path}")
    elapsed = time.time() - t0
    print("---")
    print(f"共扫描 {n} 条（{files} 文件 / {dirs} 目录），耗时 {elapsed:.2f}s")
    if n > 0:
        print(f"速率 {int(n / max(elapsed, 0.001)):,} 条/秒")


if __name__ == "__main__":
    _cli()
