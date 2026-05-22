#include "MainWindow.h"
#include "QrGenerator.h"
#include "QrDecoder.h"
#include "Compressor.h"
#include "ScreenCapture.h"
#include <commdlg.h>
#include <wingdi.h>
#include <commctrl.h>
#include <algorithm>
#include <vector>
#include <fstream>
#include <ctime>
#include <sstream>

// Simple file logger
static void logToFile(const std::string& msg) {
    std::string path = "qr_debug.log";
    std::ofstream f(path, std::ios::app);
    if (f.is_open()) {
        time_t now = time(nullptr);
        struct tm t;
        localtime_s(&t, &now);
        char ts[32];
        strftime(ts, sizeof(ts), "%H:%M:%S", &t);
        f << "[" << ts << "] " << msg << std::endl;
    }
}

// Control IDs
#define IDC_CHK_COMPRESS  1001
#define IDC_BTN_CAPTURE   1002
#define IDC_BTN_UPLOAD    1003
#define IDC_TXT_CONTENT   1005
#define IDT_TEXT_CHANGED   2001

namespace qr {

static const wchar_t CLASS_NAME[] = L"QRCodeToolClass";
static const int TOOLBAR_HEIGHT = 30;
static const int PADDING = 10;
static const int MIN_WIDTH = 440;
static const int MIN_HEIGHT = 460;
static const int DEBOUNCE_MS = 300;

MainWindow::MainWindow(HINSTANCE hInstance)
    : m_hInstance(hInstance)
    , m_hWnd(nullptr)
    , m_hLblCompress(nullptr)
    , m_hBtnCapture(nullptr)
    , m_hBtnUpload(nullptr)
    , m_hTxtContent(nullptr)
    , m_hQrBitmap(nullptr)
    , m_hFont(nullptr)
    , m_compress(true)
    , m_lastCompressed(false)
    , m_qrRect({})
{
}

MainWindow::~MainWindow()
{
    if (m_hTxtContent) {
        RemoveWindowSubclass(m_hTxtContent, EditSubclassProc, 0);
    }
    if (m_hQrBitmap) {
        DeleteObject(m_hQrBitmap);
        m_hQrBitmap = nullptr;
    }
    if (m_hFont) {
        DeleteObject(m_hFont);
        m_hFont = nullptr;
    }
}

bool MainWindow::Create()
{
    // Create a hand-drawn 16x16 QR-like icon
    static BYTE andMask[32] = {
        0xFF, 0xFF, 0xC0, 0x03, 0xBF, 0xFD, 0xBF, 0xFD,
        0xAF, 0xF5, 0xAF, 0xF5, 0xBF, 0xFD, 0xBF, 0xFD,
        0xC0, 0x03, 0xFF, 0xFF, 0xF0, 0x0F, 0xCF, 0xF3,
        0xCF, 0xF3, 0xF0, 0x0F, 0xFF, 0xFF, 0xFF, 0xFF
    };
    static BYTE xorMask[32] = {
        0x00, 0x00, 0x3F, 0xFC, 0x20, 0x04, 0x2D, 0x44,
        0x20, 0x04, 0x2D, 0x44, 0x20, 0x04, 0x2D, 0x44,
        0x3F, 0xFC, 0x00, 0x00, 0x0F, 0xF0, 0x08, 0x10,
        0x08, 0x10, 0x0F, 0xF0, 0x00, 0x00, 0x00, 0x00
    };

    ICONINFO iconInfo = {};
    iconInfo.fIcon = TRUE;
    iconInfo.xHotspot = 0;
    iconInfo.yHotspot = 0;
    iconInfo.hbmMask = CreateBitmap(16, 16, 1, 1, andMask);
    iconInfo.hbmColor = CreateBitmap(16, 16, 1, 1, xorMask);
    HICON hIcon = CreateIconIndirect(&iconInfo);

    WNDCLASSEXW wc = {};
    wc.cbSize = sizeof(wc);
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = WndProc;
    wc.hInstance = m_hInstance;
    wc.hIcon = hIcon;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    wc.lpszClassName = CLASS_NAME;
    wc.hIconSm = hIcon;

    if (!RegisterClassExW(&wc)) {
        DWORD err = GetLastError();
        if (err != ERROR_CLASS_ALREADY_EXISTS) {
            return false;
        }
    }

    m_hWnd = CreateWindowExW(
        0,
        CLASS_NAME,
        L"QR Code Tool",
        WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT, CW_USEDEFAULT,
        MIN_WIDTH, 560,
        nullptr, nullptr,
        m_hInstance,
        this
    );

    if (!m_hWnd) {
        return false;
    }

    return true;
}

void MainWindow::Show(int nCmdShow)
{
    ShowWindow(m_hWnd, nCmdShow);
    UpdateWindow(m_hWnd);
}

LRESULT CALLBACK MainWindow::EditSubclassProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam, UINT_PTR uIdSubclass, DWORD_PTR dwRefData) {
    if (msg == WM_CHAR && wParam == 1) { // Ctrl+A
        SendMessage(hWnd, EM_SETSEL, 0, -1);
        return 0;
    }
    return DefSubclassProc(hWnd, msg, wParam, lParam);
}

