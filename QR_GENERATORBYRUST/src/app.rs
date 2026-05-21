use std::sync::mpsc::{self, Receiver};
use std::thread;

use iced::widget::{button, checkbox, column, container, image as iced_image, pick_list, row, scrollable, text, text_editor};
use iced::{Element, Length, window};

use crate::logger::log;
use crate::qr_decode;
use crate::qr_encode;
use crate::qr_encode::ErrorCorrection;
use crate::screenshot;

fn rgba_to_image_handle(img: image::RgbaImage) -> iced_image::Handle {
    let mut png_bytes = Vec::new();
    let dynamic = image::DynamicImage::ImageRgba8(img);
    dynamic.write_to(&mut std::io::Cursor::new(&mut png_bytes), image::ImageFormat::Png).unwrap();
    iced_image::Handle::from_bytes(png_bytes)
}

fn load_chinese_font() -> &'static [u8] {
    let font_paths = [
        "C:\\Windows\\Fonts\\msyh.ttc",      // 微软雅黑
        "C:\\Windows\\Fonts\\msyhbd.ttc",     // 微软雅黑粗体
        "C:\\Windows\\Fonts\\simhei.ttf",     // 黑体
        "C:\\Windows\\Fonts\\simsun.ttc",     // 宋体
    ];

    for path in &font_paths {
        if let Ok(bytes) = std::fs::read(path) {
            log(&format!("加载字体: {}", path));
            return Box::leak(bytes.into_boxed_slice());
        }
    }

    log("警告: 未找到中文字体");
    &[]
}

pub struct QRCodeApp {
    text_content: text_editor::Content,
    compress_mode: bool,
    error_correction: ErrorCorrection,
    qr_image: Option<iced_image::Handle>,
    status_message: String,
    decode_rx: Option<Receiver<Result<String, String>>>,
    decoding: bool,
}

#[derive(Debug, Clone)]
pub enum Message {
    TextChanged(text_editor::Action),
    CompressToggled(bool),
    EccSelected(ErrorCorrection),
    ScreenshotClicked,
    UploadClicked,
    CheckDecodeResult,
}

impl Default for QRCodeApp {
    fn default() -> Self {
        Self::new()
    }
}

impl QRCodeApp {
    pub fn new() -> Self {
        log("程序启动");
        Self {
            text_content: text_editor::Content::new(),
            compress_mode: true,
            error_correction: ErrorCorrection::Low,
            qr_image: None,
            status_message: String::new(),
            decode_rx: None,
            decoding: false,
        }
    }

    pub fn run(settings: window::Settings) -> iced::Result {
        // 加载系统中文字体
        let font_bytes = load_chinese_font();
        let default_font = iced::Font::with_name("Microsoft YaHei");

        iced::application("二维码工具（长文本增强版）", Self::update, Self::view)
            .window(settings)
            .font(font_bytes)
            .default_font(default_font)
            .run()
    }

    fn update(&mut self, message: Message) {
        match message {
            Message::TextChanged(action) => {
                self.text_content.perform(action);
                self.generate_qr();
            }
            Message::CompressToggled(value) => {
                self.compress_mode = value;
                self.generate_qr();
            }
            Message::EccSelected(ecc) => {
                self.error_correction = ecc;
                self.generate_qr();
            }
            Message::ScreenshotClicked => {
                self.start_screenshot_decode();
            }
            Message::UploadClicked => {
                self.start_upload_decode();
            }
            Message::CheckDecodeResult => {
                self.check_decode_result();
            }
        }
    }

    fn view(&self) -> Element<Message> {
        let toolbar = row![
            checkbox("压缩模式", self.compress_mode)
                .on_toggle(Message::CompressToggled),
            text(" 纠错:"),
            pick_list(
                ErrorCorrection::ALL.to_vec(),
                Some(self.error_correction),
                Message::EccSelected,
            ),
            button("📷").on_press(Message::ScreenshotClicked),
            button("📁").on_press(Message::UploadClicked),
        ]
        .spacing(8)
        .align_y(iced::Alignment::Center);

        let qr_display: Element<Message> = if let Some(handle) = &self.qr_image {
            container(iced_image(handle).width(Length::Fill).height(Length::Shrink))
                .width(Length::Fill)
                .center_x(Length::Fill)
                .into()
        } else {
            container(text("输入文字生成二维码").color([0.5, 0.5, 0.5]).size(16))
                .width(Length::Fill)
                .height(Length::Fixed(300.0))
                .center_x(Length::Fill)
                .center_y(Length::Fill)
                .into()
        };

        let status: Element<Message> = if self.decoding {
            row![text("正在识别...").color([0.5, 0.5, 0.5])]
                .spacing(4)
                .into()
        } else if !self.status_message.is_empty() {
            text(&self.status_message).color([0.5, 0.5, 0.5]).into()
        } else {
            column![].into()
        };

        let text_input = scrollable(
            text_editor(&self.text_content)
                .on_action(Message::TextChanged)
                .height(Length::Shrink)
        )
        .height(Length::Fill);

        column![
            toolbar,
            qr_display,
            status,
            text_input,
        ]
        .spacing(8)
        .padding(8)
        .into()
    }

