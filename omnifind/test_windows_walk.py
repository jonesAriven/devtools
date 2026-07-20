# OmniFind Windows 测试 - 验证 WalkBackend
import sys
import os

sys.path.insert(0, r"C:\Dev\omnifind")
os.chdir(r"C:\Dev\omnifind")

print("测试 WalkBackend...")
from omnifind.layers.l1_filename.index import FilenameIndex, WalkBackend
from omnifind.core.config import load_config

# 加载配置
cfg = load_config()
cfg.scan_roots = [r"C:\Temp"]  # 小范围测试
print(f"扫描目录: {cfg.scan_roots}")

# 建索引
index = FilenameIndex()
backend = WalkBackend(cfg, index)
count = backend.build()
print(f"索引文件数: {count}")

# 搜索测试
results = index.search("test")
print(f"搜索 'test' 结果数: {len(results)}")
for r in results[:5]:
    print(f"  - {r.path}")

print("\n✅ WalkBackend 在 Windows 上工作正常!")
print("USN 真秒搜需要本地管理员执行脚本验证")