LRESULT CALLBACK MainWindow::WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam)
{
    MainWindow* pThis = nullptr;

    if (message == WM_NCCREATE) {
        CREATESTRUCTW* cs = reinterpret_cast<CREATESTRUCTW*>(lParam);
        pThis = reinterpret_cast<MainWindow*>(cs->lpCreateParams);
        pThis->m_hWnd = hWnd;
        SetWindowLongPtrW(hWnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(pThis));
    } else {
        pThis = reinterpret_cast<MainWindow*>(GetWindowLongPtrW(hWnd, GWLP_USERDATA));
    }

    if (pThis) {
        return pThis->HandleMessage(message, wParam, lParam);
    }

    return DefWindowProcW(hWnd, message, wParam, lParam);
}

LRESULT MainWindow::HandleMessage(UINT message, WPARAM wParam, LPARAM lParam)
{
    switch (message) {
    case WM_CREATE:
        BuildUI();
        return 0;

    case WM_SIZE:
        ResizeControls();
        return 0;

    case WM_GETMINMAXINFO: {
        MINMAXINFO* mmi = reinterpret_cast<MINMAXINFO*>(lParam);
        mmi->ptMinTrackSize.x = MIN_WIDTH;
        mmi->ptMinTrackSize.y = MIN_HEIGHT;
        return 0;
    }

    case WM_COMMAND: {
        int wmId = LOWORD(wParam);
        int wmEvent = HIWORD(wParam);
        switch (wmId) {
        case IDC_BTN_CAPTURE:
            OnCapture();
            break;
        case IDC_BTN_UPLOAD:
            OnUpload();
            break;
        case IDC_TXT_CONTENT:
            if (wmEvent == EN_CHANGE) {
                OnTextChanged();
            }
            break;
        default:
            return DefWindowProcW(m_hWnd, message, wParam, lParam);
        }
        return 0;
    }

    case WM_TIMER:
        if (wParam == IDT_TEXT_CHANGED) {
            KillTimer(m_hWnd, IDT_TEXT_CHANGED);
            GenerateQr();
        }
        return 0;

    case WM_CTLCOLOREDIT: {
        HDC hdcEdit = reinterpret_cast<HDC>(wParam);
        SetBkColor(hdcEdit, RGB(255, 255, 255));
        return reinterpret_cast<LRESULT>(GetStockObject(WHITE_BRUSH));
    }

    case WM_ERASEBKGND:
        return 1;

    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(m_hWnd, &ps);

        RECT rc;
        GetClientRect(m_hWnd, &rc);

        // Fill background
        HBRUSH hBgBrush = CreateSolidBrush(RGB(240, 240, 240));
        FillRect(hdc, &rc, hBgBrush);
        DeleteObject(hBgBrush);

        // Draw QR code
        PaintQrCode(hdc, rc);

        EndPaint(m_hWnd, &ps);
        return 0;
    }

    case WM_DESTROY:
        KillTimer(m_hWnd, IDT_TEXT_CHANGED);
        PostQuitMessage(0);
        return 0;

    default:
        return DefWindowProcW(m_hWnd, message, wParam, lParam);
    }
}

