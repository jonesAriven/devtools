import cv2
import numpy as np
from PIL import Image
import qrcode
from mss import MSS

def decode_qr(image):
    """二维码解码函数"""
    print(f"输入图像模式: {image.mode}")
    print(f"输入图像尺寸: {image.size}")
    
    img = np.array(image)
    print(f"转换后数组形状: {img.shape}")
    print(f"转换后数组类型: {img.dtype}")
    
    if img.dtype != np.uint8:
        if img.dtype == np.bool_:
            img = img.astype(np.uint8) * 255
        else:
            img = img.astype(np.uint8)
        print(f"转换后数组类型: {img.dtype}")
    
    if len(img.shape) == 2:
        gray = img
    elif img.shape[2] == 4:
        gray = cv2.cvtColor(img, cv2.COLOR_RGBA2GRAY)
    elif img.shape[2] == 3:
        gray = cv2.cvtColor(img, cv2.COLOR_RGB2GRAY)
    else:
        print(f"未知图像格式: {img.shape}")
        return None
    
    print(f"灰度图形状: {gray.shape}")
    
    detector = cv2.QRCodeDetector()
    data, points, _ = detector.detectAndDecode(gray)
    
    if data:
        print(f"直接识别成功: {data}")
        return data
    
    alpha = 2.0
    beta = 0
    enhanced = cv2.convertScaleAbs(gray, alpha=alpha, beta=beta)
    data, points, _ = detector.detectAndDecode(enhanced)
    if data:
        print(f"增强后识别成功: {data}")
        return data
    
    _, binary = cv2.threshold(gray, 127, 255, cv2.THRESH_BINARY)
    data, points, _ = detector.detectAndDecode(binary)
    if data:
        print(f"二值化后识别成功: {data}")
        return data
    
    print("未识别到二维码")
    return None

print("=== 测试真实截图识别 ===")

# 生成一个大的二维码并保存
test_text = "1234567890"
print(f"测试文本: {test_text}")

qr = qrcode.QRCode(version=1, box_size=15, border=4)
qr.add_data(test_text)
qr.make(fit=True)
img_pil = qr.make_image(fill_color="black", back_color="white")
img_pil.save("large_test_qr.png")
print(f"生成二维码尺寸: {img_pil.size}")

# 测试直接从PIL图像识别
print("\n--- 测试从PIL图像识别 ---")
result = decode_qr(img_pil)
print(f"识别成功: {result == test_text}")

# 测试从文件加载识别
print("\n--- 测试从文件加载识别 ---")
img_from_file = Image.open("large_test_qr.png")
print(f"文件图像模式: {img_from_file.mode}")
result = decode_qr(img_from_file)
print(f"识别成功: {result == test_text}")

# 测试截图识别
print("\n--- 测试截图识别 ---")
try:
    with MSS() as sct:
        monitor = sct.monitors[1]
        # 截取整个屏幕
        screenshot = sct.grab(monitor)
        
        print(f"截图尺寸: {screenshot.size}")
        
        # 转换为PIL图像
        image = Image.frombytes('RGB', screenshot.size, screenshot.bgra, 'raw', 'BGRX')
        print(f"转换后图像模式: {image.mode}")
        
        # 保存截图
        image.save("full_screenshot.png")
        print("截图已保存")
        
        # 尝试识别截图中的二维码
        result = decode_qr(image)
        print(f"截图识别结果: {result}")
        
except Exception as e:
    print(f"截图失败: {e}")

# 清理
import os
if os.path.exists("large_test_qr.png"):
    os.remove("large_test_qr.png")
if os.path.exists("full_screenshot.png"):
    os.remove("full_screenshot.png")

print("\n测试完成！")
