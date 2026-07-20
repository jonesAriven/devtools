# USN 测试脚本
# 管理员权限运行
import os
import sys
import ctypes
from ctypes import wintypes

# Windows API 定义
kernel32 = ctypes.WinDLL('kernel32', use_last_error=True)

GENERIC_READ = 0x80000000
FILE_SHARE_READ = 0x00000001
FILE_SHARE_WRITE = 0x00000002
OPEN_EXISTING = 3

FSCTL_ENUM_USN_DATA = 0x000900BB
ERROR_HANDLE_EOF = 38

class USN_JOURNAL_DATA(ctypes.Structure):
    _fields_ = [
        ("UsnJournalID", ctypes.c_ulonglong),
        ("FirstUsn", ctypes.c_ulonglong),
        ("NextUsn", ctypes.c_ulonglong),
        ("LowestValidUsn", ctypes.c_ulonglong),
        ("MaxUsn", ctypes.c_ulonglong),
        ("MaximumSize", ctypes.c_ulonglong),
        ("AllocationDelta", ctypes.c_ulonglong),
        ("MinSupportedMajorVersion", ctypes.c_ushort),
        ("MaxSupportedMajorVersion", ctypes.c_ushort),
        ("Flags", ctypes.c_ushort),
        ("RangeTrackChunkSize", ctypes.c_ushort),
        ("FileExtentSize", ctypes.c_ulonglong),
    ]

def test_usn(drive="C:\\"):
    """测试 USN 读取"""
    print(f"测试 {drive} USN 读取...")

    # 打开卷
    hVol = kernel32.CreateFileW(
        ctypes.c_wchar_p(r"\\.\\" + drive.rstrip("\\")),
        GENERIC_READ,
        FILE_SHARE_READ | FILE_SHARE_WRITE,
        None,
        OPEN_EXISTING,
        0,
        None,
    )

    if hVol == -1:
        err = ctypes.get_last_error()
        print(f"打开卷失败 (错误码 {err}), 需要管理员权限")
        return False

    try:
        # 查询 USN 日志
        journal_data = USN_JOURNAL_DATA()
        bytes_returned = wintypes.DWORD()
        success = kernel32.DeviceIoControl(
            hVol,
            0x000900B4,  # FSCTL_QUERY_USN_JOURNAL
            None,
            0,
            ctypes.byref(journal_data),
            ctypes.sizeof(journal_data),
            ctypes.byref(bytes_returned),
            None,
        )
        if not success:
            err = ctypes.get_last_error()
            print(f"查询 USN 日志失败 (错误码 {err})")
            return False

        print(f"USN 日志 ID: {journal_data.UsnJournalID}")
        print(f"下一条 USN: {journal_data.NextUsn}")
        print(f"最大大小: {journal_data.MaximumSize / 1024 / 1024:.1f} MB")

        # 简单测试: 枚举前 100 条记录
        buffer_size = 64 * 1024
        buffer = ctypes.create_string_buffer(buffer_size)
        start_usn = 0

        success = kernel32.DeviceIoControl(
            hVol,
            FSCTL_ENUM_USN_DATA,
            ctypes.byref(ctypes.c_ulonglong(start_usn)),
            8,
            buffer,
            buffer_size,
            ctypes.byref(bytes_returned),
            None,
        )

        if not success:
            err = ctypes.get_last_error()
            print(f"枚举 USN 失败 (错误码 {err})")
            return False

        print(f"USN 读取成功! 读到 {bytes_returned.value} 字节数据")
        print("USN 后端可以工作 ✅")
        return True

    finally:
        kernel32.CloseHandle(hVol)


if __name__ == "__main__":
    if len(sys.argv) > 1:
        test_usn(sys.argv[1])
    else:
        test_usn("C:\\")
        test_usn("D:\\")
