use std::sync::mpsc::{self, Receiver, Sender};

use image::RgbaImage;
use windows::Win32::Foundation::*;
use windows::Win32::Graphics::Gdi::*;
use windows::Win32::System::LibraryLoader::GetModuleHandleW;
use windows::Win32::UI::HiDpi::*;
use windows::Win32::UI::Input::KeyboardAndMouse::VK_ESCAPE;
use windows::Win32::UI::WindowsAndMessaging::*;

use crate::logger::log;

/// 截图结果
pub struct ScreenCapture {
    pub image: RgbaImage,
    pub x: i32,
    pub y: i32,
    pub width: i32,
    pub height: i32,
}

/// 捕获整个虚拟屏幕
pub fn capture_screen() -> anyhow::Result<ScreenCapture> {
    unsafe {
        // 设置 DPI 感知
        let _ = SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);

        let x = GetSystemMetrics(SM_XVIRTUALSCREEN);
        let y = GetSystemMetrics(SM_YVIRTUALSCREEN);
        let w = GetSystemMetrics(SM_CXVIRTUALSCREEN);
        let h = GetSystemMetrics(SM_CYVIRTUALSCREEN);

        log(&format!("虚拟屏幕: {},{} {}x{}", x, y, w, h));

        let hdc_screen = GetDC(None);
        let hdc_mem = CreateCompatibleDC(hdc_screen);
        let hbitmap = CreateCompatibleBitmap(hdc_screen, w, h);
        let _old_bmp = SelectObject(hdc_mem, hbitmap);

        if BitBlt(hdc_mem, 0, 0, w, h, hdc_screen, x, y, SRCCOPY).is_err() {
            SelectObject(hdc_mem, _old_bmp);
            let _ = DeleteObject(hbitmap);
            let _ = DeleteDC(hdc_mem);
            ReleaseDC(None, hdc_screen);
            return Err(anyhow::anyhow!("BitBlt 失败"));
        }

        // 获取像素数据
        let mut bmi: BITMAPINFO = std::mem::zeroed();
        bmi.bmiHeader.biSize = std::mem::size_of::<BITMAPINFOHEADER>() as u32;
        bmi.bmiHeader.biWidth = w;
        bmi.bmiHeader.biHeight = -h; // 自顶向下
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32;
        bmi.bmiHeader.biCompression = BI_RGB.0;

        let mut pixels: Vec<u8> = vec![0u8; (w * h * 4) as usize];
        let scan_lines = GetDIBits(
            hdc_mem,
            hbitmap,
            0,
            h as u32,
            Some(pixels.as_mut_ptr() as *mut _),
            &mut bmi,
            DIB_RGB_COLORS,
        );

        if scan_lines == 0 {
            // 清理
            SelectObject(hdc_mem, _old_bmp);
            let _ = DeleteObject(hbitmap);
            let _ = DeleteDC(hdc_mem);
            ReleaseDC(None, hdc_screen);
            return Err(anyhow::anyhow!("GetDIBits 失败"));
        }

        // 清理 GDI 资源
        SelectObject(hdc_mem, _old_bmp);
        let _ = DeleteObject(hbitmap);
        let _ = DeleteDC(hdc_mem);
        ReleaseDC(None, hdc_screen);

        // BGRA -> RGBA
        for chunk in pixels.chunks_exact_mut(4) {
            chunk.swap(0, 2); // B <-> R
            // Alpha 设为 255
            chunk[3] = 255;
        }

        let image = RgbaImage::from_raw(w as u32, h as u32, pixels)
            .ok_or_else(|| anyhow::anyhow!("创建图像失败"))?;

        Ok(ScreenCapture {
            image,
            x,
            y,
            width: w,
            height: h,
        })
    }
}

/// 选区结果
pub struct Selection {
    pub x: i32,
    pub y: i32,
    pub width: i32,
    pub height: i32,
}

