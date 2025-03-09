# 首先导入基础库
import os
import sys
import threading
import time
import io
import numpy as np
from PIL import Image, ImageTk

# 添加全局Python包路径
python_path = os.path.dirname(sys.executable)
site_packages = os.path.join(python_path, 'Lib', 'site-packages')
sys.path.append(site_packages)

# GUI相关
import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext, filedialog

# 二维码相关
import qrcode
from pyzbar.pyzbar import decode

# 截图相关
from mss import mss
from mss.screenshot import ScreenShot

# OCR支持（使用PaddleOCR）
try:
    from paddleocr import PaddleOCR
    # 初始化PaddleOCR，使用简化参数
    ocr = PaddleOCR(
        use_angle_cls=True,
        lang='ch',
        use_gpu=False,
        show_log=False
    )
    OCR_SUPPORTED = True
except Exception as e:
    OCR_SUPPORTED = False
    print(f"OCR功能不可用：{str(e)}")

# 摄像头支持（可选）
try:
    import cv2
    CAMERA_SUPPORTED = True
except ImportError:
    CAMERA_SUPPORTED = False
    print("摄像头功能不可用：未安装opencv-python")

# 功能标志
QR_SUPPORTED = True

class QRCodeGenerator:
    def __init__(self, root):
        self.root = root
        self.root.title("二维码工具")
        self.root.geometry("600x500")
        
        # 创建主框架
        main_frame = ttk.Frame(root)
        main_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)
        
        # 左侧框架
        left_frame = ttk.Frame(main_frame)
        left_frame.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        
        # 二维码显示区域
        self.qr_label = ttk.Label(left_frame)
        self.qr_label.pack(pady=10)
        
        # 右侧框架
        right_frame = ttk.Frame(main_frame)
        right_frame.pack(side=tk.RIGHT, fill=tk.BOTH)
        
        # 功能按钮区域
        button_frame = ttk.Frame(right_frame)
        button_frame.pack(fill=tk.X, pady=5)
        
        # 二维码识别按钮
        if QR_SUPPORTED:
            ttk.Button(button_frame, text="识别二维码", command=self.decode_qr).pack(side=tk.LEFT, padx=2)
        
        # OCR按钮
        if OCR_SUPPORTED:
            ttk.Button(button_frame, text="文字识别", command=self.start_ocr).pack(side=tk.LEFT, padx=2)
        
        # 摄像头按钮
        if CAMERA_SUPPORTED:
            ttk.Button(button_frame, text="摄像头扫码", command=self.start_camera).pack(side=tk.LEFT, padx=2)
        
        # 文本输入区域
        self.text_entry = scrolledtext.ScrolledText(right_frame, width=30, height=10)
        self.text_entry.pack(fill=tk.BOTH, expand=True, pady=5)
        
        # 生成二维码按钮
        ttk.Button(right_frame, text="生成二维码", command=self.generate_qr).pack(fill=tk.X, pady=5)
        
        # 状态栏
        self.status_label = ttk.Label(right_frame, text="就绪")
        self.status_label.pack(fill=tk.X, pady=5)
        
        # 显示功能状态
        status_text = "可用功能：\n"
        status_text += "√ 二维码生成\n"
        status_text += "√ 二维码识别\n"
        status_text += "√ OCR文字识别\n" if OCR_SUPPORTED else "× OCR文字识别 [不可用]\n"
        status_text += "√ 摄像头扫码" if CAMERA_SUPPORTED else "× 摄像头扫码 [不可用]"
        messagebox.showinfo("功能状态", status_text)

    def generate_qr(self):
        """生成二维码"""
        text = self.text_entry.get('1.0', tk.END).strip()
        if not text:
            messagebox.showinfo("提示", "请输入文本内容")
            return
            
        try:
            qr = qrcode.QRCode(version=1, box_size=10, border=5)
            qr.add_data(text)
            qr.make(fit=True)
            qr_image = qr.make_image(fill_color="black", back_color="white")
            
            # 转换为PhotoImage
            photo = ImageTk.PhotoImage(qr_image)
            self.qr_label.configure(image=photo)
            self.qr_label.image = photo
            
            self.status_label.config(text="二维码已生成")
        except Exception as e:
            messagebox.showerror("错误", f"生成失败：{str(e)}")

    def decode_qr(self):
        """识别二维码"""
        try:
            file_path = filedialog.askopenfilename(
                filetypes=[("图片文件", "*.png *.jpg *.jpeg *.bmp *.gif")]
            )
            if not file_path:
                return
                
            image = Image.open(file_path)
            result = decode(image)
            
            if result:
                self.text_entry.delete('1.0', tk.END)
                self.text_entry.insert('1.0', result[0].data.decode('utf-8'))
                self.status_label.config(text="识别成功")
            else:
                messagebox.showinfo("提示", "未识别到二维码")
        except Exception as e:
            messagebox.showerror("错误", f"识别失败：{str(e)}")

    # OCR和摄像头相关方法根据功能可用性条件执行
    def start_ocr(self):
        if not OCR_SUPPORTED:
            messagebox.showinfo("提示", "OCR功能不可用")
            return
        # OCR相关代码...

    def start_camera(self):
        if not CAMERA_SUPPORTED:
            messagebox.showinfo("提示", "摄像头功能不可用")
            return
        # 摄像头相关代码...

if __name__ == '__main__':
    root = tk.Tk()
    app = QRCodeGenerator(root)
    root.mainloop() 