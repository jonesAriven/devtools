import os
import sys
import numpy as np
import cv2
import qrcode
from PIL import Image

def test_qr_detection():
    print("=== 测试二维码识别 ===")
    
    # 1. 生成一个测试二维码（使用更大的尺寸）
    test_text = "1231313131321"
    print(f"生成测试二维码，内容: {test_text}")
    
    qr = qrcode.QRCode(
        version=1,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=20,  # 更大的box尺寸
        border=4,
    )
    qr.add_data(test_text)
    qr.make(fit=True)
    
    qr_image = qr.make_image(fill_color="black", back_color="white")
    # 不缩放，保持原始大小
    qr_image.save("test_qr.png")
    print(f"测试图片已保存: test_qr.png")
    print(f"图片尺寸: {qr_image.size}")
    
    # 2. 测试OpenCV WeChatQRCode
    print("\n--- 测试 OpenCV WeChatQRCode ---")
    try:
        cv2_base_dir = os.path.dirname(cv2.__file__)
        model_dir = os.path.join(cv2_base_dir, 'data')
        
        detect_prototxt = os.path.join(model_dir, 'detect.prototxt')
        detect_caffe_model = os.path.join(model_dir, 'detect.caffemodel')
        sr_prototxt = os.path.join(model_dir, 'sr.prototxt')
        sr_caffe_model = os.path.join(model_dir, 'sr.caffe_model')
        
        print(f"模型目录: {model_dir}")
        print(f"detect.prototxt 存在: {os.path.exists(detect_prototxt)}")
        print(f"detect.caffemodel 存在: {os.path.exists(detect_caffe_model)}")
        print(f"sr.prototxt 存在: {os.path.exists(sr_prototxt)}")
        print(f"sr.caffe_model 存在: {os.path.exists(sr_caffe_model)}")
        
        if all([os.path.exists(f) for f in [detect_prototxt, detect_caffe_model, sr_prototxt, sr_caffe_model]]):
            qr_detector = cv2.wechat_qrcode_WeChatQRCode(
                detect_prototxt, detect_caffe_model, sr_prototxt, sr_caffe_model
            )
            
            img = cv2.imread("test_qr.png")
            result, points = qr_detector.detectAndDecode(img)
            
            if len(result) > 0:
                print(f"✅ WeChatQRCode识别成功! 内容: {result}")
            else:
                print("❌ WeChatQRCode未识别到二维码")
        else:
            print("❌ WeChatQRCode模型文件不存在")
    except Exception as e:
        print(f"❌ WeChatQRCode测试失败: {e}")
    
    # 3. 测试普通QRCodeDetector
    print("\n--- 测试 OpenCV QRCodeDetector ---")
    try:
        img = cv2.imread("test_qr.png")
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        
        detector = cv2.QRCodeDetector()
        data, points, _ = detector.detectAndDecode(gray)
        
        if data:
            print(f"✅ QRCodeDetector识别成功! 内容: {data}")
        else:
            print("❌ QRCodeDetector未识别到二维码")
            
            # 尝试各种预处理
            print("\n--- 尝试图像预处理 ---")
            
            # 增加对比度
            alpha = 2.0
            beta = 0
            enhanced = cv2.convertScaleAbs(gray, alpha=alpha, beta=beta)
            data, _, _ = detector.detectAndDecode(enhanced)
            print(f"增强后: {'成功' if data else '失败'}")
            
            # 二值化
            _, binary = cv2.threshold(gray, 127, 255, cv2.THRESH_BINARY)
            data, _, _ = detector.detectAndDecode(binary)
            print(f"二值化后: {'成功' if data else '失败'}")
            
            # 高斯模糊
            blurred = cv2.GaussianBlur(gray, (3, 3), 0)
            data, _, _ = detector.detectAndDecode(blurred)
            print(f"高斯模糊后: {'成功' if data else '失败'}")
            
            # 尝试不同尺寸
            print("\n--- 尝试不同尺寸 ---")
            for scale in [0.5, 0.75, 1.5, 2.0]:
                resized = cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_LINEAR)
                data, _, _ = detector.detectAndDecode(resized)
                print(f"缩放 {scale}x: {'成功' if data else '失败'}")
                
    except Exception as e:
        print(f"❌ QRCodeDetector测试失败: {e}")
    
    # 4. 测试从PIL图像识别
    print("\n--- 测试从PIL图像识别 ---")
    try:
        img_pil = Image.open("test_qr.png")
        img_np = np.array(img_pil)
        
        # 确保是uint8类型
        if img_np.dtype != np.uint8:
            img_np = img_np.astype(np.uint8)
        
        if len(img_np.shape) == 3 and img_np.shape[2] == 3:
            gray = cv2.cvtColor(img_np, cv2.COLOR_RGB2GRAY)
        elif len(img_np.shape) == 3 and img_np.shape[2] == 4:
            gray = cv2.cvtColor(img_np, cv2.COLOR_RGBA2GRAY)
        else:
            gray = img_np
        
        detector = cv2.QRCodeDetector()
        data, points, _ = detector.detectAndDecode(gray)
        
        if data:
            print(f"✅ 从PIL图像识别成功! 内容: {data}")
        else:
            print("❌ 从PIL图像未识别到二维码")
            
    except Exception as e:
        print(f"❌ 从PIL图像识别失败: {e}")
    
    # 清理
    if os.path.exists("test_qr.png"):
        os.remove("test_qr.png")
        print(f"\n已清理测试文件")


if __name__ == "__main__":
    test_qr_detection()
