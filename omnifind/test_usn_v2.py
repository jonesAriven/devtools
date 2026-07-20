# USN 测试脚本 - 修正版
import os
import sys
import ctypes
from ctypes import wintypes

kernel32 = ctypes.WinDLL('kernel32', use_last_error=True)

GENERIC_READ = 0x80000000
FILE_SHARE_READ = 0x00000001
FILE_SHARE_WRITE = 0x00000002
OPEN_EXISTING = 3

FSCTL_QUERY_USN_JOURNAL = 0x000900B4
FSCTL_ENUM_USN_DATA = 0x000900BB

class MFT_ENUM_DATA(ctypes.Structure):
    _fields_ = [
        ("StartFileReferenceNumber", ctypes.c_ulonglong),
        ("LowUsn", ctypes.c_ulonglong),
        ("HighUsn", ctypes.c_ulonglong),
    ]

def test_usn(drive="C:\\"):
    print(f"测试 {drive} USN 读取...")

    # 打开卷
    vol_path = r"\\.\%s" % drive.rstrip("\\")
    print(f"卷路径: {vol_path}")

    hVol = kernel32.CreateFileW(
        ctypes.c_wchar_p(vol_path),
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
        # 先试简单版本: 用 pywin32 如果有
        try:
            import win32file
            import win32con
            print("使用 pywin32 测试...")

            # 枚举 USN
            enum_data = MFT_ENUM_DATA()
            enum_data.StartFileReferenceNumber = 0
            enum_data.LowUsn = 0
            enum_data.HighUsn = 0xFFFFFFFFFFFFFFFF

            buffer = ctypes.create_string_buffer(64 * 1024)
            bytes_returned = wintypes.DWORD()

            success = kernel32.DeviceIoControl(
                hVol,
                FSCTL_ENUM_USN_DATA,
                ctypes.byref(enum_data),
                ctypes.sizeof(enum_data),
                buffer,
                64 * 1024,
                ctypes.byref(bytes_returned),
                None,
            )

            if success:
                print(f"USN 读取成功! 读到 {bytes_returned.value} 字节")
                print("USN 后端可以工作 ✅")
                return True
            else:
                err = ctypes.get_last_error()
                print(f"DeviceIoControl 失败 (错误码 {err})")
                return False

        except ImportError as e:
            print(f"pywin32 导入失败: {e}")
            return False

    finally:
        kernel32.CloseHandle(hVol)


if __name__ == "__main__":
    import os
    # 检查是否管理员
    try:
        import ctypes
        is_admin = ctypes.windll.shell32.IsUserAnAdmin()
    except:
        is_admin = False
    print(f"管理员权限: {'是' if is_admin else '否'}")
    if not is_admin:
        print("警告: 需要管理员权限才能读 USN!")

    test_usn("C:\\")
    test_usn("D:\\")