void MainWindow::PaintQrCode(HDC hdc, const RECT& clientRect)
{
    // Draw white background for QR area
    HBRUSH hWhiteBrush = CreateSolidBrush(RGB(255, 255, 255));
    FillRect(hdc, &m_qrRect, hWhiteBrush);
    DeleteObject(hWhiteBrush);

    // Draw border
    HPEN hBorderPen = CreatePen(PS_SOLID, 1, RGB(180, 180, 180));
    HPEN hOldPen = (HPEN)SelectObject(hdc, hBorderPen);
    HBRUSH hOldBrush = (HBRUSH)SelectObject(hdc, GetStockObject(NULL_BRUSH));
    Rectangle(hdc, m_qrRect.left, m_qrRect.top, m_qrRect.right, m_qrRect.bottom);
    SelectObject(hdc, hOldBrush);
    SelectObject(hdc, hOldPen);
    DeleteObject(hBorderPen);

    if (!m_hQrBitmap) return;

    // Get bitmap dimensions
    BITMAP bm;
    GetObject(m_hQrBitmap, sizeof(bm), &bm);

    // Calculate centered, aspect-ratio-preserving draw rect
    int bmpW = bm.bmWidth;
    int bmpH = bm.bmHeight;
    int areaW = m_qrRect.right - m_qrRect.left;
    int areaH = m_qrRect.bottom - m_qrRect.top;

    // Scale to fit, maintaining aspect ratio
    float scale = (float)(std::min)(areaW, areaH) / (float)(std::max)(bmpW, bmpH);
    int drawW = (int)(bmpW * scale);
    int drawH = (int)(bmpH * scale);

    // Center in the QR area
    int drawX = m_qrRect.left + (areaW - drawW) / 2;
    int drawY = m_qrRect.top + (areaH - drawH) / 2;

    // Draw bitmap using SetDIBitsToDevice for pixel-perfect rendering
    HDC memDC = CreateCompatibleDC(hdc);
    HBITMAP hOldBmp = (HBITMAP)SelectObject(memDC, m_hQrBitmap);

    // Use StretchBlt with HALFTONE for best quality scaling
    int oldStretchMode = SetStretchBltMode(hdc, HALFTONE);
    StretchBlt(hdc, drawX, drawY, drawW, drawH, memDC, 0, 0, bmpW, bmpH, SRCCOPY);
    SetStretchBltMode(hdc, oldStretchMode);

    SelectObject(memDC, hOldBmp);
    DeleteDC(memDC);
}

void MainWindow::BuildUI()
{
    // Create font
    m_hFont = CreateFontW(
        -MulDiv(10, 96, 72),
        0, 0, 0, FW_NORMAL,
        FALSE, FALSE, FALSE,
        DEFAULT_CHARSET,
        OUT_DEFAULT_PRECIS,
        CLIP_DEFAULT_PRECIS,
        CLEARTYPE_QUALITY,
        DEFAULT_PITCH | FF_DONTCARE,
        L"Microsoft YaHei"
    );

    // Toolbar controls
    // Compress status label (hidden by default, shown when compressed)
    m_hLblCompress = CreateWindowExW(
        0, L"STATIC", L"",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        5, 7, 80, 18,
        m_hWnd, reinterpret_cast<HMENU>(IDC_CHK_COMPRESS),
        m_hInstance, nullptr
    );
    SendMessageW(m_hLblCompress, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFont), TRUE);

    m_hBtnCapture = CreateWindowExW(
        0, L"BUTTON", L"\u622A\u56FE",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        90, 3, 40, 24,
        m_hWnd, reinterpret_cast<HMENU>(IDC_BTN_CAPTURE),
        m_hInstance, nullptr
    );
    SendMessageW(m_hBtnCapture, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFont), TRUE);

    m_hBtnUpload = CreateWindowExW(
        0, L"BUTTON", L"\u4E0A\u4F20",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        135, 3, 40, 24,
        m_hWnd, reinterpret_cast<HMENU>(IDC_BTN_UPLOAD),
        m_hInstance, nullptr
    );
    SendMessageW(m_hBtnUpload, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFont), TRUE);

    // Text input/output (no STATIC control for QR - we paint it ourselves)
    m_hTxtContent = CreateWindowExW(
        0, L"EDIT", L"",
        WS_CHILD | WS_VISIBLE | WS_BORDER |
        ES_MULTILINE | ES_AUTOVSCROLL | WS_VSCROLL,
        PADDING, 440, 420, 100,
        m_hWnd, reinterpret_cast<HMENU>(IDC_TXT_CONTENT),
        m_hInstance, nullptr
    );
    SendMessageW(m_hTxtContent, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFont), TRUE);

    // Subclass edit control to handle Ctrl+A (select all)
    SetWindowSubclass(m_hTxtContent, EditSubclassProc, 0, 0);

    // Initialize QR display rect
    m_qrRect = { PADDING, TOOLBAR_HEIGHT + 4, PADDING + 420, TOOLBAR_HEIGHT + 4 + 400 };
}

