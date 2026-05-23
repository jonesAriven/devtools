use image::{DynamicImage, GrayImage, Luma};

/// 缩放图像
pub fn scale_image(img: &GrayImage, scale: u32) -> GrayImage {
    let new_w = img.width() * scale;
    let new_h = img.height() * scale;
    let rgba = image::DynamicImage::ImageLuma8(img.clone())
        .resize_exact(new_w, new_h, image::imageops::FilterType::CatmullRom);
    rgba.to_luma8()
}

/// 阈值二值化
pub fn apply_threshold(img: &GrayImage, threshold: u8) -> GrayImage {
    let mut result = GrayImage::from_pixel(img.width(), img.height(), Luma([0u8]));
    for (x, y, pixel) in img.enumerate_pixels() {
        let val = pixel.0[0];
        result.put_pixel(x, y, Luma([if val > threshold { 255 } else { 0 }]));
    }
    result
}

/// Otsu 自适应阈值
pub fn apply_otsu_threshold(img: &GrayImage) -> GrayImage {
    let threshold = otsu_threshold(img);
    apply_threshold(img, threshold)
}

fn otsu_threshold(img: &GrayImage) -> u8 {
    let mut histogram = [0u32; 256];
    for pixel in img.pixels() {
        histogram[pixel.0[0] as usize] += 1;
    }

    let total = img.width() * img.height();
    let mut sum: f64 = 0.0;
    for i in 0..256 {
        sum += i as f64 * histogram[i] as f64;
    }

    let mut sum_b: f64 = 0.0;
    let mut w_b: u32 = 0;
    let mut max_variance: f64 = 0.0;
    let mut threshold: u8 = 0;

    for t in 0..256 {
        w_b += histogram[t];
        if w_b == 0 {
            continue;
        }
        let w_f = total - w_b;
        if w_f == 0 {
            break;
        }
        sum_b += t as f64 * histogram[t] as f64;
        let m_b = sum_b / w_b as f64;
        let m_f = (sum - sum_b) / w_f as f64;
        let variance = w_b as f64 * w_f as f64 * (m_b - m_f) * (m_b - m_f);
        if variance > max_variance {
            max_variance = variance;
            threshold = t as u8;
        }
    }
    threshold
}

/// 对比度增强
pub fn enhance_contrast(img: &GrayImage) -> GrayImage {
    let mut result = GrayImage::from_pixel(img.width(), img.height(), Luma([0u8]));
    for (x, y, pixel) in img.enumerate_pixels() {
        let gray = pixel.0[0];
        let enhanced = ((gray as i16 - 128) * 2 + 128).clamp(0, 255) as u8;
        result.put_pixel(x, y, Luma([enhanced]));
    }
    result
}

/// 反色
pub fn invert_colors(img: &GrayImage) -> GrayImage {
    let mut result = GrayImage::from_pixel(img.width(), img.height(), Luma([0u8]));
    for (x, y, pixel) in img.enumerate_pixels() {
        result.put_pixel(x, y, Luma([255 - pixel.0[0]]));
    }
    result
}

/// 去噪（三段式）
pub fn remove_noise(img: &GrayImage) -> GrayImage {
    let mut result = GrayImage::from_pixel(img.width(), img.height(), Luma([0u8]));
    for (x, y, pixel) in img.enumerate_pixels() {
        let gray = pixel.0[0];
        let val = if gray < 80 {
            0
        } else if gray > 180 {
            255
        } else {
            128
        };
        result.put_pixel(x, y, Luma([val]));
    }
    result
}

/// 将 DynamicImage 转为灰度图
pub fn to_gray(img: &DynamicImage) -> GrayImage {
    img.to_luma8()
}
