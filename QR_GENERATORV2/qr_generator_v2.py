# 首先导入基础库
import os
import sys
import threading
import time
import io
import numpy as np
from PIL import Image, ImageTk

# 将当前目录添加到PATH
current_dir = os.path.dirname(os.path.abspath(__file__))
os.environ['PATH'] = current_dir + os.pathsep + os.environ.get('PATH', '')

# 添加全局Python包路径
python_path = os.path.dirname(sys.executable)
site_packages = os.path.join(python_path, 'Lib', 'site-packages')
sys.path.append(site_packages)

# GUI相关
import tkinter as tk
from tkinter import ttk, messagebox, scrolledtext, filedialog

# 二维码生成相关
import qrcode

# 截图相关
from mss import mss
from mss.screenshot import ScreenShot

# 尝试导入各种二维码识别库
DECODER_AVAILABLE = False
DECODER_TYPE = None
decode_func = None

# 尝试使用OpenCV的WeChatQRCode（更准确）
try:
    import cv2
    
    # 获取OpenCV安装路径
    cv2_base_dir = os.path.dirname(cv2.__file__)
    model_dir = os.path.join(cv2_base_dir, 'data')
    
    # 检查模型文件是否存在
    detect_prototxt = os.path.join(model_dir, 'detect.prototxt')
    detect_caffe_model = os.path.join(model_dir, 'detect.caffemodel')
    sr_prototxt = os.path.join(model_dir, 'sr.prototxt')
    sr_caffe_model = os.path.join(model_dir, 'sr.caffemodel')
    
    if all([os.path.exists(f) for f in [detect_prototxt, detect_caffe_model, sr_prototxt, sr_caffe_model]]):
        # 使用WeChatQRCode
        qr_detector = cv2.wechat_qrcode_WeChatQRCode(
            detect_prototxt,
            detect_caffe_model,
            sr_prototxt,
            sr_caffe_model
        )
        
        def cv2_wechat_decode(image):
            img = np.array(image)
            if len(img.shape) == 2:
                img = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
            elif img.shape[2] == 4:
                img = cv2.cvtColor(img, cv2.COLOR_RGBA2BGR)
            elif img.shape[2] == 3:
                img = cv2.cvtColor(img, cv2.COLOR_RGB2BGR)
            
            result, points = qr_detector.detectAndDecode(img)
            if len(result) > 0:
                return result[0]
            return None
        
        decode_func = cv2_wechat_decode
        DECODER_AVAILABLE = True
        DECODER_TYPE = "OpenCV WeChat"
        print(f"使用OpenCV WeChatQRCode作为二维码解码器")
    else:
        print("WeChatQRCode模型文件不存在，尝试使用普通QRCodeDetector")
        raise Exception("WeChatQRCode模型文件不存在")
        
except Exception as e:
    print(f"WeChatQRCode不可用: {e}")
    
    # 尝试普通的QRCodeDetector
    try:
        import cv2
        
        def cv2_simple_decode(image):
            # 将PIL图像转换为numpy数组
            img = np.array(image)
            
            # 关键修复：确保数据类型是uint8
            if img.dtype != np.uint8:
                if img.dtype == np.bool_:
                    img = img.astype(np.uint8) * 255
                else:
                    img = img.astype(np.uint8)
            
            # 转换为灰度图
            if len(img.shape) == 2:
                gray = img
            elif img.shape[2] == 4:
                gray = cv2.cvtColor(img, cv2.COLOR_RGBA2GRAY)
            elif img.shape[2] == 3:
                gray = cv2.cvtColor(img, cv2.COLOR_RGB2GRAY)
            else:
                print(f"未知图像格式: {img.shape}")
                return None
            
            detector = cv2.QRCodeDetector()
            
            # 尝试直接识别
            data, points, _ = detector.detectAndDecode(gray)
            if data:
                return data
            
            # 如果图像太大，尝试缩小
            max_dim = 800
            height, width = gray.shape
            if max(height, width) > max_dim:
                scale = max_dim / max(height, width)
                gray_scaled = cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
                data, points, _ = detector.detectAndDecode(gray_scaled)
                if data:
                    return data
            
            # 尝试图像增强
            alpha = 2.0
            beta = 0
            enhanced = cv2.convertScaleAbs(gray, alpha=alpha, beta=beta)
            data, points, _ = detector.detectAndDecode(enhanced)
            if data:
                return data
            
            # 尝试二值化
            _, binary = cv2.threshold(gray, 127, 255, cv2.THRESH_BINARY)
            data, points, _ = detector.detectAndDecode(binary)
            if data:
                return data
            
            # 尝试自适应阈值二值化
            binary_adaptive = cv2.adaptiveThreshold(
                gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2
            )
            data, points, _ = detector.detectAndDecode(binary_adaptive)
            if data:
                return data
            
            # 尝试不同的阈值
            for threshold in [100, 127, 150, 180]:
                _, binary = cv2.threshold(gray, threshold, 255, cv2.THRESH_BINARY)
                data, points, _ = detector.detectAndDecode(binary)
                if data:
                    return data
            
            # 尝试边缘检测后识别
            edges = cv2.Canny(gray, 50, 150)
            data, points, _ = detector.detectAndDecode(edges)
            if data:
                return data
            
            return None
        
        decode_func = cv2_simple_decode
        DECODER_AVAILABLE = True
        DECODER_TYPE = "OpenCV Simple"
        print(f"使用OpenCV QRCodeDetector作为二维码解码器")
    except ImportError:
        print("OpenCV不可用")

