use std::sync::atomic::{AtomicBool, Ordering};

use image::DynamicImage;
use rqrr::PreparedImage;

use crate::compress::decompress_text;
use crate::image_proc;
use crate::logger::log;

static CANCEL_FLAG: AtomicBool = AtomicBool::new(false);

pub fn request_cancel() {
    CANCEL_FLAG.store(true, Ordering::SeqCst);
}

fn is_cancelled() -> bool {
    CANCEL_FLAG.load(Ordering::SeqCst)
}

/// 解码结果
pub struct DecodeResult {
    pub text: String,
    pub was_compressed: bool,
}

/// 从图片中解码 QR 码，使用 7 组多策略
pub fn decode_qr(img: &DynamicImage) -> Option<DecodeResult> {
    CANCEL_FLAG.store(false, Ordering::SeqCst);
    let gray = image_proc::to_gray(img);
    let result = try_decode_with_strategies(&gray);
    result.map(|text| {
        if let Some(decompressed) = decompress_text(&text) {
            DecodeResult {
                text: decompressed,
                was_compressed: true,
            }
        } else {
            DecodeResult {
                text,
                was_compressed: false,
            }
        }
    })
}

fn try_decode_with_strategies(img: &image::GrayImage) -> Option<String> {
    let sw = std::time::Instant::now();
    let mut strategy_group = 0;

    // 策略组1: 缩放 2x-5x
    strategy_group += 1;
    log(&format!(
        "策略组{}: 缩放 2x-5x, 图片{}x{}",
        strategy_group,
        img.width(),
        img.height()
    ));
    for scale in [2u32, 3, 4, 5] {
        if is_cancelled() { return None; }
        let scaled = image_proc::scale_image(img, scale);
        if let Some(text) = quick_decode(&scaled) {
            log(&format!("=> 解码策略: 缩放{}x, 耗时{}ms", scale, sw.elapsed().as_millis()));
            return Some(text);
        }
    }

    // 策略组2: 阈值二值化 50-210
    strategy_group += 1;
    log(&format!("策略组{}: 阈值 50-210", strategy_group));
    for thresh in [50u8, 70, 90, 110, 130, 150, 170, 190, 210] {
        if is_cancelled() { return None; }
        let binary = image_proc::apply_threshold(img, thresh);
        if let Some(text) = quick_decode(&binary) {
            log(&format!("=> 解码策略: 阈值{}, 耗时{}ms", thresh, sw.elapsed().as_millis()));
            return Some(text);
        }
    }

    // 策略组3: 阈值+缩放
    strategy_group += 1;
    log(&format!("策略组{}: 阈值+缩放", strategy_group));
    for thresh in [50u8, 70, 90, 110, 130, 150, 170, 190, 210] {
        if is_cancelled() { return None; }
        let binary = image_proc::apply_threshold(img, thresh);
        for scale in [2u32, 3, 4] {
            if is_cancelled() { return None; }
            let scaled = image_proc::scale_image(&binary, scale);
            if let Some(text) = quick_decode(&scaled) {
                log(&format!("=> 解码策略: 阈值{}+缩放{}x, 耗时{}ms", thresh, scale, sw.elapsed().as_millis()));
                return Some(text);
            }
        }
    }

    // 策略组4: Otsu+缩放
    strategy_group += 1;
    log(&format!("策略组{}: Otsu+缩放", strategy_group));
    let otsu = image_proc::apply_otsu_threshold(img);
    if let Some(text) = quick_decode(&otsu) {
        log(&format!("=> 解码策略: Otsu, 耗时{}ms", sw.elapsed().as_millis()));
        return Some(text);
    }
    for scale in [2u32, 3, 4, 5] {
        if is_cancelled() { return None; }
        let scaled = image_proc::scale_image(&otsu, scale);
        if let Some(text) = quick_decode(&scaled) {
            log(&format!("=> 解码策略: Otsu+缩放{}x, 耗时{}ms", scale, sw.elapsed().as_millis()));
            return Some(text);
        }
    }

    // 策略组5: 对比度增强+缩放
    strategy_group += 1;
    log(&format!("策略组{}: 对比度增强+缩放", strategy_group));
    let enhanced = image_proc::enhance_contrast(img);
    if let Some(text) = quick_decode(&enhanced) {
        log(&format!("=> 解码策略: 对比度增强, 耗时{}ms", sw.elapsed().as_millis()));
        return Some(text);
    }
    for scale in [2u32, 3, 4] {
        if is_cancelled() { return None; }
        let scaled = image_proc::scale_image(&enhanced, scale);
        if let Some(text) = quick_decode(&scaled) {
            log(&format!("=> 解码策略: 对比度增强+缩放{}x, 耗时{}ms", scale, sw.elapsed().as_millis()));
            return Some(text);
        }
    }

    // 策略组6: 反色
    strategy_group += 1;
    log(&format!("策略组{}: 反色", strategy_group));
    let inverted = image_proc::invert_colors(img);
    if let Some(text) = quick_decode(&inverted) {
        log(&format!("=> 解码策略: 反色, 耗时{}ms", sw.elapsed().as_millis()));
        return Some(text);
    }

    // 策略组7: 去噪+缩放
    strategy_group += 1;
    log(&format!("策略组{}: 去噪+缩放", strategy_group));
    let denoised = image_proc::remove_noise(img);
    if let Some(text) = quick_decode(&denoised) {
        log(&format!("=> 解码策略: 去噪, 耗时{}ms", sw.elapsed().as_millis()));
        return Some(text);
    }
    for scale in [2u32, 3] {
        if is_cancelled() { return None; }
        let scaled = image_proc::scale_image(&denoised, scale);
        if let Some(text) = quick_decode(&scaled) {
            log(&format!("=> 解码策略: 去噪+缩放{}x, 耗时{}ms", scale, sw.elapsed().as_millis()));
            return Some(text);
        }
    }

    log(&format!(
        "所有策略均未识别, 耗时{}ms",
        sw.elapsed().as_millis()
    ));
    None
}

fn quick_decode(img: &image::GrayImage) -> Option<String> {
    let mut prepared = PreparedImage::prepare(img.clone());
    let grids = prepared.detect_grids();
    for grid in grids {
        if let Ok((_, content)) = grid.decode() {
            return Some(content);
        }
    }
    None
}