void MainWindow::ResizeControls()
{
    RECT rc;
    GetClientRect(m_hWnd, &rc);
    int width = rc.right;
    int height = rc.bottom;

    int imgX = PADDING;
    int imgY = TOOLBAR_HEIGHT + 4;
    int imgW = width - 2 * PADDING;
    int imgH = imgW;  // Keep square

    int txtY = imgY + imgH + PADDING;
    int txtH = height - txtY - PADDING;
    if (txtH < 60) {
        txtH = 60;
        imgH = height - TOOLBAR_HEIGHT - 4 - PADDING - txtH - PADDING;
        if (imgH < 100) imgH = 100;
        txtY = imgY + imgH + PADDING;
    }

    m_qrRect = { imgX, imgY, imgX + imgW, imgY + imgH };
    MoveWindow(m_hTxtContent, PADDING, txtY, width - 2 * PADDING, txtH, TRUE);
    InvalidateRect(m_hWnd, nullptr, TRUE);
}

void MainWindow::GenerateQr()
{
    try {
        std::string text = GetText();
        if (text.empty()) {
            UpdateQrImage(nullptr);
            SetWindowTextW(m_hLblCompress, L"");
            m_lastCompressed = false;
            return;
        }

        bool compressed = false;
        HBITMAP hNewBmp = qr::generateQrBitmap(text, m_compress, 600, compressed);
        if (hNewBmp) {
            logToFile("GenerateQr: bitmap created OK, compressed=" + std::to_string(compressed));
        } else {
            logToFile("GenerateQr: bitmap creation FAILED");
        }
        UpdateQrImage(hNewBmp);

        // Update compress status label
        if (compressed != m_lastCompressed) {
            m_lastCompressed = compressed;
            SetWindowTextW(m_hLblCompress, compressed ? L"\u5DF2\u538B\u7F29" : L"");
        }
    } catch (const std::exception& e) {
        logToFile(std::string("GenerateQr exception: ") + e.what());
        UpdateQrImage(nullptr);
    } catch (...) {
        logToFile("GenerateQr unknown exception");
        UpdateQrImage(nullptr);
    }
}

