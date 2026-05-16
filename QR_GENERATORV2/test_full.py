import cv2
import numpy as np
from PIL import Image
import qrcode

def decode_qr(image):
    """修复后的二维码解码函数"""
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
    
    # 尝试直接识别
    detector = cv2.QRCodeDetector()
    data, points, _ = detector.detectAndDecode(gray)
    
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
    
    return None

# 测试1：生成二维码并识别
print("=== 测试1：生成二维码并识别 ===")
test_text = "1231313131321"
qr = qrcode.QRCode(version=1, box_size=10, border=4)
qr.add_data(test_text)
qr.make(fit=True)
img_pil = qr.make_image(fill_color="black", back_color="white")

result = decode_qr(img_pil)
print(f"原始文本: {test_text}")
print(f"识别结果: {result}")
print(f"识别成功: {result == test_text}")
print()

# 测试2：测试不同大小的二维码
print("=== 测试2：不同大小的二维码 ===")
for box_size in [5, 10, 15, 20]:
    qr = qrcode.QRCode(version=1, box_size=box_size, border=4)
    qr.add_data("test")
    qr.make(fit=True)
    img_pil = qr.make_image(fill_color="black", back_color="white")
    result = decode_qr(img_pil)
    print(f"box_size={box_size}, 尺寸={img_pil.size}, 识别结果={result}, 成功={result == 'test'}")
print()

# 测试3：测试截图模拟（mss返回的是bgra格式）
print("=== 测试3：模拟截图格式（BGRA）===")
# 创建一个简单的黑白测试图像模拟截图
test_img = np.zeros((100, 100, 4), dtype=np.uint8)
# 模拟一些黑白块
test_img[:50, :50] = [0, 0, 0, 255]     # 黑色
test_img[50:, 50:] = [255, 255, 255, 255] # 白色
test_img[:50, 50:] = [255, 255, 255, 255] # 白色
test_img[50:, :50] = [0, 0, 0, 255]     # 黑色

# 转换为PIL图像（模拟mss的截图转换方式）
img_from_array = Image.frombytes('RGB', (100, 100), test_img.tobytes(), 'raw', 'BGRX')
print(f"模拟截图图像模式: {img_from_array.mode}")
print(f"模拟截图图像尺寸: {img_from_array.size}")
print("测试完成！")