# 尝试pyzbar作为备选
if not DECODER_AVAILABLE:
    try:
        from pyzbar.pyzbar import decode as pyzbar_decode
        
        def pyzbar_decode_wrapper(image):
            result = pyzbar_decode(image)
            if result:
                return result[0].data.decode('utf-8')
            return None
        
        decode_func = pyzbar_decode_wrapper
        DECODER_AVAILABLE = True
        DECODER_TYPE = "pyzbar"
        print(f"使用pyzbar作为二维码解码器")
    except Exception as e:
        print(f"pyzbar不可用: {e}")

# 如果都不可用，显示警告
if not DECODER_AVAILABLE:
    print("警告：未找到可用的二维码解码器，识别功能不可用")


class QRCodeGeneratorV2:
    def __init__(self, root):
        self.root = root
        self.root.title("二维码工具 V2")
        self.root.geometry("500x600")
        self.root.resizable(True, True)
        
        self.last_qr_text = ""
        
        # 创建主框架
        main_frame = ttk.Frame(root, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # 二维码预览区域
        qr_frame = ttk.LabelFrame(main_frame, text="二维码预览")
        qr_frame.pack(fill=tk.X, pady=5)
        
        self.qr_label = ttk.Label(qr_frame)
        self.qr_label.pack(pady=10)
        
        # 功能按钮区域
        button_frame = ttk.Frame(main_frame)
        button_frame.pack(fill=tk.X, pady=5)
        
        # 截图识别按钮
        if DECODER_AVAILABLE:
            ttk.Button(button_frame, text="截图识别", command=self.start_screenshot).pack(side=tk.LEFT, padx=5, fill=tk.X, expand=True)
            ttk.Button(button_frame, text="上传图片", command=self.upload_image).pack(side=tk.RIGHT, padx=5, fill=tk.X, expand=True)
        else:
            ttk.Button(button_frame, text="二维码识别功能不可用", state="disabled").pack(side=tk.LEFT, padx=5, fill=tk.X, expand=True)
        
        # 文本输入区域
        input_frame = ttk.LabelFrame(main_frame, text="输入文本")
        input_frame.pack(fill=tk.BOTH, expand=True, pady=5)
        
        self.text_entry = scrolledtext.ScrolledText(input_frame, width=60, height=15, font=("微软雅黑", 10))
        self.text_entry.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        self.text_entry.bind("<KeyRelease>", self.on_text_change)
        
        # 状态栏
        status_frame = ttk.Frame(main_frame)
        status_frame.pack(fill=tk.X, pady=5)
        
        self.status_label = ttk.Label(status_frame, text="就绪", foreground="#008800")
        self.status_label.pack(side=tk.LEFT)
        
        decoder_info = f"解码器: {DECODER_TYPE}" if DECODER_TYPE else "解码器: 不可用"
        self.info_label = ttk.Label(status_frame, text=f"支持多开 | 实时刷新 | {decoder_info}", foreground="#666666")
        self.info_label.pack(side=tk.RIGHT)
        
        # 生成默认二维码
        self.generate_default_qr()
    
    def generate_default_qr(self):
        self.generate_qr("请输入文本内容")
    
    def generate_qr(self, text):
        try:
            qr = qrcode.QRCode(
                version=1,
                error_correction=qrcode.constants.ERROR_CORRECT_M,
                box_size=8,
                border=4,
            )
            qr.add_data(text)
            qr.make(fit=True)
            
            qr_image = qr.make_image(fill_color="black", back_color="white")
            qr_image = qr_image.resize((260, 260), Image.LANCZOS)
            
            photo = ImageTk.PhotoImage(qr_image)
            self.qr_label.configure(image=photo)
            self.qr_label.image = photo
            
        except Exception as e:
            self.status_label.config(text=f"生成失败：{str(e)}", foreground="#FF0000")
    
    def on_text_change(self, event=None):
        current_text = self.text_entry.get('1.0', tk.END).strip()
        
        if current_text == self.last_qr_text:
            return
        
        self.last_qr_text = current_text
        self.status_label.config(text="生成中...", foreground="#008800")
        
        def update_qr():
            if self.last_qr_text:
                self.generate_qr(self.last_qr_text)
                self.status_label.config(text="已更新", foreground="#008800")
            else:
                self.generate_default_qr()
                self.status_label.config(text="就绪", foreground="#008800")
        
        threading.Thread(target=update_qr, daemon=True).start()
    
    def decode_qr_code(self, image):
        """解码二维码"""
        if not DECODER_AVAILABLE or not decode_func:
            return None
        return decode_func(image)
    
    def start_screenshot(self):
        self.root.iconify()
        time.sleep(0.2)
        
        self.screenshot_window = tk.Toplevel()
        self.screenshot_window.attributes('-fullscreen', True)
        self.screenshot_window.attributes('-alpha', 0.3)
        self.screenshot_window.attributes('-topmost', True)
        
        self.screen_canvas = tk.Canvas(self.screenshot_window, cursor="crosshair")
        self.screen_canvas.pack(fill=tk.BOTH, expand=True)
        
        self.start_x = 0
        self.start_y = 0
        self.rect = None
        
        self.screen_canvas.bind("<ButtonPress-1>", self.on_mouse_down)
        self.screen_canvas.bind("<B1-Motion>", self.on_mouse_drag)
        self.screen_canvas.bind("<ButtonRelease-1>", self.on_mouse_up)
        self.screenshot_window.bind("<Escape>", self.cancel_screenshot)
    
    def on_mouse_down(self, event):
        self.start_x = event.x
        self.start_y = event.y
        self.rect = None
    
    def on_mouse_drag(self, event):
        if self.rect:
            self.screen_canvas.delete(self.rect)
        self.rect = self.screen_canvas.create_rectangle(
            self.start_x, self.start_y, event.x, event.y,
            outline="#00FF00", width=2, fill="#00FF00", stipple="gray50"
        )
    
    def on_mouse_up(self, event):
        end_x = event.x
        end_y = event.y
        self.screenshot_window.destroy()
        
        x1 = min(self.start_x, end_x)
        y1 = min(self.start_y, end_y)
        x2 = max(self.start_x, end_x)
        y2 = max(self.start_y, end_y)
        
        if x2 - x1 < 20 or y2 - y1 < 20:
            self.root.deiconify()
            messagebox.showinfo("提示", "请选择较大的区域")
            return
        
        def process_selection():
            try:
                with mss() as sct:
                    monitor = sct.monitors[1]
                    screenshot = sct.grab({
                        'left': monitor['left'] + x1,
                        'top': monitor['top'] + y1,
                        'width': x2 - x1,
                        'height': y2 - y1
                    })
                    image = Image.frombytes('RGB', screenshot.size, screenshot.bgra, 'raw', 'BGRX')
                
                result = self.decode_qr_code(image)
                
                self.root.deiconify()
                
                if result:
                    self.text_entry.delete('1.0', tk.END)
                    self.text_entry.insert('1.0', result)
                    self.status_label.config(text="识别成功")
                else:
                    messagebox.showinfo("提示", "未识别到二维码")
                    self.status_label.config(text="未识别到二维码", foreground="#FF8800")
                    
            except Exception as e:
                self.root.deiconify()
                messagebox.showerror("错误", f"识别失败：{str(e)}")
                self.status_label.config(text=f"识别失败：{str(e)}", foreground="#FF0000")
        
        threading.Thread(target=process_selection, daemon=True).start()
    
    def cancel_screenshot(self, event=None):
        self.screenshot_window.destroy()
        self.root.deiconify()
    
    def upload_image(self):
        try:
            file_path = filedialog.askopenfilename(
                filetypes=[("图片文件", "*.png *.jpg *.jpeg *.bmp *.gif")]
            )
            if not file_path:
                return
                
            image = Image.open(file_path)
            result = self.decode_qr_code(image)
            
            if result:
                self.text_entry.delete('1.0', tk.END)
                self.text_entry.insert('1.0', result)
                self.status_label.config(text="识别成功")
            else:
                messagebox.showinfo("提示", "未识别到二维码")
                self.status_label.config(text="未识别到二维码", foreground="#FF8800")
        except Exception as e:
            messagebox.showerror("错误", f"识别失败：{str(e)}")
            self.status_label.config(text=f"识别失败：{str(e)}", foreground="#FF0000")


if __name__ == '__main__':
    root = tk.Tk()
    app = QRCodeGeneratorV2(root)
    root.mainloop()
