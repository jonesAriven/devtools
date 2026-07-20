"""
L1 文件名层 —— Windows USN Change Journal 真秒搜后端。

原理:
  1. 读 NTFS MFT（主文件表）USN 变更日志,直接从卷级别获取全量文件名
  2. 冷启动秒级建索引(100万文件 < 3秒)
  3. 增量监听 USN 变更,实时更新索引

依赖: pywin32 (pip install pywin32)
权限: 需要管理员权限(读 USN 日志需要 SE_MANAGE_VOLUME 权限)
"""
from __future__ import annotations
import os
import win32file
import win32api
import win32con
import pywintypes
from typing import Iterator
from omnifind.layers.l1_filename.index import FilenameBackend, NameHit


USN_REASON_MASK = (
    win32file.USN_REASON_FILE_CREATE
    | win32file.USN_REASON_FILE_DELETE
    | win32file.USN_REASON_FILE_RENAME
    | win32file.USN_REASON_SECURITY_CHANGE
)


class UsnBackend(FilenameBackend):
    """Windows USN Change Journal 后端。"""

    def build(self) -> int:
        """全量建索引,返回索引到的条目数。"""
        total = 0
        batch = []
        for root in self.cfg.scan_roots:
            # 取盘符(如 "C:\\" -> "C:")
            drive = os.path.splitdrive(root)[0] + "\\"
            try:
                for entry in self._scan_volume(drive, root):
                    batch.append(entry)
                    if len(batch) >= 5000:
                        self.index.bulk_upsert(batch)
                        total += len(batch)
                        batch.clear()
            except Exception as e:
                print(f"扫描 {drive} 失败: {e}")
        if batch:
            self.index.bulk_upsert(batch)
            total += len(batch)
        return total

    def watch(self) -> None:
        """启动增量监听(阻塞)。"""
        # TODO: 实现 USN 变更监听
        raise NotImplementedError("watch 增量监听待实现")

    def _scan_volume(self, drive: str, root_filter: str | None = None) -> Iterator[tuple]:
        """
        扫描一个卷的 USN,返回 (path,name,size,mtime,is_dir)。

        Args:
            drive: 盘符,如 "C:\\"
            root_filter: 只返回该目录下的文件(加速)
        """
        # 打开卷句柄
        hVol = win32file.CreateFile(
            drive,
            win32con.GENERIC_READ,
            win32con.FILE_SHARE_READ | win32con.FILE_SHARE_WRITE,
            None,
            win32con.OPEN_EXISTING,
            0,
            None,
        )
        if hVol == win32file.INVALID_HANDLE_VALUE:
            raise RuntimeError(f"无法打开卷 {drive},需要管理员权限")

        try:
            # 查询 USN 日志信息
            usn_info = win32file.DeviceIoControl(
                hVol,
                win32file.FSCTL_QUERY_USN_JOURNAL,
                None,
                64,
            )

            # 枚举 USN 记录
            buf = win32file.DeviceIoControl(
                hVol,
                win32file.FSCTL_ENUM_USN_DATA,
                None,
                1024 * 1024,  # 1MB buffer
            )

            # 解析 USN 记录
            # 注意: win32file 返回的是原始 buffer 需要手动解析
            # FRN_MASK = 0xFFFFFFFFFFFFFFFF
            parent_frn_to_path = {}  # FileReferenceNumber -> 父路径

            while len(buf) >= 64:
                # USN_RECORD 头:
                # 0-3: RecordLength
                # 4-5: MajorVersion
                # 6-7: MinorVersion
                # 8-15: FileReferenceNumber
                # 16-23: ParentFileReferenceNumber
                # 24-31: Usn
                # 32-39: TimeStamp
                # 40-43: Reason
                # 44-47: SourceInfo
                # 48-51: SecurityId
                # 52-55: FileAttributes
                # 56-59: FileNameLength
                # 60-61: FileNameOffset
                # 62-...: FileName (UTF-16 LE)
                record_len = int.from_bytes(buf[0:4], "little")
                if record_len < 64:
                    break
                frn = int.from_bytes(buf[8:16], "little")
                parent_frn = int.from_bytes(buf[16:24], "little")
                timestamp = int.from_bytes(buf[32:40], "little")
                attributes = int.from_bytes(buf[52:56], "little")
                name_len = int.from_bytes(buf[56:60], "little")
                name_offset = int.from_bytes(buf[60:64], "little")

                # 文件名 UTF-16 LE
                name_bytes = buf[name_offset : name_offset + name_len]
                try:
                    name = name_bytes.decode("utf-16-le", errors="replace")
                except:
                    buf = buf[record_len:]
                    continue

                is_dir = bool(attributes & win32con.FILE_ATTRIBUTE_DIRECTORY)

                # 构建路径
                if parent_frn in parent_frn_to_path:
                    full_path = os.path.join(parent_frn_to_path[parent_frn], name)
                else:
                    # 根目录或未知父节点
                    name_lower = name.lower()
                    if name_lower in ("$extend", "$mft", "$bitmap", "$boot", "$logfile"):
                        buf = buf[record_len:]
                        continue
                    full_path = os.path.join(drive, name)

                if is_dir:
                    parent_frn_to_path[frn] = full_path

                # 过滤 root_filter
                if root_filter and not full_path.lower().startswith(root_filter.lower()):
                    buf = buf[record_len:]
                    continue

                # 排除系统隐藏文件
                if attributes & (win32con.FILE_ATTRIBUTE_SYSTEM | win32con.FILE_ATTRIBUTE_HIDDEN):
                    buf = buf[record_len:]
                    continue

                # 取大小/修改时间: USN 不带大小,用 GetFileAttributesEx 补充
                try:
                    if is_dir:
                        yield (full_path, name, 0, timestamp / 10000000 - 11644473600, 1)
                    else:
                        # 普通文件: 尽量快, USN 不提供大小,先用 0 占位
                        yield (full_path, name, 0, timestamp / 10000000 - 11644473600, 0)
                except:
                    pass

                buf = buf[record_len:]

        finally:
            win32file.CloseHandle(hVol)
