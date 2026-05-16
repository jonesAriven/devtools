import os
import sys

python_path = os.path.dirname(sys.executable)
site_packages = os.path.join(python_path, 'Lib', 'site-packages')
sys.path.append(site_packages)

pyzbar_path = os.path.join(site_packages, 'pyzbar')
if os.path.exists(pyzbar_path):
    os.environ['PATH'] = pyzbar_path + os.pathsep + os.environ.get('PATH', '')

print("=== 检查依赖库 ===")

try:
    import qrcode
    print("✅ qrcode 可用")
except Exception as e:
    print(f"❌ qrcode 不可用: {e}")

try:
    from PIL import Image, ImageTk
    print("✅ PIL/Pillow 可用")
except Exception as e:
    print(f"❌ PIL/Pillow 不可用: {e}")

try:
    from pyzbar.pyzbar import decode as decode_qr
    print("✅ pyzbar 可用")
except Exception as e:
    print(f"❌ pyzbar 不可用: {e}")

try:
    from mss import mss
    print("✅ mss 可用")
except Exception as e:
    print(f"❌ mss 不可用: {e}")

try:
    import tkinter as tk
    from tkinter import ttk
    print("✅ tkinter 可用")
except Exception as e:
    print(f"❌ tkinter 不可用: {e}")