/// 显示半透明遮罩窗口，让用户选择区域
/// 返回 None 表示用户取消（ESC）
pub fn show_selection_overlay(screen_x: i32, screen_y: i32, screen_w: i32, screen_h: i32) -> Option<Selection> {
    unsafe {
        let (tx, rx): (Sender<Option<Selection>>, Receiver<Option<Selection>>) = mpsc::channel();

        // 注册窗口类
        let class_name: Vec<u16> = "QRSelectionOverlay\0".encode_utf16().collect();
        let h_instance: HINSTANCE = GetModuleHandleW(None).ok()?.into();

        let wnd_class = WNDCLASSW {
            hInstance: h_instance,
            lpszClassName: windows::core::PCWSTR(class_name.as_ptr()),
            style: CS_HREDRAW | CS_VREDRAW,
            lpfnWndProc: Some(selection_wnd_proc),
            ..Default::default()
        };

        let atom = RegisterClassW(&wnd_class);
        if atom == 0 {
            log("注册窗口类失败");
            return None;
        }

        // 创建分层窗口
        let hwnd = match CreateWindowExW(
            WS_EX_LAYERED | WS_EX_TOPMOST | WS_EX_TOOLWINDOW,
            windows::core::PCWSTR(class_name.as_ptr()),
            windows::core::PCWSTR::null(),
            WS_POPUP | WS_VISIBLE,
            screen_x,
            screen_y,
            screen_w,
            screen_h,
            None,
            None,
            h_instance,
            None,
        ) {
            Ok(h) => h,
            Err(_) => {
                log("创建遮罩窗口失败");
                return None;
            }
        };

        // 设置半透明（30% 不透明度 = 77/255）
        let _ = SetLayeredWindowAttributes(hwnd, COLORREF(0), 77, LWA_ALPHA);

        // 设置十字光标
        if let Ok(cursor) = LoadCursorW(None, IDC_CROSS) {
            SetClassLongPtrW(hwnd, GCLP_HCURSOR, cursor.0 as isize);
        }

        // 存储通道和屏幕信息到窗口属性
        // 使用全局变量传递（简化实现）
        OVERLAY_TX.with(|cell| *cell.borrow_mut() = Some(tx));
        SCREEN_BOUNDS.with(|cell| {
            *cell.borrow_mut() = Some((screen_x, screen_y, screen_w, screen_h));
        });

        let _ = ShowWindow(hwnd, SW_SHOW);
        let _ = UpdateWindow(hwnd);

        // 消息循环
        let mut msg = MSG::default();
        while GetMessageW(&mut msg, None, 0, 0).as_bool() {
            let _ = TranslateMessage(&msg);
            DispatchMessageW(&msg);

            // 检查是否收到结果
            if let Ok(result) = rx.try_recv() {
                let _ = DestroyWindow(hwnd);
                let _ = UnregisterClassW(windows::core::PCWSTR(class_name.as_ptr()), h_instance);
                return result;
            }
        }

        let _ = DestroyWindow(hwnd);
        let _ = UnregisterClassW(windows::core::PCWSTR(class_name.as_ptr()), h_instance);
        None
    }
}

use std::cell::RefCell;

thread_local! {
    static OVERLAY_TX: RefCell<Option<Sender<Option<Selection>>>> = RefCell::new(None);
    static SCREEN_BOUNDS: RefCell<Option<(i32, i32, i32, i32)>> = RefCell::new(None);
    static SELECTION_STATE: RefCell<SelectionState> = RefCell::new(SelectionState::default());
}

#[derive(Default)]
struct SelectionState {
    start_x: i32,
    start_y: i32,
    selecting: bool,
    sel_rect: RECT,
    has_selection: bool,
}

