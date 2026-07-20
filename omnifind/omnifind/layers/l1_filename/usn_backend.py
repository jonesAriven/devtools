"""
L1 Windows USN/MFT 后端 —— 真·Everything 秒搜(分叉点1:1.1)。

⚠️ 本文件为 Windows 专用,必须在 Windows 机(192.168.31.77 / ideaworkspace)上
   开发和验证。Linux 上无法运行(依赖 ctypes.windll + DeviceIoControl)。
   make_backend() 在非 Windows 或异常时会自动回退到 WalkBackend。

实现路线(Windows 机上填充):
  1. 枚举 NTFS 卷(GetLogicalDrives 过滤 fixed drive)。
  2. 对每个卷:
     a. 用 CreateFile 打开 \\.\C: (需管理员/SeManageVolumePrivilege)。
     b. DeviceIoControl(FSCTL_ENUM_USN_DATA) 遍历 MFT,一次性拿到全盘文件名+FRN+父FRN。
        —— 这是 Everything 冷启动秒级全量的关键:直接读 MFT 而非 os.walk。
     c. 用 FRN->父FRN 关系在内存重建完整路径,批量写 FilenameIndex。
  3. 增量:DeviceIoControl(FSCTL_READ_USN_JOURNAL) 持续读 USN 变更日志,
     解析 USN_RECORD(Reason 位:CREATE/DELETE/RENAME),增量更新索引。
     保存 NextUsn 做断点续读。

参考结构:
  MFT_ENUM_DATA_V0 { StartFileReferenceNumber; LowUsn; HighUsn }
  USN_RECORD_V2    { RecordLength; FileReferenceNumber; ParentFileReferenceNumber;
                     Usn; Reason; FileNameLength; FileNameOffset; FileName[] }
  FSCTL_ENUM_USN_DATA    = 0x900b3
  FSCTL_READ_USN_JOURNAL = 0x900bb
  FSCTL_QUERY_USN_JOURNAL= 0x900f4
"""
from __future__ import annotations

from omnifind.layers.l1_filename.index import FilenameBackend


class UsnBackend(FilenameBackend):
    """Windows NTFS USN/MFT 后端。占位:待 Windows 机实现。"""

    def build(self) -> int:
        raise NotImplementedError(
            "UsnBackend.build 待在 Windows 机(192.168.31.77)实现:"
            "CreateFile 打开卷 -> FSCTL_ENUM_USN_DATA 遍历 MFT -> 重建路径入索引"
        )

    def watch(self) -> None:
        raise NotImplementedError(
            "UsnBackend.watch 待实现:FSCTL_READ_USN_JOURNAL 持续读变更日志增量更新"
        )