    fn get_text(&self) -> String {
        self.text_content.text()
    }

    fn generate_qr(&mut self) {
        let content = self.get_text().trim().to_string();
        if content.is_empty() {
            self.qr_image = None;
            self.status_message.clear();
            return;
        }

        match qr_encode::generate_qr(&content, self.compress_mode, self.error_correction) {
            Ok(img) => {
                let handle = rgba_to_image_handle(img);
                self.qr_image = Some(handle);
                self.status_message.clear();
            }
            Err(e) => {
                self.qr_image = None;
                self.status_message = format!("生成二维码失败: {}", e);
            }
        }
    }

    fn start_screenshot_decode(&mut self) {
        log("开始截图识别");

        let capture = match screenshot::capture_screen() {
            Ok(c) => c,
            Err(e) => {
                self.status_message = format!("截图失败: {}", e);
                log(&format!("截图失败: {}", e));
                return;
            }
        };

        log(&format!("截图成功: {}x{}", capture.width, capture.height));

        let selection = screenshot::show_selection_overlay(
            capture.x,
            capture.y,
            capture.width,
            capture.height,
        );

        match selection {
            Some(sel) => {
                if sel.width < 40 || sel.height < 40 {
                    self.status_message = "选区过小".to_string();
                    log("选区过小");
                    return;
                }
                log(&format!("截图区域: x={}, y={}, w={}, h={}", sel.x, sel.y, sel.width, sel.height));

                let crop_x = (sel.x - capture.x).max(0) as u32;
                let crop_y = (sel.y - capture.y).max(0) as u32;
                let crop_w = sel.width as u32;
                let crop_h = sel.height as u32;

                let cropped = image::imageops::crop_imm(&capture.image, crop_x, crop_y, crop_w, crop_h);
                let cropped_img = image::DynamicImage::ImageRgba8(cropped.to_image());
                self.start_background_decode(cropped_img);
            }
            None => {
                self.status_message = "截图已取消".to_string();
                log("截图取消");
            }
        }
    }

    fn start_upload_decode(&mut self) {
        let file_path = rfd::FileDialog::new()
            .add_filter("图片", &["png", "jpg", "jpeg", "bmp"])
            .pick_file();

        match file_path {
            Some(path) => {
                log(&format!("上传图片: {:?}", path));
                match image::open(&path) {
                    Ok(img) => {
                        self.start_background_decode(img);
                    }
                    Err(e) => {
                        self.status_message = format!("打开图片失败: {}", e);
                        log(&format!("打开图片失败: {}", e));
                    }
                }
            }
            None => {}
        }
    }

    fn start_background_decode(&mut self, img: image::DynamicImage) {
        self.decoding = true;
        self.status_message = "正在识别...".to_string();

        let (tx, rx) = mpsc::channel();
        self.decode_rx = Some(rx);

        thread::spawn(move || {
            let result = match qr_decode::decode_qr(&img) {
                Some(decode_result) => Ok(decode_result.text),
                None => Err("未识别到二维码".to_string()),
            };
            let _ = tx.send(result);
        });
    }

    fn check_decode_result(&mut self) {
        if let Some(rx) = &self.decode_rx {
            if let Ok(result) = rx.try_recv() {
                self.decoding = false;
                self.decode_rx = None;
                match result {
                    Ok(text) => {
                        log(&format!("解码成功: {} 字符", text.len()));
                        self.text_content = text_editor::Content::with_text(&text);
                        self.status_message.clear();
                        self.generate_qr();
                    }
                    Err(e) => {
                        self.status_message = e.clone();
                        log(&e);
                    }
                }
            }
        }
    }
}
