use image::{Rgba, RgbaImage};
use qrcodegen::{QrCode, QrCodeEcc, QrSegment};

use crate::compress::compress_text;
use crate::logger::log;

/// 纠错等级
#[derive(Clone, Copy, PartialEq, Debug)]
pub enum ErrorCorrection {
    Low,    // 7%
    Medium, // 15%
    Quartile, // 25%
    High,   // 30%
}

impl std::fmt::Display for ErrorCorrection {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.label())
    }
}

impl ErrorCorrection {
    pub fn label(&self) -> &str {
        match self {
            ErrorCorrection::Low => "L 7%",
            ErrorCorrection::Medium => "M 15%",
            ErrorCorrection::Quartile => "Q 25%",
            ErrorCorrection::High => "H 30%",
        }
    }

    pub fn to_qr_ecc(&self) -> QrCodeEcc {
        match self {
            ErrorCorrection::Low => QrCodeEcc::Low,
            ErrorCorrection::Medium => QrCodeEcc::Medium,
            ErrorCorrection::Quartile => QrCodeEcc::Quartile,
            ErrorCorrection::High => QrCodeEcc::High,
        }
    }

    pub const ALL: [ErrorCorrection; 4] = [
        ErrorCorrection::Low,
        ErrorCorrection::Medium,
        ErrorCorrection::Quartile,
        ErrorCorrection::High,
    ];
}

/// 生成 QR 码图片
/// compress: 是否使用 Brotli+Base45 压缩模式
/// ecc: 纠错等级
/// 返回 600x600 的 RGBA 图片
pub fn generate_qr(content: &str, compress: bool, ecc: ErrorCorrection) -> Result<RgbaImage, String> {
    if content.is_empty() {
        return Err("内容为空".to_string());
    }

    let encode_content = if compress {
        compress_text(content)
    } else {
        content.to_string()
    };

    log(&format!(
        "编码内容长度: {} 字符, 模式: {}",
        encode_content.len(),
        if compress { "Brotli+Base45+Alphanumeric" } else { "UTF-8+Byte" }
    ));

    let qr_ecc = ecc.to_qr_ecc();

    // 尝试使用字母数字模式（压缩模式下内容全部是 Base45 字符集，属于 QR 字母数字字符集）
    let qr = if compress {
        // 压缩模式：手动创建字母数字段以确保使用字母数字模式
        if QrSegment::is_alphanumeric(&encode_content) {
            let seg = QrSegment::make_alphanumeric(&encode_content);
            match QrCode::encode_segments(&[seg], qr_ecc) {
                Ok(code) => code,
                Err(e) => return Err(format!("QR编码失败(内容可能过长): {}", e)),
            }
        } else {
            // 如果不是纯字母数字，回退到 encode_text
            match QrCode::encode_text(&encode_content, qr_ecc) {
                Ok(code) => code,
                Err(e) => return Err(format!("QR编码失败: {}", e)),
            }
        }
    } else {
        // 非压缩模式：使用 encode_text 自动选择最优编码
        match QrCode::encode_text(&encode_content, qr_ecc) {
            Ok(code) => code,
            Err(e) => return Err(format!("QR编码失败: {}", e)),
        }
    };

    // 渲染为 600x600 图片
    let img_size: u32 = 600;
    let margin: u32 = 2;
    let qr_size = qr.size() as u32;
    let scale = img_size / (qr_size + 2 * margin);

    let actual_size = (qr_size + 2 * margin) * scale;
    let offset_x = (img_size - actual_size) / 2;
    let offset_y = (img_size - actual_size) / 2;

    let mut img = RgbaImage::from_pixel(img_size, img_size, Rgba([255, 255, 255, 255]));

    for y in 0..qr_size {
        for x in 0..qr_size {
            if qr.get_module(x as i32, y as i32) {
                for dy in 0..scale {
                    for dx in 0..scale {
                        let px = offset_x + (x + margin) * scale + dx;
                        let py = offset_y + (y + margin) * scale + dy;
                        if px < img_size && py < img_size {
                            img.put_pixel(px, py, Rgba([0, 0, 0, 255]));
                        }
                    }
                }
            }
        }
    }

    log("二维码生成成功");
    Ok(img)
}
