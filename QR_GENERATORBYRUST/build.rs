use std::env;
use std::fs;
use std::path::Path;

fn main() {
    let out_dir = env::var("OUT_DIR").unwrap();
    let ico_path = Path::new(&out_dir).join("app_icon.ico");

    // 生成 256x256 QR码风格图标
    let png_data = generate_qr_icon(256);

    // 将 PNG 包装为 ICO 格式
    let ico_data = create_ico_from_png(&png_data, 256, 256);
    fs::write(&ico_path, &ico_data).unwrap();

    // 嵌入 Windows 资源
    if cfg!(target_os = "windows") {
        let rc_path = Path::new(&out_dir).join("app_icon.rc");
        let rc_content = format!(
            "1 ICON \"{}\"",
            ico_path.to_str().unwrap().replace("\\", "\\\\")
        );
        fs::write(&rc_path, rc_content).unwrap();

        // 使用 rc.exe 编译资源
        let rc_exe = find_rc_exe();
        if let Some(rc) = rc_exe {
            let res_path = Path::new(&out_dir).join("app_icon.res");
            let status = std::process::Command::new(&rc)
                .arg("/fo")
                .arg(&res_path)
                .arg(&rc_path)
                .status()
                .expect("failed to run rc.exe");

            if status.success() {
                println!("cargo:rustc-link-arg={}", res_path.to_str().unwrap());
                println!("cargo:rerun-if-changed=build.rs");
            }
        }
    }
}

fn find_rc_exe() -> Option<String> {
    let program_files = env::var("ProgramFiles(x86)").unwrap_or_else(|_| r"C:\Program Files (x86)".to_string());
    let sdk_base = Path::new(&program_files).join("Windows Kits");
    if sdk_base.exists() {
        if let Ok(entries) = fs::read_dir(sdk_base.join("10").join("bin")) {
            let mut latest: Option<String> = None;
            for entry in entries.flatten() {
                let bin_dir = entry.path().join("x86").join("rc.exe");
                if bin_dir.exists() {
                    latest = Some(bin_dir.to_str().unwrap().to_string());
                }
            }
            return latest;
        }
    }
    None
}

fn generate_qr_icon(size: u32) -> Vec<u8> {
    use image::{ImageBuffer, Rgba};

    let mut img = ImageBuffer::<Rgba<u8>, Vec<u8>>::new(size, size);

    // 白色背景
    for pixel in img.pixels_mut() {
        *pixel = Rgba([255, 255, 255, 255]);
    }

    let module_count: i32 = 25;
    let margin: i32 = 2;
    let total_modules = module_count + 2 * margin;
    let module_size = size as f32 / total_modules as f32;

    let dark = Rgba([30, 60, 120, 255]); // 深蓝色

    let set_module = |img: &mut ImageBuffer<Rgba<u8>, Vec<u8>>, mx: i32, my: i32| {
        let x0 = ((mx + margin) as f32 * module_size) as u32;
        let y0 = ((my + margin) as f32 * module_size) as u32;
        let x1 = (((mx + margin + 1) as f32 * module_size).ceil()) as u32;
        let y1 = (((my + margin + 1) as f32 * module_size).ceil()) as u32;
        for y in y0..y1.min(size) {
            for x in x0..x1.min(size) {
                img.put_pixel(x, y, dark);
            }
        }
    };

    // 三个定位图案 (7x7)
    let draw_finder = |img: &mut ImageBuffer<Rgba<u8>, Vec<u8>>, ox: i32, oy: i32| {
        for dy in 0..7i32 {
            for dx in 0..7i32 {
                let is_outer = dy == 0 || dy == 6 || dx == 0 || dx == 6;
                let is_inner = dy >= 2 && dy <= 4 && dx >= 2 && dx <= 4;
                if is_outer || is_inner {
                    set_module(img, ox + dx, oy + dy);
                }
            }
        }
    };

    draw_finder(&mut img, 0, 0);
    draw_finder(&mut img, module_count - 7, 0);
    draw_finder(&mut img, 0, module_count - 7);

    // 定时图案 (水平)
    for x in 8..(module_count - 8) {
        if x % 2 == 0 {
            set_module(&mut img, x, 6);
        }
    }
    // 定时图案 (垂直)
    for y in 8..(module_count - 8) {
        if y % 2 == 0 {
            set_module(&mut img, 6, y);
        }
    }

    // 对齐图案 (5x5, 中心在 18,18)
    for dy in -2i32..=2 {
        for dx in -2i32..=2 {
            let is_outer = dx.abs() == 2 || dy.abs() == 2;
            let is_center = dx == 0 && dy == 0;
            if is_outer || is_center {
                set_module(&mut img, 18 + dx, 18 + dy);
            }
        }
    }

    // 数据区域 - 用伪随机模式填充
    let mut seed: u32 = 12345;
    let next_rand = |seed: &mut u32| -> bool {
        *seed = seed.wrapping_mul(1103515245).wrapping_add(12345);
        (*seed >> 16) & 1 == 1
    };

    for y in 0..module_count {
        for x in 0..module_count {
            let in_finder_tl = x < 8 && y < 8;
            let in_finder_tr = x >= module_count - 8 && y < 8;
            let in_finder_bl = x < 8 && y >= module_count - 8;
            let in_timing_h = y == 6 && x >= 8 && x < module_count - 8;
            let in_timing_v = x == 6 && y >= 8 && y < module_count - 8;
            let in_align = x >= 16 && x <= 20 && y >= 16 && y <= 20;

            if !in_finder_tl && !in_finder_tr && !in_finder_bl && !in_timing_h && !in_timing_v && !in_align {
                if next_rand(&mut seed) {
                    set_module(&mut img, x, y);
                }
            }
        }
    }

    let mut buf = std::io::Cursor::new(Vec::new());
    img.write_to(&mut buf, image::ImageFormat::Png).unwrap();
    buf.into_inner()
}

fn create_ico_from_png(png_data: &[u8], width: u32, height: u32) -> Vec<u8> {
    let mut ico = Vec::new();

    // ICO header
    ico.extend_from_slice(&0u16.to_le_bytes());      // reserved
    ico.extend_from_slice(&1u16.to_le_bytes());      // type: icon
    ico.extend_from_slice(&1u16.to_le_bytes());      // count: 1 image

    // Directory entry
    ico.push(if width >= 256 { 0 } else { width as u8 });  // 0 = 256
    ico.push(if height >= 256 { 0 } else { height as u8 }); // 0 = 256
    ico.push(0u8);                                    // color palette
    ico.push(0u8);                                    // reserved
    ico.extend_from_slice(&1u16.to_le_bytes());      // color planes
    ico.extend_from_slice(&32u16.to_le_bytes());     // bits per pixel
    ico.extend_from_slice(&(png_data.len() as u32).to_le_bytes()); // image size
    ico.extend_from_slice(&22u32.to_le_bytes());     // offset (6 + 16 = 22)

    // PNG image data
    ico.extend_from_slice(png_data);

    ico
}