unsafe extern "system" fn selection_wnd_proc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    match msg {
        WM_LBUTTONDOWN => {
            let x = get_x_lparam(lparam);
            let y = get_y_lparam(lparam);
            SELECTION_STATE.with(|cell| {
                let mut state = cell.borrow_mut();
                state.start_x = x;
                state.start_y = y;
                state.selecting = true;
                state.has_selection = false;
                state.sel_rect = RECT::default();
            });
            LRESULT(0)
        }
        WM_MOUSEMOVE => {
            let x = get_x_lparam(lparam);
            let y = get_y_lparam(lparam);
            let should_invalidate = SELECTION_STATE.with(|cell| {
                let mut state = cell.borrow_mut();
                if !state.selecting {
                    return false;
                }
                let left = state.start_x.min(x);
                let top = state.start_y.min(y);
                let right = state.start_x.max(x);
                let bottom = state.start_y.max(y);
                state.sel_rect = RECT { left, top, right, bottom };
                state.has_selection = true;
                true
            });
            if should_invalidate {
                let _ = InvalidateRect(hwnd, None, false);
            }
            LRESULT(0)
        }
        WM_LBUTTONUP => {
            let rect = SELECTION_STATE.with(|cell| {
                let mut state = cell.borrow_mut();
                state.selecting = false;
                state.sel_rect
            });

            // 通知主线程选区完成
            OVERLAY_TX.with(|cell| {
                if let Some(tx) = cell.borrow_mut().as_ref() {
                    let _ = tx.send(Some(Selection {
                        x: rect.left,
                        y: rect.top,
                        width: rect.right - rect.left,
                        height: rect.bottom - rect.top,
                    }));
                }
            });
            LRESULT(0)
        }
        WM_KEYDOWN => {
            if wparam.0 as u16 == VK_ESCAPE.0 {
                // ESC 取消
                OVERLAY_TX.with(|cell| {
                    if let Some(tx) = cell.borrow_mut().as_ref() {
                        let _ = tx.send(None);
                    }
                });
            }
            LRESULT(0)
        }
        WM_PAINT => {
            let mut ps = PAINTSTRUCT::default();
            let hdc = BeginPaint(hwnd, &mut ps);

            // 绘制选区矩形
            SELECTION_STATE.with(|cell| {
                let state = cell.borrow();
                if state.has_selection {
                    let pen = CreatePen(PS_SOLID, 3, COLORREF(0x00FF00)); // 绿色
                    let old_pen = SelectObject(hdc, pen);
                    let old_brush = SelectObject(hdc, GetStockObject(NULL_BRUSH));

                    let _ = Rectangle(hdc, state.sel_rect.left, state.sel_rect.top, state.sel_rect.right, state.sel_rect.bottom);

                    // 绘制尺寸标注
                    let w = state.sel_rect.right - state.sel_rect.left;
                    let h = state.sel_rect.bottom - state.sel_rect.top;
                    let label = format!("{} × {}", w, h);
                    let label_wide: Vec<u16> = label.encode_utf16().collect();

                    // 标注位置
                    let label_x = state.sel_rect.left;
                    let label_y = state.sel_rect.top - 22;
                    let label_y = if label_y < 0 { state.sel_rect.bottom + 4 } else { label_y };

                    // 背景矩形
                    let mut text_size = SIZE { cx: 0, cy: 0 };
                    let _ = GetTextExtentPoint32W(hdc, &label_wide, &mut text_size);
                    let bg_rect = RECT {
                        left: label_x,
                        top: label_y,
                        right: label_x + text_size.cx + 8,
                        bottom: label_y + text_size.cy + 4,
                    };
                    let bg_brush = CreateSolidBrush(COLORREF(0x000000));
                    FillRect(hdc, &bg_rect, bg_brush);
                    let _ = DeleteObject(bg_brush);

                    SetTextColor(hdc, COLORREF(0x00FF00)); // 绿色文字
                    SetBkMode(hdc, TRANSPARENT);
                    let _ = TextOutW(hdc, label_x + 4, label_y + 2, &label_wide);

                    // 恢复 GDI 对象
                    SelectObject(hdc, old_pen);
                    SelectObject(hdc, old_brush);
                    let _ = DeleteObject(pen);
                }
            });

            let _ = EndPaint(hwnd, &ps);
            LRESULT(0)
        }
        WM_DESTROY => {
            // 确保通道被通知
            OVERLAY_TX.with(|cell| {
                if let Some(tx) = cell.borrow_mut().as_ref() {
                    let _ = tx.send(None);
                }
            });
            LRESULT(0)
        }
        _ => DefWindowProcW(hwnd, msg, wparam, lparam),
    }
}

// 辅助函数：从 LPARAM 提取坐标
fn get_x_lparam(lparam: LPARAM) -> i32 {
    (lparam.0 & 0xFFFF) as i16 as i32
}

fn get_y_lparam(lparam: LPARAM) -> i32 {
    ((lparam.0 >> 16) & 0xFFFF) as i16 as i32
}
