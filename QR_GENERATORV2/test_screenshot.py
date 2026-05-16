import cv2
import numpy as np
from PIL import Image
import qrcode
from mss import mss
import time

def decode_qr(image):
    """二维码解码函数"""
    img = np.array(image)
    
    if img.dtype != np.uint8:
        if img.dtype == np.bool_:
            img = img.astype(np.uint8) * 255
        else:
            img = img.astype(np.uint8)
    
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
    data, points, _ = detector.detectAndDecode(gray)
    
    if data:
        return data
    
    alpha = 2.0
    beta = 0
    enhanced = cv2.convertScaleAbs(gray, alpha=alpha, beta=beta)
    data, points, _ = detector.detectAndDecode(enhanced)
    if data:
        return data
    
    _, binary = cv2.threshold(gray, 127, 255, cv2.THRESH_BINARY)
    data, points, _ = detector.detectAndDecode(binary)
    if data:
        return data
    
    return None

# 测试截图识别流程
print("=== 测试截图识别流程 ===")

# 1. 生成并保存二维码图片
test_text = "test_screenshot_123"
print(f"生成测试二维码: {test_text}")
qr = qrcode.QRCode(version=1, box_size=10, border=4)
qr.add_data(test_text)
qr.make(fit=True)
img_pil = qr.make_image(fill_color="black", back_color="white")
img_pil.save("test_qr_for_screenshot.png")
print("二维码已保存")

# 2. 测试模拟截图流程
print("\n--- 测试模拟截图 ---")
# 从文件加载模拟截图
screenshot_img = Image.open("test_qr_for_screenshot.png")
print(f"截图图像模式: {screenshot_img.mode}")
print(f"截图图像尺寸: {screenshot_img.size}")

# 模拟mss的BGRX转换
if screenshot_img.mode == 'RGB':
    # 模拟mss的转换方式
    img_bytes = screenshot_img.tobytes()
    # mss返回的是BGRA格式，我们模拟一下
    import struct
    new_bytes = b''
    for i in range(0, len(img_bytes), 3):
        r, g, b = img_bytes[i], img_bytes[i+1], img_bytes[i+2]
        new_bytes += struct.pack('BBBB', b, g, r, 255)  # BGRA
    
    # 转换回RGB（模拟代码中的转换）
    img_from_bytes = Image.frombytes('RGB', screenshot_img.size, new_bytes, 'raw', 'BGRX')
    print(f"转换后图像模式: {img_from_bytes.mode}")
    
    # 识别
    result = decode_qr(img_from_bytes)
    print(f"识别结果: {result}")
    print(f"识别成功: {result == test_text}")

# 3. 测试真实截图（截取屏幕左上角区域）
print("\n--- 测试真实截图 ---")
try:
    with mss() as sct:
        monitor = sct.monitors[1]
        # 截取左上角小区域
        screenshot = sct.grab({
            'left': monitor['left'],
            'top': monitor['top'],
            'width': 300,
            'height': 300
        })
        
        print(f"截图尺寸: {screenshot.size}")
        print(f"截图类型: {type(screenshot)}")
        
        # 转换为PIL图像（和代码中一样）
        image = Image.frombytes('RGB', screenshot.size, screenshot.bgra, 'raw', 'BGRX')
        print(f"转换后图像模式: {image.mode}")
        
        # 保存截图供调试
        image.save("debug_screenshot.png")
        print("截图已保存到 debug_screenshot.png")
        
except Exception as e:
    print(f"截图失败: {e}")

# 清理
import os
if os.path.exists("test_qr_for_screenshot.png"):
    os.remove("test_qr_for_screenshot.png")
print("\n测试完成！")
