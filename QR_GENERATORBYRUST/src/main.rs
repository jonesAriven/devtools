#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod app;
mod compress;
mod image_proc;
mod logger;
mod qr_decode;
mod qr_encode;
mod screenshot;

fn main() {
    if let Err(e) = app::QRCodeApp::run(iced::window::Settings {
        size: iced::Size::new(440.0, 560.0),
        min_size: Some(iced::Size::new(440.0, 460.0)),
        icon: None,
        ..Default::default()
    }) {
        // 显示错误弹窗
        let msg = format!("程序启动失败:\n\n{}", e);
        unsafe {
            windows::Win32::UI::WindowsAndMessaging::MessageBoxW(
                None,
                &windows::core::HSTRING::from(&msg),
                &windows::core::HSTRING::from("二维码工具 - 错误"),
                windows::Win32::UI::WindowsAndMessaging::MB_ICONERROR,
            );
        }
    }
}
