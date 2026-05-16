import tkinter as tk
from tkinter import ttk, filedialog
from PIL import Image, ImageTk, ImageGrab
import qrcode
import cv2
import numpy as np

class QRCodeTool:
    def __init__(self, root):
        self.root = root
        self.root.title("二维码工具（无DLL版）")
        self.root.geometry("520x650")
        self.root.resizable(False, False)

        # 二维码预览
        self.qr_frame = ttk.LabelFrame(root, text="二维码预览")
        self.qr_frame.pack(pady=10, padx=15, fill=tk.BOTH, expand=True)
        self.qr_label = ttk.Label(self.qr_frame)
        self.qr_label.pack(pady=20)
        self.update_qr_code("")

        # 按钮区
        self.btn_frame = ttk.Frame(root)
        self.btn_frame.pack(pady=5, padx=15, fill=tk.X)
        self.snap_btn = ttk.Button(self.btn_frame, text="截图识别", command=self.start_select_area)
        self.snap_btn.pack(side=tk.LEFT, padx=5, fill=tk.X, expand=True)
        self.upload_btn = ttk.Button(self.btn_frame, text="上传图片", command=self.upload_image)
        self.upload_btn.pack(side=tk.RIGHT, padx=5, fill=tk.X, expand=True)

        # 输入框
        self.text_label = ttk.Label(root, text="输入文本 / 识别结果")
        self.text_label.pack(pady=(10, 0), padx=15, anchor=tk.W)
        self.text_input = tk.Text(root, height=12, wrap=tk.WORD, font=("微软雅黑", 10))
        self.text_input.pack(pady=5, padx=15, fill=tk.BOTH, expand=True)
        self.text_input.bind("<KeyRelease>", self.on_text_update)

        self.start_x = self.start_y = self.rect_id = self.overlay = None
        self.qr_detector = cv2.QRCodeDetector()

    # ===================== 生成二维码（支持长文本） =====================
    def update_qr_code(self, content):
        if not content.strip():
            img = Image.new("RGB", (280, 280), "white")
        else:
            qr = qrcode.QRCode(
                version=20,
                error_correction=qrcode.constants.ERROR_CORRECT_H,
                box_size=6,
                border=8
            )
            qr.add_data(content)
            qr.make(fit=True)
            img = qr.make_image(fill_color="black", back_color="white").convert("RGB")
            img = img.resize((280, 280), Image.Resampling.LANCZOS)

        self.photo = ImageTk.PhotoImage(img)
        self.qr_label.config(image=self.photo)

    def on_text_update(self, event):
        text = self.text_input.get("1.0", tk.END).strip()
        self.update_qr_code(text)

    # ===================== 截图选区 =====================
    def start_select_area(self):
        self.overlay = tk.Toplevel(self.root)
        self.overlay.attributes("-fullscreen", True)
        self.overlay.attributes("-alpha", 0.25)
        self.overlay.attributes("-topmost", True)
        self.overlay.configure(bg="gray")
        self.overlay.overrideredirect(True)

        self.canvas = tk.Canvas(self.overlay, cursor="cross", bg="gray", highlightthickness=0)
        self.canvas.pack(fill=tk.BOTH, expand=True)
        self.canvas.bind("<ButtonPress-1>", self.on_press)
        self.canvas.bind("<B1-Motion>", self.on_drag)
        self.canvas.bind("<ButtonRelease-1>", self.on_release)

    def on_press(self, event):
        self.start_x = event.x
        self.start_y = event.y
        self.rect_id = self.canvas.create_rectangle(0,0,0,0, outline="red", width=2)

    def on_drag(self, event):
        self.canvas.coords(self.rect_id, self.start_x, self.start_y, event.x, event.y)

    def on_release(self, event):
        x1, y1 = min(self.start_x, event.x), min(self.start_y, event.y)
        x2, y2 = max(self.start_x, event.x), max(self.start_y, event.y)
        self.overlay.destroy()

        if (x2 - x1) < 30 or (y2 - y1) < 30:
            self.write_result("区域过小，无法识别")
            return

        try:
            image = ImageGrab.grab(bbox=(x1, y1, x2, y2))
            self.recognize_qr(image)
        except Exception as e:
            self.write_result(f"识别失败：{str(e)}")

    # ===================== OpenCV 原生识别（无DLL依赖） =====================
    def recognize_qr(self, pil_image):
        img = np.array(pil_image.convert("RGB"))
        img = cv2.cvtColor(img, cv2.COLOR_RGB2BGR)
        img = cv2.resize(img, None, fx=2.0, fy=2.0, interpolation=cv2.INTER_CUBIC)

        # 多预处理增强识别率
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        thresh1 = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
        thresh2 = cv2.threshold(gray, 100, 255, cv2.THRESH_BINARY)[1]

        for t in [img, thresh1, thresh2]:
            data, _, _ = self.qr_detector.detectAndDecode(t)
            if data:
                self.write_result(data)
                return

        self.write_result("未识别到二维码（内容过长/密度过高）")

    def write_result(self, text):
        self.text_input.delete("1.0", tk.END)
        self.text_input.insert("1.0", text)
        self.on_text_update(None)

    # ===================== 上传图片 =====================
    def upload_image(self):
        path = filedialog.askopenfilename(filetypes=[("图片", "*.png;*.jpg;*.jpeg")])
        if not path:
            return
        try:
            img = Image.open(path)
            self.recognize_qr(img)
        except:
            self.write_result("图片打开失败")

if __name__ == "__main__":
    root = tk.Tk()
    app = QRCodeTool(root)
    root.mainloop()