void MainWindow::OnCapture()
{
    logToFile("=== OnCapture START ===");
    try {
        HBITMAP hCaptured = qr::captureScreenSelection(m_hWnd);
        if (!hCaptured) {
            logToFile("Capture returned NULL");
            return;
        }

        BITMAP bm;
        GetObject(hCaptured, sizeof(bm), &bm);
        {
            std::ostringstream oss;
            oss << "Captured: " << bm.bmWidth << "x" << bm.bmHeight << ", " << bm.bmBitsPixel << "bpp";
            logToFile(oss.str());
        }

        DecodeResult result = qr::decodeFromBitmap(hCaptured);
        DeleteObject(hCaptured);

        {
            std::ostringstream oss;
            oss << "Decode result: success=" << result.success << ", text_len=" << result.text.size() << ", strategy=" << result.strategy;
            logToFile(oss.str());
        }

        if (!result.success) {
            logToFile("Decode failed: all strategies failed");
            return;
        }

        if (result.text.empty()) {
            logToFile("Decode OK but text is empty");
            return;
        }

        // Log raw decoded text (first 500 chars)
        {
            std::string preview = result.text.substr(0, 500);
            logToFile("Raw decoded (first 500): " + preview);
        }

        std::string decompressed;
        try {
            decompressed = qr::decompressText(result.text);
            {
                std::ostringstream oss;
                oss << "Decompress OK, len=" << decompressed.size();
                logToFile(oss.str());
            }
            // Log decompressed text (first 500 chars)
            if (!decompressed.empty()) {
                logToFile("Decompressed (first 500): " + decompressed.substr(0, 500));
            }
        } catch (const std::exception& e) {
            logToFile(std::string("Decompress exception: ") + e.what());
            decompressed = result.text; // fallback: show raw text
        } catch (...) {
            logToFile("Decompress unknown exception");
            decompressed = result.text; // fallback: show raw text
        }

        if (!decompressed.empty()) {
            SetText(decompressed);
            logToFile("SetText done, len=" + std::to_string(decompressed.size()));
        }
    } catch (const std::exception& e) {
        logToFile(std::string("OnCapture exception: ") + e.what());
    } catch (...) {
        logToFile("OnCapture unknown exception");
    }
    logToFile("=== OnCapture END ===");
}

void MainWindow::OnUpload()
{
    try {
        wchar_t filePath[MAX_PATH] = {};

        OPENFILENAMEW ofn = {};
        ofn.lStructSize = sizeof(ofn);
        ofn.hwndOwner = m_hWnd;
        ofn.lpstrFile = filePath;
        ofn.nMaxFile = MAX_PATH;
        ofn.lpstrFilter = L"Image Files\0*.png;*.jpg;*.jpeg;*.bmp\0All Files\0*.*\0";
        ofn.nFilterIndex = 1;
        ofn.Flags = OFN_FILEMUSTEXIST | OFN_PATHMUSTEXIST;

        if (GetOpenFileNameW(&ofn)) {
            DecodeResult result = qr::decodeFromFile(filePath);
            if (result.success && !result.text.empty()) {
                std::string decompressed = qr::decompressText(result.text);
                SetText(decompressed);
            }
        }
    } catch (...) {
        // Suppress all exceptions
    }
}

void MainWindow::OnTextChanged()
{
    KillTimer(m_hWnd, IDT_TEXT_CHANGED);
    SetTimer(m_hWnd, IDT_TEXT_CHANGED, DEBOUNCE_MS, nullptr);
}

void MainWindow::UpdateQrImage(HBITMAP hBmp)
{
    if (m_hQrBitmap) {
        DeleteObject(m_hQrBitmap);
        m_hQrBitmap = nullptr;
    }

    m_hQrBitmap = hBmp;
    InvalidateRect(m_hWnd, &m_qrRect, TRUE);
}

void MainWindow::SetText(const std::string& text)
{
    std::wstring wtext = Utf8ToWide(text);
    SetWindowTextW(m_hTxtContent, wtext.c_str());
    // EN_CHANGE from SetWindowTextW may not fire reliably when called programmatically
    // So directly generate QR code after setting text
    GenerateQr();
}

std::string MainWindow::GetText() const
{
    int len = GetWindowTextLengthW(m_hTxtContent);
    if (len <= 0) return "";

    std::wstring wtext(len, L'\0');
    GetWindowTextW(m_hTxtContent, &wtext[0], len + 1);
    return WideToUtf8(wtext);
}

std::wstring MainWindow::Utf8ToWide(const std::string& str) const
{
    if (str.empty()) return L"";

    int len = MultiByteToWideChar(CP_UTF8, 0, str.c_str(), -1, nullptr, 0);
    if (len <= 0) return L"";

    std::wstring wstr(len - 1, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, str.c_str(), -1, &wstr[0], len);
    return wstr;
}

std::string MainWindow::WideToUtf8(const std::wstring& wstr) const
{
    if (wstr.empty()) return "";

    int len = WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), -1, nullptr, 0, nullptr, nullptr);
    if (len <= 0) return "";

    std::string str(len - 1, '\0');
    WideCharToMultiByte(CP_UTF8, 0, wstr.c_str(), -1, &str[0], len, nullptr, nullptr);
    return str;
}

} // namespace qr
