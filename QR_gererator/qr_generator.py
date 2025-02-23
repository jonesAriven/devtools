import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext
import qrcode
from PIL import Image, ImageTk, ImageGrab
import io
import cv2
import numpy as np
from pyzbar.pyzbar import decode
import keyboard
import threading
import time

class QRCodeGenerator:
    def __init__(self, root):
        self.root = root
        self.root.title("二维码生成器/识别器")
        self.root.geometry("600x800")  # 增加窗口高度
        
        # 创建样式
        style = ttk.Style()
        style.configure("TLabel", padding=5)
        style.configure("TButton", padding=5)
        
        # 创建框架来容纳输入区域
        input_frame = ttk.Frame(root)
        input_frame.pack(fill=tk.X, padx=20, pady=10)
        
        # 创建按钮框架
        button_frame = ttk.Frame(input_frame)
        button_frame.pack(fill=tk.X, pady=5)
        
        # 创建截屏按钮
        self.capture_button = ttk.Button(
            button_frame,
            text="截屏识别二维码(按ESC结束截屏)",
            command=self.start_screen_capture
        )
        self.capture_button.pack(side=tk.LEFT, padx=5)
        
        # 创建摄像头按钮
        self.camera_button = ttk.Button(
            button_frame,
            text="打开摄像头扫码",
            command=self.start_camera_capture
        )
        self.camera_button.pack(side=tk.LEFT, padx=5)
        
        # 创建输入框和标签
        self.text_label = ttk.Label(input_frame, text="请输入要转换的文本或等待扫描结果：")
        self.text_label.pack(pady=10)
        
        # 创建StringVar来跟踪输入框内容变化
        self.text_var = tk.StringVar()
        self.text_var.trace('w', self.on_text_change)
        
        # 创建多行文本框
        self.text_entry = scrolledtext.ScrolledText(
            input_frame,
            width=40,
            height=10,
            font=('Arial', 12),
            wrap=tk.WORD
        )
        self.text_entry.pack(fill=tk.X, pady=10)
        
        # 绑定文本变化事件
        self.text_entry.bind('<KeyRelease>', self.on_text_change)
        
        # 创建显示二维码的标签
        self.qr_label = ttk.Label(root)
        self.qr_label.pack(pady=20)
        
        # 用于存储上一次生成的文本
        self.last_text = ""
        
        # 摄像头捕获标志
        self.is_capturing = False
        
    def start_screen_capture(self):
        self.root.iconify()  # 最小化窗口
        time.sleep(0.5)  # 等待窗口最小化
        
        try:
            # 截取全屏
            screenshot = ImageGrab.grab()
            # 将PIL图像转换为OpenCV格式
            screenshot_cv = cv2.cvtColor(np.array(screenshot), cv2.COLOR_RGB2BGR)
            
            # 识别二维码
            decoded_objects = decode(screenshot_cv)
            
            self.root.deiconify()  # 恢复窗口
            
            if decoded_objects:
                # 获取识别到的文本
                qr_text = decoded_objects[0].data.decode('utf-8')
                # 设置文本框内容
                self.text_entry.delete('1.0', tk.END)
                self.text_entry.insert('1.0', qr_text)
            else:
                messagebox.showinfo("提示", "未检测到二维码")
                
        except Exception as e:
            self.root.deiconify()
            messagebox.showerror("错误", f"截屏识别失败：{str(e)}")
    
    def start_camera_capture(self):
        if not self.is_capturing:
            self.is_capturing = True
            self.camera_button.configure(text="关闭摄像头")
            threading.Thread(target=self.camera_capture, daemon=True).start()
        else:
            self.is_capturing = False
            self.camera_button.configure(text="打开摄像头扫码")
    
    def camera_capture(self):
        cap = cv2.VideoCapture(0)
        
        while self.is_capturing:
            ret, frame = cap.read()
            if not ret:
                break
                
            # 识别二维码
            decoded_objects = decode(frame)
            
            if decoded_objects:
                # 获取识别到的文本
                qr_text = decoded_objects[0].data.decode('utf-8')
                # 设置文本框内容
                self.text_entry.delete('1.0', tk.END)
                self.text_entry.insert('1.0', qr_text)
                break
            
            # 显示预览窗口
            cv2.imshow('扫描二维码 (按q退出)', frame)
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break
        
        cap.release()
        cv2.destroyAllWindows()
        self.is_capturing = False
        self.camera_button.configure(text="打开摄像头扫码")
    
    def on_text_change(self, *args):
        # 获取当前文本
        text = self.text_entry.get('1.0', 'end-1c')
        
        # 如果文本为空或与上次相同，则不生成
        if not text or text == self.last_text:
            return
            
        self.last_text = text
        self.generate_qr(text)
            
    def generate_qr(self, text):
        try:
            # 生成二维码
            qr = qrcode.QRCode(
                version=1,
                error_correction=qrcode.constants.ERROR_CORRECT_L,
                box_size=10,
                border=4,
            )
            qr.add_data(text)
            qr.make(fit=True)
            
            # 创建二维码图像
            qr_image = qr.make_image(fill_color="black", back_color="white")
            
            # 调整图像大小为更大尺寸
            qr_image = qr_image.resize((300, 300))
            
            # 转换为PhotoImage以在tkinter中显示
            photo_image = ImageTk.PhotoImage(qr_image)
            
            # 更新标签显示二维码
            self.qr_label.configure(image=photo_image)
            self.qr_label.image = photo_image
            
        except Exception as e:
            messagebox.showerror("错误", f"生成二维码时出错：{str(e)}")

if __name__ == "__main__":
    root = tk.Tk()
    app = QRCodeGenerator(root)
    root.mainloop() 