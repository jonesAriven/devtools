import tkinter as tk
from tkinter import ttk, filedialog
from PIL import Image, ImageTk, ImageGrab
import qrcode
import cv2
import numpy as np

class QRCodeTool:
    def __init__(self, root):
        self.root = root
        self.root.title("二维码工具 V2")
        self.root.geometry("520x650")
        self.root.resizable(False, False)

        # 二维码预览面板
        self.qr_frame = ttk.LabelFrame(root, text="二维码预览")
        self.qr_frame.pack(pady=10, padx=15, fill=tk.BOTH, expand=True)
        
        self.qr_label = ttk.Label(self.qr_frame)
        self.qr_label.pack(pady=20)
        self.update_qr_code("")  # 初始空白

        # 功能按钮区
        self.btn_frame = ttk.Frame(root)
        self.btn_frame.pack(pady=5, padx=15, fill=tk.X)
        
        self.snap_btn = ttk.Button(self.btn_frame, text="截图识别", command=self.start_select_area)
        self.snap_btn.pack(side=tk.LEFT, padx=5, fill=tk.X, expand=True)
        
        self.upload_btn = ttk.Button(self.btn_frame, text="上传图片", command=self.upload_image)
        self.upload_btn.pack(side=tk.RIGHT, padx=5, fill=tk.X, expand=True)

        # 文本输入区（实时生成 + 识别结果写入）
        self.text_label = ttk.Label(root, text="输入文本 / 识别结果")
        self.text_label.pack(pady=(10, 0), padx=15, anchor=tk.W)
        
        self.text_input = tk.Text(root, height=12, wrap=tk.WORD, font=("微软雅黑", 10))
        self.text_input.pack(pady=5, padx=15, fill=tk.BOTH, expand=True)
        self.text_input.bind("<KeyRelease>", self.on_text_update)

        # 截图选择变量
        self.start_x = None
        self.start_y = None
        self.rect_id = None
        self.overlay = None

    # ===================== 实时生成二维码 =====================
    def update_qr_code(self, content):
        """根据文本实时更新二维码"""
        if not content.strip():
            img = Image.new("RGB", (220, 220), "white")
        else:
            qr = qrcode.QRCode(
                version=1, error_correction=qrcode.constants.ERROR_CORRECT_L,
                box_size=5, border=4
            )
            qr.add_data(content)
            qr.make(fit=True)
            img = qr.make_image(fill_color="black", back_color="white").convert("RGB")
            img = img.resize((220, 220), Image.Resampling.LANCZOS)

        self.photo = ImageTk.PhotoImage(img)
        self.qr_label.config(image=self.photo)

    def on_text_update(self, event):
        text = self.text_input.get("1.0", tk.END).strip()
        self.update_qr_code(text)

    # ===================== 区域截图识别（核心） =====================
    def start_select_area(self):
        """启动半透明遮罩，鼠标框选二维码区域"""
        screen_w = self.root.winfo_screenwidth()
        screen_h = self.root.winfo_screenheight()

        # 半透明遮罩窗口
        self.overlay = tk.Toplevel(self.root)
        self.overlay.attributes("-fullscreen", True)
        self.overlay.attributes("-alpha", 0.25)  # 透明度
        self.overlay.attributes("-topmost", True)
        self.overlay.configure(bg="gray")
        self.overlay.overrideredirect(True)  # 无边框

        # 画布用于画选择框
        self.canvas = tk.Canvas(self.overlay, cursor="cross", bg="gray", highlightthickness=0)
        self.canvas.pack(fill=tk.BOTH, expand=True)

        # 绑定鼠标事件
        self.canvas.bind("<ButtonPress-1>", self.on_press)
        self.canvas.bind("<B1-Motion>", self.on_drag)
        self.canvas.bind("<ButtonRelease-1>", self.on_release)

    def on_press(self, event):
        """鼠标按下：记录起点"""
        self.start_x = event.x
        self.start_y = event.y
        self.rect_id = self.canvas.create_rectangle(0,0,0,0, outline="red", width=2)

    def on_drag(self, event):
        """鼠标拖拽：绘制选区框"""
        self.canvas.coords(self.rect_id, self.start_x, self.start_y, event.x, event.y)

    def on_release(self, event):
        """鼠标松开：截取选区并识别"""
        x1, y1 = min(self.start_x, event.x), min(self.start_y, event.y)
        x2, y2 = max(self.start_x, event.x), max(self.start_y, event.y)

        # 关闭遮罩
        self.overlay.destroy()
        self.overlay = None

        # 区域过小直接忽略
        if (x2 - x1) < 20 or (y2 - y1) < 20:
            self.write_result("区域过小，无法识别")
            return

        # 截取选中区域
        try:
            # 全屏截图，直接框选
            image = ImageGrab.grab(bbox=(x1, y1, x2, y2))
            self.recognize_qr(image)

        except Exception as e:
            self.write_result(f"识别失败：{str(e)}")

    def recognize_qr(self, image):
        """用OpenCV识别二维码"""
        # PIL图像转OpenCV格式
        img_cv = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
        qr_detector = cv2.QRCodeDetector()
        data, _, _ = qr_detector.detectAndDecode(img_cv)

        if not data:
            self.write_result("未识别到二维码内容")
            return

        self.write_result(data)

    def write_result(self, text):
        """把识别结果/失败信息写入下方输入框"""
        self.text_input.delete("1.0", tk.END)
        self.text_input.insert("1.0", text)
        self.on_text_update(None)  # 同步更新二维码

    # ===================== 上传图片识别 =====================
    def upload_image(self):
        path = filedialog.askopenfilename(filetypes=[("图片", "*.png;*.jpg;*.jpeg")])
        if not path:
            return
        try:
            img = Image.open(path).convert("RGB")
            self.recognize_qr(img)
        except:
            self.write_result("图片打开失败")

if __name__ == "__main__":
    root = tk.Tk()
    app = QRCodeTool(root)
    root.mainloop()