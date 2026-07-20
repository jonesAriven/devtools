# USN 测试脚本 - pywin32 原生版
import os
import sys

try:
    import win32file
    import win32con
    import pywintypes
    print("pywin32 导入成功")
except ImportError as e:
    print(f"pywin32 导入失败: {e}")
    sys.exit(1)

def test_usn(drive="C:\\"):
    print(f"\n测试 {drive} USN 读取...")
    vol_path = r"\\.\%s" % drive.rstrip("\\")
    print(f"卷路径: {vol_path}")

    try:
        hVol = win32file.CreateFile(
            vol_path,
            win32con.GENERIC_READ,
            win32con.FILE_SHARE_READ | win32con.FILE_SHARE_WRITE,
            None,
            win32con.OPEN_EXISTING,
            0,
            None,
        )
    except pywintypes.error as e:
        print(f"打开卷失败: {e}")
        return False

    try:
        # 查询 USN 日志
        try:
            usn_info = win32file.DeviceIoControl(
                hVol,
                win32file.FSCTL_QUERY_USN_JOURNAL,
                None,
                64,
            )
            print(f"USN 查询成功!")
        except pywintypes.error as e:
            print(f"查询 USN 失败: {e}")
            if e.winerror == 1179:
                print("尝试创建 USN 日志...")
                try:
                    win32file.DeviceIoControl(
                        hVol,
                        win32file.FSCTL_CREATE_USN_JOURNAL,
                        None,
                        0,
                    )
                    print("USN 日志创建成功!")
                except Exception as e2:
                    print(f"创建 USN 失败: {e2}")
            return False

        # 尝试枚举 USN
        try:
            data = win32file.DeviceIoControl(
                hVol,
                win32file.FSCTL_ENUM_USN_DATA,
                b'\x00' * 8,  # StartUsn = 0
                1024 * 1024,  # 1MB buffer
            )
            print(f"USN 枚举成功! 读到 {len(data)} 字节")
            print("USN 后端可以工作 ✅")
            return True
        except pywintypes.error as e:
            print(f"枚举 USN 失败: {e}")
            return False

    finally:
        win32file.CloseHandle(hVol)


if __name__ == "__main__":
    import ctypes
    is_admin = ctypes.windll.shell32.IsUserAnAdmin()
    print(f"管理员权限: {'是' if is_admin else '否'}")
    if not is_admin:
        print("警告: 需要管理员权限!")

    test_usn("C:\\")
    test_usn("D:\\")
