#include "MainWindow.h"
#include "Resource.h"
#include "QrGenerator.h"
#include "QrDecoder.h"
#include "Compressor.h"
#include "ScreenCapture.h"
#include <commdlg.h>
#include <wingdi.h>
#include <commctrl.h>
#include <windowsx.h>
#include <algorithm>
#include <vector>
#include <fstream>
#include <ctime>
#include <sstream>

// Control IDs
#define IDC_CHK_COMPRESS  1001
#define IDC_BTN_CAPTURE   1002
#define IDC_BTN_UPLOAD    1003
#define IDC_TXT_CONTENT   1005
#define IDC_CMB_ECL       1006
#define IDC_BTN_PREV      1007
#define IDC_BTN_NEXT      1008
#define IDC_LBL_PAGE      1009
#define IDC_BTN_SETTINGS  1010
#define IDC_BTN_FLOAT_CAPTURE 1011
#define IDT_TEXT_CHANGED   2001

namespace qr {

static const wchar_t CLASS_NAME[] = L"QRCodeToolClass";
const wchar_t MainWindow::FLOAT_CLASS_NAME[] = L"QRFloatProgressClass";
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
    , m_hCmbEcl(nullptr)
    , m_hBtnPrev(nullptr)
    , m_hBtnNext(nullptr)
    , m_hLblPage(nullptr)
    , m_hBtnSettings(nullptr)
    , m_hQrBitmap(nullptr)
    , m_hFont(nullptr)
    , m_hFontBold(nullptr)
    , m_hCompressBrush(nullptr)
    , m_compress(true)
    , m_lastCompressed(false)
    , m_eclLevel(0)
    , m_qrRect({})
    , m_currentPage(0)
    , m_hFloatWnd(nullptr)
    , m_hotkeyConfig{MOD_ALT | MOD_CONTROL, 'S', true}
    , m_hotkeyRegistered(false)
{
    LoadHotkeyConfig();
}

MainWindow::~MainWindow()
{
    UnregisterGlobalHotkey();
    if (m_hTxtContent) {
        RemoveWindowSubclass(m_hTxtContent, EditSubclassProc, 0);
    }
    if (m_hQrBitmap) {
        DeleteObject(m_hQrBitmap);
        m_hQrBitmap = nullptr;
    }
    // Clean up all QR page bitmaps
    for (auto& page : m_qrPages) {
        if (page.bitmap) {
            DeleteObject(page.bitmap);
        }
    }
    m_qrPages.clear();
    if (m_hFont) {
        DeleteObject(m_hFont);
        m_hFont = nullptr;
    }
    if (m_hFontBold) {
        DeleteObject(m_hFontBold);
        m_hFontBold = nullptr;
    }
    if (m_hCompressBrush) {
        DeleteObject(m_hCompressBrush);
        m_hCompressBrush = nullptr;
    }
}

bool MainWindow::Create()
{
    HICON hIconLarge = LoadIconW(m_hInstance, MAKEINTRESOURCEW(IDR_ICON1));
    HICON hIconSmall = (HICON)LoadImageW(m_hInstance, MAKEINTRESOURCEW(IDR_ICON1),
        IMAGE_ICON, 16, 16, LR_DEFAULTCOLOR);

    WNDCLASSEXW wc = {};
    wc.cbSize = sizeof(wc);
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = WndProc;
    wc.hInstance = m_hInstance;
    wc.hIcon = hIconLarge;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    wc.lpszClassName = CLASS_NAME;
    wc.hIconSm = hIconSmall;

    if (!RegisterClassExW(&wc)) {
        DWORD err = GetLastError();
        if (err != ERROR_CLASS_ALREADY_EXISTS) {
            return false;
        }
    }

    // Register floating progress window class
    static bool floatRegistered = false;
    if (!floatRegistered) {
        WNDCLASSEXW fwc = {};
        fwc.cbSize = sizeof(fwc);
        fwc.style = CS_HREDRAW | CS_VREDRAW;
        fwc.lpfnWndProc = FloatWndProc;
        fwc.hInstance = m_hInstance;
        fwc.hCursor = LoadCursor(nullptr, IDC_ARROW);
        fwc.hbrBackground = nullptr;
        fwc.lpszClassName = FLOAT_CLASS_NAME;
        RegisterClassExW(&fwc);
        floatRegistered = true;
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

    SendMessage(m_hWnd, WM_SETICON, ICON_BIG, reinterpret_cast<LPARAM>(hIconLarge));
    SendMessage(m_hWnd, WM_SETICON, ICON_SMALL, reinterpret_cast<LPARAM>(hIconSmall));

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
        RegisterGlobalHotkey();
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
        case IDC_CMB_ECL:
            if (wmEvent == CBN_SELCHANGE) {
                OnEclChanged();
            }
            break;
        case IDC_BTN_PREV:
            OnPrevPage();
            break;
        case IDC_BTN_NEXT:
            OnNextPage();
            break;
        case IDC_BTN_SETTINGS:
            ShowSettingsDialog();
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

    case WM_CTLCOLORSTATIC: {
        HDC hdcStatic = reinterpret_cast<HDC>(wParam);
        HWND hCtrl = reinterpret_cast<HWND>(lParam);
        if (hCtrl == m_hLblCompress) {
            SetTextColor(hdcStatic, RGB(0, 160, 0));
            SetBkColor(hdcStatic, RGB(240, 240, 240));
            if (!m_hCompressBrush) {
                m_hCompressBrush = CreateSolidBrush(RGB(240, 240, 240));
            }
            return reinterpret_cast<LRESULT>(m_hCompressBrush);
        }
        if (hCtrl == m_hLblPage) {
            SetTextColor(hdcStatic, RGB(80, 80, 80));
            SetBkColor(hdcStatic, RGB(240, 240, 240));
            if (!m_hCompressBrush) {
                m_hCompressBrush = CreateSolidBrush(RGB(240, 240, 240));
            }
            return reinterpret_cast<LRESULT>(m_hCompressBrush);
        }
        return DefWindowProcW(m_hWnd, message, wParam, lParam);
    }

    case WM_ERASEBKGND:
        return 1;

    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(m_hWnd, &ps);

        RECT rc;
        GetClientRect(m_hWnd, &rc);

        HBRUSH hBgBrush = CreateSolidBrush(RGB(240, 240, 240));
        FillRect(hdc, &rc, hBgBrush);
        DeleteObject(hBgBrush);

        PaintQrCode(hdc, rc);

        EndPaint(m_hWnd, &ps);
        return 0;
    }

    case WM_HOTKEY:
        if (wParam == HOTKEY_ID) {
            OnGlobalHotkey();
        }
        return 0;

    case WM_DESTROY:
        UnregisterGlobalHotkey();
        KillTimer(m_hWnd, IDT_TEXT_CHANGED);
        PostQuitMessage(0);
        return 0;

    default:
        return DefWindowProcW(m_hWnd, message, wParam, lParam);
    }
}

void MainWindow::PaintQrCode(HDC hdc, const RECT& clientRect)
{
    HBRUSH hWhiteBrush = CreateSolidBrush(RGB(255, 255, 255));
    FillRect(hdc, &m_qrRect, hWhiteBrush);
    DeleteObject(hWhiteBrush);

    HPEN hBorderPen = CreatePen(PS_SOLID, 1, RGB(180, 180, 180));
    HPEN hOldPen = (HPEN)SelectObject(hdc, hBorderPen);
    HBRUSH hOldBrush = (HBRUSH)SelectObject(hdc, GetStockObject(NULL_BRUSH));
    Rectangle(hdc, m_qrRect.left, m_qrRect.top, m_qrRect.right, m_qrRect.bottom);
    SelectObject(hdc, hOldBrush);
    SelectObject(hdc, hOldPen);
    DeleteObject(hBorderPen);

    if (!m_hQrBitmap) return;

    BITMAP bm;
    GetObject(m_hQrBitmap, sizeof(bm), &bm);

    int bmpW = bm.bmWidth;
    int bmpH = bm.bmHeight;
    int areaW = m_qrRect.right - m_qrRect.left;
    int areaH = m_qrRect.bottom - m_qrRect.top;

    float scale = (float)(std::min)(areaW, areaH) / (float)(std::max)(bmpW, bmpH);
    int drawW = (int)(bmpW * scale);
    int drawH = (int)(bmpH * scale);

    int drawX = m_qrRect.left + (areaW - drawW) / 2;
    int drawY = m_qrRect.top + (areaH - drawH) / 2;

    HDC memDC = CreateCompatibleDC(hdc);
    HBITMAP hOldBmp = (HBITMAP)SelectObject(memDC, m_hQrBitmap);

    int oldStretchMode = SetStretchBltMode(hdc, HALFTONE);
    StretchBlt(hdc, drawX, drawY, drawW, drawH, memDC, 0, 0, bmpW, bmpH, SRCCOPY);
    SetStretchBltMode(hdc, oldStretchMode);

    SelectObject(memDC, hOldBmp);
    DeleteDC(memDC);
}

void MainWindow::BuildUI()
{
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

    m_hFontBold = CreateFontW(
        -MulDiv(10, 96, 72),
        0, 0, 0, FW_BOLD,
        FALSE, FALSE, FALSE,
        DEFAULT_CHARSET,
        OUT_DEFAULT_PRECIS,
        CLIP_DEFAULT_PRECIS,
        CLEARTYPE_QUALITY,
        DEFAULT_PITCH | FF_DONTCARE,
        L"Microsoft YaHei"
    );

    // Toolbar: [已压缩] [截图] [上传] [◀] [1/3] [▶]  ...  [纠错率: ▼]

    m_hLblCompress = CreateWindowExW(
        0, L"STATIC", L"",
        WS_CHILD | WS_VISIBLE | SS_LEFT,
        5, 7, 50, 18,
        m_hWnd, reinterpret_cast<HMENU>(IDC_CHK_COMPRESS),
        m_hInstance, nullptr
    );
    SendMessageW(m_hLblCompress, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFontBold), TRUE);

    m_hBtnCapture = CreateWindowExW(
        0, L"BUTTON", L"\u622A\u56FE",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        58, 3, 40, 24,
        m_hWnd, reinterpret_cast<HMENU>(IDC_BTN_CAPTURE),
        m_hInstance, nullptr
    );
    SendMessageW(m_hBtnCapture, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFont), TRUE);

    m_hBtnUpload = CreateWindowExW(
        0, L"BUTTON", L"\u4E0A\u4F20",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        102, 3, 40, 24,
        m_hWnd, reinterpret_cast<HMENU>(IDC_BTN_UPLOAD),
        m_hInstance, nullptr
    );
    SendMessageW(m_hBtnUpload, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFont), TRUE);

    // Page navigation: [◀] [1/3] [▶]
    m_hBtnPrev = CreateWindowExW(
        0, L"BUTTON", L"\u25C0",
        WS_CHILD | BS_PUSHBUTTON,
        150, 3, 24, 24,
        m_hWnd, reinterpret_cast<HMENU>(IDC_BTN_PREV),
        m_hInstance, nullptr
    );
    SendMessageW(m_hBtnPrev, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFont), TRUE);

    m_hLblPage = CreateWindowExW(
        0, L"STATIC", L"",
        WS_CHILD | SS_CENTER,
        176, 7, 50, 18,
        m_hWnd, reinterpret_cast<HMENU>(IDC_LBL_PAGE),
        m_hInstance, nullptr
    );
    SendMessageW(m_hLblPage, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFont), TRUE);

    m_hBtnNext = CreateWindowExW(
        0, L"BUTTON", L"\u25B6",
        WS_CHILD | BS_PUSHBUTTON,
        228, 3, 24, 24,
        m_hWnd, reinterpret_cast<HMENU>(IDC_BTN_NEXT),
        m_hInstance, nullptr
    );
    SendMessageW(m_hBtnNext, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFont), TRUE);

    // Error correction level dropdown
    m_hCmbEcl = CreateWindowExW(
        0, L"COMBOBOX", L"",
        WS_CHILD | WS_VISIBLE | CBS_DROPDOWNLIST | WS_VSCROLL,
        380, 2, 55, 200,
        m_hWnd, reinterpret_cast<HMENU>(IDC_CMB_ECL),
        m_hInstance, nullptr
    );
    SendMessageW(m_hCmbEcl, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFont), TRUE);
    SendMessageW(m_hCmbEcl, CB_ADDSTRING, 0, reinterpret_cast<LPARAM>(L"L"));
    SendMessageW(m_hCmbEcl, CB_ADDSTRING, 0, reinterpret_cast<LPARAM>(L"M"));
    SendMessageW(m_hCmbEcl, CB_ADDSTRING, 0, reinterpret_cast<LPARAM>(L"Q"));
    SendMessageW(m_hCmbEcl, CB_ADDSTRING, 0, reinterpret_cast<LPARAM>(L"H"));
    SendMessageW(m_hCmbEcl, CB_SETCURSEL, 0, 0);

    // Settings button (gear icon) - positioned after ECL dropdown
    m_hBtnSettings = CreateWindowExW(
        0, L"BUTTON", L"\u2699",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        438, 2, 28, 24,
        m_hWnd, reinterpret_cast<HMENU>(IDC_BTN_SETTINGS),
        m_hInstance, nullptr
    );
    SendMessageW(m_hBtnSettings, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFont), TRUE);

    // Tooltip for settings button
    HWND hTT = CreateWindowExW(0, TOOLTIPS_CLASSW, NULL,
        WS_POPUP | TTS_ALWAYSTIP,
        CW_USEDEFAULT, CW_USEDEFAULT, CW_USEDEFAULT, CW_USEDEFAULT,
        m_hWnd, NULL, m_hInstance, NULL);
    if (hTT) {
        TOOLINFOW ti = { sizeof(ti) };
        ti.uFlags = TTF_IDISHWND | TTF_SUBCLASS;
        ti.hwnd = m_hWnd;
        ti.uId = reinterpret_cast<UINT_PTR>(m_hBtnSettings);
        ti.lpszText = const_cast<LPWSTR>(L"\u8BBE\u7F6E\u5FEB\u6377\u952E");
        SendMessageW(hTT, TTM_ADDTOOLW, 0, reinterpret_cast<LPARAM>(&ti));
    }

    m_hTxtContent = CreateWindowExW(
        0, L"EDIT", L"",
        WS_CHILD | WS_VISIBLE | WS_BORDER |
        ES_MULTILINE | ES_AUTOVSCROLL | WS_VSCROLL,
        PADDING, 440, 420, 100,
        m_hWnd, reinterpret_cast<HMENU>(IDC_TXT_CONTENT),
        m_hInstance, nullptr
    );
    SendMessageW(m_hTxtContent, WM_SETFONT, reinterpret_cast<WPARAM>(m_hFont), TRUE);
    SendMessageW(m_hTxtContent, EM_SETLIMITTEXT, 0, 0);  // 0 = no limit

    SetWindowSubclass(m_hTxtContent, EditSubclassProc, 0, 0);

    m_qrRect = { PADDING, TOOLBAR_HEIGHT + 4, PADDING + 420, TOOLBAR_HEIGHT + 4 + 400 };

    // Initially hide page navigation
    UpdatePageInfo();
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
    int imgH = imgW;

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
    MoveWindow(m_hCmbEcl, width - 93, 2, 55, 200, TRUE);
    MoveWindow(m_hBtnSettings, width - 32, 2, 28, 24, TRUE);
    InvalidateRect(m_hWnd, nullptr, TRUE);
}

void MainWindow::GenerateQr()
{
    try {
        std::string text = GetText();
        if (text.empty()) {
            // Clean up old pages
            for (auto& page : m_qrPages) {
                if (page.bitmap) DeleteObject(page.bitmap);
            }
            m_qrPages.clear();
            m_currentPage = 0;
            UpdateQrImage(nullptr);
            SetWindowTextW(m_hLblCompress, L"");
            m_lastCompressed = false;
            UpdatePageInfo();
            return;
        }

        QrEcl ecl = static_cast<QrEcl>(m_eclLevel);
        bool compressed = false;
        std::vector<QrPage> pages = qr::generateQrPages(text, m_compress, 600, compressed, ecl);

        // Clean up old pages
        for (auto& page : m_qrPages) {
            if (page.bitmap) DeleteObject(page.bitmap);
        }
        m_qrPages = std::move(pages);
        m_currentPage = 0;

        // Show first page
        if (!m_qrPages.empty() && m_qrPages[0].bitmap) {
            UpdateQrImage(m_qrPages[0].bitmap);
        } else {
            UpdateQrImage(nullptr);
        }

        if (compressed != m_lastCompressed) {
            m_lastCompressed = compressed;
            SetWindowTextW(m_hLblCompress, compressed ? L"\u5DF2\u538B\u7F29" : L"");
        }

        UpdatePageInfo();
    } catch (const std::exception& e) {
        UpdateQrImage(nullptr);
    } catch (...) {
        UpdateQrImage(nullptr);
    }
}

void MainWindow::OnPrevPage()
{
    if (m_currentPage > 0) {
        m_currentPage--;
        if (m_currentPage < (int)m_qrPages.size() && m_qrPages[m_currentPage].bitmap) {
            UpdateQrImage(m_qrPages[m_currentPage].bitmap);
        }
        UpdatePageInfo();
    }
}

void MainWindow::OnNextPage()
{
    if (m_currentPage < (int)m_qrPages.size() - 1) {
        m_currentPage++;
        if (m_currentPage < (int)m_qrPages.size() && m_qrPages[m_currentPage].bitmap) {
            UpdateQrImage(m_qrPages[m_currentPage].bitmap);
        }
        UpdatePageInfo();
    }
}

void MainWindow::UpdatePageInfo()
{
    int totalPages = (int)m_qrPages.size();
    if (totalPages <= 1) {
        // Single page or no pages - hide navigation
        ShowWindow(m_hBtnPrev, SW_HIDE);
        ShowWindow(m_hBtnNext, SW_HIDE);
        ShowWindow(m_hLblPage, SW_HIDE);
    } else {
        // Multi-page - show navigation
        ShowWindow(m_hBtnPrev, SW_SHOW);
        ShowWindow(m_hBtnNext, SW_SHOW);
        ShowWindow(m_hLblPage, SW_SHOW);

        // Update page label
        std::wstring pageText = std::to_wstring(m_currentPage + 1) + L"/" + std::to_wstring(totalPages);
        SetWindowTextW(m_hLblPage, pageText.c_str());

        // Enable/disable buttons
        EnableWindow(m_hBtnPrev, m_currentPage > 0);
        EnableWindow(m_hBtnNext, m_currentPage < totalPages - 1);
    }
}

void MainWindow::OnCapture()
{
    // Clear text box if it has content (new scan session)
    if (GetWindowTextLengthW(m_hTxtContent) > 0) {
        // Use SetWindowTextW directly to avoid SetText() calling GenerateQr()
        // which can cause re-entrancy issues during screen capture
        SetWindowTextW(m_hTxtContent, L"");
        KillTimer(m_hWnd, IDT_TEXT_CHANGED);  // cancel any pending text-change timer

        // Clear QR display manually
        for (auto& p : m_qrPages) { if (p.bitmap) DeleteObject(p.bitmap); }
        m_qrPages.clear();
        m_currentPage = 0;
        m_hQrBitmap = nullptr;
        InvalidateRect(m_hWnd, &m_qrRect, TRUE);
        SetWindowTextW(m_hLblCompress, L"");
        m_lastCompressed = false;
        UpdatePageInfo();

        // Reset multi-page assembler for new scan session
        m_assembler.reset();
        CloseFloatProgress();
    }

    try {
        HBITMAP hCaptured = qr::captureScreenSelection(m_hWnd);
        if (!hCaptured) {
            return;
        }

        DecodeResult result = qr::decodeFromBitmap(hCaptured);
        DeleteObject(hCaptured);

        if (!result.success || result.text.empty()) {
            return;
        }

        // Check if this is a multi-page QR code
        if (result.text.compare(0, 3, "M5:") == 0) {
            int page = 0, total = 0;
            std::string chunk;
            if (MultiPageAssembler::parseM5Header(result.text, page, total, chunk)) {
                // Clear QR display when starting a new multi-page scan
                if (m_assembler.pages.empty()) {
                    for (auto& p : m_qrPages) { if (p.bitmap) DeleteObject(p.bitmap); }
                    m_qrPages.clear();
                    m_currentPage = 0;
                    UpdateQrImage(nullptr);
                    UpdatePageInfo();
                }

                bool isNew = m_assembler.addPage(result.text);
                if (!isNew) {
                    // Duplicate page - show orange warning in float
                    auto missing = m_assembler.getMissingPages();
                    std::wstring msg = L"\u5DF2\u6536\u96C6\u7B2C ";
                    std::vector<int> collected;
                    for (auto& kv : m_assembler.pages) {
                        collected.push_back(kv.first + 1);
                    }
                    std::sort(collected.begin(), collected.end());
                    for (size_t i = 0; i < collected.size(); i++) {
                        if (i > 0) msg += L"\u3001";
                        msg += std::to_wstring(collected[i]);
                    }
                    msg += L" \u9875\uFF0C\u8FD8\u7F3A\u7B2C ";
                    for (size_t i = 0; i < missing.size(); i++) {
                        if (i > 0) msg += L"\u3001";
                        msg += std::to_wstring(missing[i]);
                    }
                    msg += L" \u9875";
                    // Append orange text: "第X页已收集"
                    msg += L"|\u7B2C" + std::to_wstring(page) + L"\u9875\u5DF2\u6536\u96C6";
                    ShowFloatProgress(msg);
                    return;
                }

                if (m_assembler.isComplete()) {
                    CloseFloatProgress();
                    std::string assembled = m_assembler.assemble();
                    if (!assembled.empty()) {
                        SetText(assembled);
                    }
                    m_assembler.reset();
                } else {
                    // Show floating progress: collected pages and missing pages
                    auto missing = m_assembler.getMissingPages();
                    std::wstring msg = L"\u5DF2\u6536\u96C6\u7B2C ";
                    // List collected pages (pages map uses 0-based keys, display as 1-based)
                    std::vector<int> collected;
                    for (auto& kv : m_assembler.pages) {
                        collected.push_back(kv.first + 1);  // convert 0-based to 1-based
                    }
                    std::sort(collected.begin(), collected.end());
                    for (size_t i = 0; i < collected.size(); i++) {
                        if (i > 0) msg += L"\u3001";
                        msg += std::to_wstring(collected[i]);
                    }
                    msg += L" \u9875\uFF0C\u8FD8\u7F3A\u7B2C ";
                    for (size_t i = 0; i < missing.size(); i++) {
                        if (i > 0) msg += L"\u3001";
                        msg += std::to_wstring(missing[i]);
                    }
                    msg += L" \u9875";
                    ShowFloatProgress(msg);
                }
                return;
            }
        }

        // Single page QR (B5:, GZ:, or plain text)
        m_assembler.reset();  // reset any previous multi-page state

        // Clear QR display before setting new
        for (auto& p : m_qrPages) { if (p.bitmap) DeleteObject(p.bitmap); }
        m_qrPages.clear();
        m_currentPage = 0;

        std::string decompressed;
        try {
            decompressed = qr::decompressText(result.text);
        } catch (...) {
            decompressed = result.text;
        }

        if (!decompressed.empty()) {
            SetText(decompressed);
        }
    } catch (...) {
    }
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
                // Check multi-page
                if (result.text.compare(0, 3, "M5:") == 0) {
                    int page = 0, total = 0;
                    std::string chunk;
                    if (MultiPageAssembler::parseM5Header(result.text, page, total, chunk)) {
                        // Clear old content when starting a new multi-page scan
                        if (m_assembler.pages.empty()) {
                            SetText("");
                            for (auto& p : m_qrPages) { if (p.bitmap) DeleteObject(p.bitmap); }
                            m_qrPages.clear();
                            m_currentPage = 0;
                            UpdateQrImage(nullptr);
                            UpdatePageInfo();
                        }

                        m_assembler.addPage(result.text);
                        if (m_assembler.isComplete()) {
                            CloseFloatProgress();
                            std::string assembled = m_assembler.assemble();
                            if (!assembled.empty()) {
                                SetText(assembled);
                            }
                            m_assembler.reset();
                        } else {
                            auto missing = m_assembler.getMissingPages();
                            std::wstring msg = L"\u5DF2\u6536\u96C6\u7B2C ";
                            std::vector<int> collected;
                            for (auto& kv : m_assembler.pages) {
                                collected.push_back(kv.first + 1);  // convert 0-based to 1-based
                            }
                            std::sort(collected.begin(), collected.end());
                            for (size_t i = 0; i < collected.size(); i++) {
                                if (i > 0) msg += L"\u3001";
                                msg += std::to_wstring(collected[i]);
                            }
                            msg += L" \u9875\uFF0C\u8FD8\u7F3A\u7B2C ";
                            for (size_t i = 0; i < missing.size(); i++) {
                                if (i > 0) msg += L"\u3001";
                                msg += std::to_wstring(missing[i]);
                            }
                            msg += L" \u9875";
                            ShowFloatProgress(msg);
                        }
                        return;
                    }
                }

                m_assembler.reset();
                std::string decompressed = qr::decompressText(result.text);
                // Clear old content before setting new
                for (auto& p : m_qrPages) { if (p.bitmap) DeleteObject(p.bitmap); }
                m_qrPages.clear();
                m_currentPage = 0;
                SetText(decompressed);
            }
        }
    } catch (...) {
    }
}

void MainWindow::OnTextChanged()
{
    KillTimer(m_hWnd, IDT_TEXT_CHANGED);
    SetTimer(m_hWnd, IDT_TEXT_CHANGED, DEBOUNCE_MS, nullptr);
}

void MainWindow::OnEclChanged()
{
    int sel = static_cast<int>(SendMessageW(m_hCmbEcl, CB_GETCURSEL, 0, 0));
    if (sel != CB_ERR && sel != m_eclLevel) {
        m_eclLevel = sel;
        GenerateQr();
    }
}

void MainWindow::UpdateQrImage(HBITMAP hBmp)
{
    // Don't delete old bitmap - it's owned by m_qrPages now
    m_hQrBitmap = hBmp;
    InvalidateRect(m_hWnd, &m_qrRect, TRUE);
}

void MainWindow::SetText(const std::string& text)
{
    std::wstring wtext = Utf8ToWide(text);
    SetWindowTextW(m_hTxtContent, wtext.c_str());
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

// ============================================================
// Floating progress window
// ============================================================

LRESULT CALLBACK MainWindow::FloatWndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam)
{
    switch (message) {
    case WM_CREATE: {
        // Create "截图" button in the float window - centered at bottom
        CREATESTRUCTW* cs = reinterpret_cast<CREATESTRUCTW*>(lParam);
        HWND hBtnCapture = CreateWindowExW(
            0, L"BUTTON", L"\u26F6 \u622A\u56FE",
            WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
            0, 0, 70, 24,
            hWnd, reinterpret_cast<HMENU>(IDC_BTN_FLOAT_CAPTURE),
            cs->hInstance, nullptr
        );
        HFONT hFont = CreateFontW(-13, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
            DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
            CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Microsoft YaHei");
        SendMessageW(hBtnCapture, WM_SETFONT, reinterpret_cast<WPARAM>(hFont), TRUE);
        // Store font to clean up later
        SetPropW(hWnd, L"BtnFont", hFont);
        return 0;
    }

    case WM_COMMAND:
        if (LOWORD(wParam) == IDC_BTN_FLOAT_CAPTURE) {
            // Get MainWindow from parent
            MainWindow* mainWnd = reinterpret_cast<MainWindow*>(GetWindowLongPtrW(GetParent(hWnd), GWLP_USERDATA));
            if (mainWnd) {
                mainWnd->OnCaptureFromFloat();
            }
            return 0;
        }
        break;

    case WM_SIZE: {
        // Position the capture button centered at bottom
        RECT rc;
        GetClientRect(hWnd, &rc);
        HWND hBtn = GetDlgItem(hWnd, IDC_BTN_FLOAT_CAPTURE);
        if (hBtn) {
            int btnW = 70, btnH = 24;
            int btnX = (rc.right - btnW) / 2;  // centered horizontally
            int btnY = rc.bottom - btnH - 4;    // 4px from bottom
            MoveWindow(hBtn, btnX, btnY, btnW, btnH, TRUE);
        }
        return 0;
    }

    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hWnd, &ps);

        RECT rc;
        GetClientRect(hWnd, &rc);

        // Semi-transparent dark background
        HBRUSH bgBrush = CreateSolidBrush(RGB(40, 40, 40));
        FillRect(hdc, &rc, bgBrush);
        DeleteObject(bgBrush);

        // Get the stored text (format: "main text|orange text" or just "main text")
        wchar_t* text = (wchar_t*)GetWindowLongPtrW(hWnd, GWLP_USERDATA);
        if (text) {
            SetBkMode(hdc, TRANSPARENT);
            HFONT hFont = CreateFontW(-14, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Microsoft YaHei");
            HFONT hOldFont = (HFONT)SelectObject(hdc, hFont);

            // Split text by '|' separator
            std::wstring fullText(text);
            std::wstring mainText, orangeText;
            size_t sepPos = fullText.find(L"|");
            if (sepPos != std::wstring::npos) {
                mainText = fullText.substr(0, sepPos);
                orangeText = fullText.substr(sepPos + 1);
            } else {
                mainText = fullText;
            }

            // Leave space at bottom for the capture button
            RECT textRc = rc;
            textRc.left += 16;
            textRc.right -= 16;
            textRc.top += 10;
            textRc.bottom -= 30;  // space for button

            if (orangeText.empty()) {
                // Single color text with word wrap
                SetTextColor(hdc, RGB(255, 255, 255));
                DrawTextW(hdc, mainText.c_str(), -1, &textRc, DT_LEFT | DT_WORDBREAK);
            } else {
                // Two-color text: draw main text with word wrap, then orange text on next line
                SetTextColor(hdc, RGB(255, 255, 255));
                int mainHeight = DrawTextW(hdc, mainText.c_str(), -1, &textRc, DT_LEFT | DT_WORDBREAK | DT_CALCRECT);

                // Actually draw main text
                DrawTextW(hdc, mainText.c_str(), -1, &textRc, DT_LEFT | DT_WORDBREAK);

                // Draw orange text below main text
                SetTextColor(hdc, RGB(255, 165, 0));
                RECT orangeRc = textRc;
                orangeRc.top += mainHeight + 4;
                orangeRc.bottom = rc.bottom - 30;
                DrawTextW(hdc, orangeText.c_str(), -1, &orangeRc, DT_LEFT | DT_WORDBREAK);
            }

            SelectObject(hdc, hOldFont);
            DeleteObject(hFont);
        }

        EndPaint(hWnd, &ps);
        return 0;
    }

    case WM_LBUTTONDOWN:
        // Allow dragging the float window (but not on the button)
        if (reinterpret_cast<HWND>(WindowFromPoint(POINT{GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam)})) != GetDlgItem(hWnd, IDC_BTN_FLOAT_CAPTURE)) {
            SetPropW(hWnd, L"UserMoved", reinterpret_cast<HANDLE>(1));
            SendMessage(hWnd, WM_NCLBUTTONDOWN, HTCAPTION, 0);
        }
        return 0;

    case WM_RBUTTONDOWN:
        // Right-click to close and cancel multi-page scan
        {
            MainWindow* mainWnd = reinterpret_cast<MainWindow*>(GetWindowLongPtrW(GetParent(hWnd), GWLP_USERDATA));
            if (mainWnd) {
                mainWnd->m_assembler.reset();
                mainWnd->CloseFloatProgress();
                mainWnd->UpdatePageInfo();
            }
        }
        return 0;

    case WM_DESTROY: {
        wchar_t* text = (wchar_t*)GetWindowLongPtrW(hWnd, GWLP_USERDATA);
        if (text) {
            delete[] text;
            SetWindowLongPtrW(hWnd, GWLP_USERDATA, 0);
        }
        // Clean up button font
        HFONT hFont = (HFONT)GetPropW(hWnd, L"BtnFont");
        if (hFont) {
            DeleteObject(hFont);
            RemovePropW(hWnd, L"BtnFont");
        }
        return 0;
    }

    case WM_ERASEBKGND:
        return 1;
    }

    return DefWindowProcW(hWnd, message, wParam, lParam);
}

void MainWindow::ShowFloatProgress(const std::wstring& msg)
{
    // Calculate text size to determine window dimensions
    HDC screenDc = GetDC(nullptr);
    HFONT hFont = CreateFontW(-14, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
        CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Microsoft YaHei");
    HFONT hOldFont = (HFONT)SelectObject(screenDc, hFont);

    // Split text by '|' separator
    std::wstring mainText, orangeText;
    size_t sepPos = msg.find(L"|");
    if (sepPos != std::wstring::npos) {
        mainText = msg.substr(0, sepPos);
        orangeText = msg.substr(sepPos + 1);
    } else {
        mainText = msg;
    }

    // Calculate required size for main text (max width = 400, word wrap)
    int maxWidth = 400;
    RECT calcRc = { 0, 0, maxWidth, 0 };
    int mainHeight = DrawTextW(screenDc, mainText.c_str(), -1, &calcRc, DT_LEFT | DT_WORDBREAK | DT_CALCRECT);
    int mainWidth = calcRc.right;

    int totalHeight = mainHeight;
    int totalWidth = mainWidth;

    // Calculate orange text size
    if (!orangeText.empty()) {
        RECT orangeCalcRc = { 0, 0, maxWidth, 0 };
        int orangeHeight = DrawTextW(screenDc, orangeText.c_str(), -1, &orangeCalcRc, DT_LEFT | DT_WORDBREAK | DT_CALCRECT);
        totalHeight += 4 + orangeHeight;
        totalWidth = (std::max)(totalWidth, (int)orangeCalcRc.right);
    }

    SelectObject(screenDc, hOldFont);
    DeleteObject(hFont);
    ReleaseDC(nullptr, screenDc);

    // Add padding + space for capture button at bottom
    int floatW = totalWidth + 32;  // 16px padding each side
    int floatH = totalHeight + 48; // 10px padding top + 28px for button area + 10px padding bottom
    floatW = (std::max)(floatW, 200);   // minimum width
    floatH = (std::max)(floatH, 80);     // minimum height (increased for button)
    floatW = (std::min)(floatW, 500);   // maximum width
    floatH = (std::min)(floatH, 400);   // maximum height (increased for button)

    // Calculate position (centered in the QR display area)
    RECT mainRc;
    GetWindowRect(m_hWnd, &mainRc);
    int floatX = mainRc.left + (mainRc.right - mainRc.left - floatW) / 2;
    int floatY = mainRc.top + TOOLBAR_HEIGHT + 4 + (m_qrRect.bottom - m_qrRect.top - floatH) / 2;

    // If float already exists, update its text and resize
    if (m_hFloatWnd && IsWindow(m_hFloatWnd)) {
        wchar_t* oldText = (wchar_t*)GetWindowLongPtrW(m_hFloatWnd, GWLP_USERDATA);
        if (oldText) delete[] oldText;

        size_t len = msg.size() + 1;
        wchar_t* textCopy = new wchar_t[len];
        wcscpy_s(textCopy, len, msg.c_str());
        SetWindowLongPtrW(m_hFloatWnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(textCopy));

        // If user has dragged the float window, keep its position, only resize
        if (GetPropW(m_hFloatWnd, L"UserMoved")) {
            RECT curRc;
            GetWindowRect(m_hFloatWnd, &curRc);
            MoveWindow(m_hFloatWnd, curRc.left, curRc.top, floatW, floatH, TRUE);
        } else {
            MoveWindow(m_hFloatWnd, floatX, floatY, floatW, floatH, TRUE);
        }
        InvalidateRect(m_hFloatWnd, nullptr, TRUE);
        return;
    }

    m_hFloatWnd = CreateWindowExW(
        WS_EX_TOPMOST | WS_EX_TOOLWINDOW | WS_EX_LAYERED,
        FLOAT_CLASS_NAME,
        L"",
        WS_POPUP,
        floatX, floatY, floatW, floatH,
        m_hWnd, nullptr, m_hInstance, nullptr
    );

    if (!m_hFloatWnd) return;

    // Set semi-transparency
    SetLayeredWindowAttributes(m_hFloatWnd, 0, 220, LWA_ALPHA);

    // Store text in window data
    size_t len = msg.size() + 1;
    wchar_t* textCopy = new wchar_t[len];
    wcscpy_s(textCopy, len, msg.c_str());
    SetWindowLongPtrW(m_hFloatWnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(textCopy));

    ShowWindow(m_hFloatWnd, SW_SHOW);
    UpdateWindow(m_hFloatWnd);
}

void MainWindow::CloseFloatProgress()
{
    if (m_hFloatWnd) {
        DestroyWindow(m_hFloatWnd);
        m_hFloatWnd = nullptr;
    }
}

// ============================================================
// Global hotkey
// ============================================================

void MainWindow::RegisterGlobalHotkey()
{
    UnregisterGlobalHotkey();
    if (m_hotkeyConfig.enabled && m_hWnd) {
        m_hotkeyRegistered = RegisterHotKey(m_hWnd, HOTKEY_ID,
            m_hotkeyConfig.modifiers, m_hotkeyConfig.vk);
    }
}

void MainWindow::UnregisterGlobalHotkey()
{
    if (m_hotkeyRegistered) {
        UnregisterHotKey(m_hWnd, HOTKEY_ID);
        m_hotkeyRegistered = false;
    }
}

void MainWindow::LoadHotkeyConfig()
{
    wchar_t exePath[MAX_PATH] = {};
    GetModuleFileNameW(nullptr, exePath, MAX_PATH);
    std::wstring iniPath = exePath;
    size_t lastSlash = iniPath.rfind(L'\\');
    if (lastSlash != std::wstring::npos) {
        iniPath = iniPath.substr(0, lastSlash + 1) + L"QRCodeTool.ini";
    } else {
        iniPath = L"QRCodeTool.ini";
    }

    UINT modifiers = GetPrivateProfileIntW(L"Hotkey", L"Modifiers", MOD_ALT | MOD_CONTROL, iniPath.c_str());
    UINT vk = GetPrivateProfileIntW(L"Hotkey", L"VK", 'S', iniPath.c_str());
    INT enabled = GetPrivateProfileIntW(L"Hotkey", L"Enabled", 1, iniPath.c_str());

    m_hotkeyConfig.modifiers = modifiers;
    m_hotkeyConfig.vk = vk;
    m_hotkeyConfig.enabled = (enabled != 0);
}

void MainWindow::SaveHotkeyConfig()
{
    wchar_t exePath[MAX_PATH] = {};
    GetModuleFileNameW(nullptr, exePath, MAX_PATH);
    std::wstring iniPath = exePath;
    size_t lastSlash = iniPath.rfind(L'\\');
    if (lastSlash != std::wstring::npos) {
        iniPath = iniPath.substr(0, lastSlash + 1) + L"QRCodeTool.ini";
    } else {
        iniPath = L"QRCodeTool.ini";
    }

    wchar_t buf[32] = {};
    _itow_s(m_hotkeyConfig.modifiers, buf, 10);
    WritePrivateProfileStringW(L"Hotkey", L"Modifiers", buf, iniPath.c_str());
    _itow_s(m_hotkeyConfig.vk, buf, 10);
    WritePrivateProfileStringW(L"Hotkey", L"VK", buf, iniPath.c_str());
    _itow_s(m_hotkeyConfig.enabled ? 1 : 0, buf, 10);
    WritePrivateProfileStringW(L"Hotkey", L"Enabled", buf, iniPath.c_str());
}

std::wstring MainWindow::GetHotkeyDisplayText() const
{
    if (!m_hotkeyConfig.enabled) return L"\u672A\u542F\u7528";

    std::wstring text;
    if (m_hotkeyConfig.modifiers & MOD_CONTROL) text += L"Ctrl+";
    if (m_hotkeyConfig.modifiers & MOD_ALT) text += L"Alt+";
    if (m_hotkeyConfig.modifiers & MOD_SHIFT) text += L"Shift+";
    if (m_hotkeyConfig.modifiers & MOD_WIN) text += L"Win+";

    // Convert VK code to display name
    UINT vk = m_hotkeyConfig.vk;
    if (vk >= 'A' && vk <= 'Z') {
        text += (wchar_t)vk;
    } else if (vk >= '0' && vk <= '9') {
        text += (wchar_t)vk;
    } else if (vk == VK_F1) text += L"F1";
    else if (vk == VK_F2) text += L"F2";
    else if (vk == VK_F3) text += L"F3";
    else if (vk == VK_F4) text += L"F4";
    else if (vk == VK_F5) text += L"F5";
    else if (vk == VK_F6) text += L"F6";
    else if (vk == VK_F7) text += L"F7";
    else if (vk == VK_F8) text += L"F8";
    else if (vk == VK_F9) text += L"F9";
    else if (vk == VK_F10) text += L"F10";
    else if (vk == VK_F11) text += L"F11";
    else if (vk == VK_F12) text += L"F12";
    else if (vk == VK_SPACE) text += L"Space";
    else if (vk == VK_RETURN) text += L"Enter";
    else if (vk == VK_ESCAPE) text += L"Esc";
    else {
        text += L"0x" + std::to_wstring(vk);
    }

    return text;
}

void MainWindow::OnGlobalHotkey()
{
    OnCapture();
}

void MainWindow::OnCaptureFromFloat()
{
    OnCapture();
}

// ============================================================
// Settings dialog
// ============================================================

// Dialog control IDs
#define IDC_CHK_HOTKEY_ENABLE  2001
#define IDC_LBL_HOTKEY_MOD     2002
#define IDC_CHK_MOD_CTRL       2003
#define IDC_CHK_MOD_ALT        2004
#define IDC_CHK_MOD_SHIFT      2005
#define IDC_CHK_MOD_WIN        2006
#define IDC_LBL_HOTKEY_KEY     2007
#define IDC_EDT_HOTKEY_KEY     2008
#define IDC_BTN_HOTKEY_RESET   2009

static std::wstring VkToDisplayName(UINT vk)
{
    if (vk >= 'A' && vk <= 'Z') return std::wstring(1, (wchar_t)vk);
    if (vk >= '0' && vk <= '9') return std::wstring(1, (wchar_t)vk);
    if (vk >= VK_F1 && vk <= VK_F12) return L"F" + std::to_wstring(vk - VK_F1 + 1);
    if (vk == VK_SPACE) return L"Space";
    if (vk == VK_RETURN) return L"Enter";
    if (vk == VK_ESCAPE) return L"Esc";
    if (vk == VK_TAB) return L"Tab";
    if (vk == VK_BACK) return L"Backspace";
    if (vk == VK_DELETE) return L"Delete";
    if (vk == VK_INSERT) return L"Insert";
    if (vk == VK_HOME) return L"Home";
    if (vk == VK_END) return L"End";
    if (vk == VK_PRIOR) return L"PgUp";
    if (vk == VK_NEXT) return L"PgDn";
    return L"0x" + std::to_wstring(vk);
}

// Subclass proc for hotkey edit control - captures key presses
LRESULT CALLBACK MainWindow::HotkeyEditSubclassProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam, UINT_PTR uIdSubclass, DWORD_PTR dwRefData)
{
    if (msg == WM_KEYDOWN || msg == WM_SYSKEYDOWN) {
        UINT vk = (UINT)wParam;
        // Ignore modifier-only keys
        if (vk != VK_CONTROL && vk != VK_MENU && vk != VK_SHIFT && vk != VK_LWIN && vk != VK_RWIN) {
            // Update modifier checkboxes based on currently held keys
            HWND hDlg = GetParent(hWnd);
            UINT mods = 0;
            if (GetKeyState(VK_CONTROL) & 0x8000) mods |= MOD_CONTROL;
            if (GetKeyState(VK_MENU) & 0x8000) mods |= MOD_ALT;
            if (GetKeyState(VK_SHIFT) & 0x8000) mods |= MOD_SHIFT;
            CheckDlgButton(hDlg, IDC_CHK_MOD_CTRL, (mods & MOD_CONTROL) ? BST_CHECKED : BST_UNCHECKED);
            CheckDlgButton(hDlg, IDC_CHK_MOD_ALT, (mods & MOD_ALT) ? BST_CHECKED : BST_UNCHECKED);
            CheckDlgButton(hDlg, IDC_CHK_MOD_SHIFT, (mods & MOD_SHIFT) ? BST_CHECKED : BST_UNCHECKED);

            // Display the key name
            SetWindowTextW(hWnd, VkToDisplayName(vk).c_str());

            // Store captured VK in dialog prop
            SetPropW(hDlg, L"CapturedVK", reinterpret_cast<HANDLE>(static_cast<UINT_PTR>(vk)));
            return 0;
        }
        return 0;  // Don't process modifier-only keys
    }
    if (msg == WM_CHAR || msg == WM_SYSCHAR) {
        return 0;  // Swallow character messages
    }
    return DefSubclassProc(hWnd, msg, wParam, lParam);
}

INT_PTR CALLBACK MainWindow::SettingsDlgProc(HWND hDlg, UINT message, WPARAM wParam, LPARAM lParam)
{
    MainWindow* self = reinterpret_cast<MainWindow*>(GetPropW(hDlg, L"MainWnd"));

    switch (message) {
    case WM_COMMAND:
        switch (LOWORD(wParam)) {
        case IDC_CHK_HOTKEY_ENABLE: {
            BOOL enabled = IsDlgButtonChecked(hDlg, IDC_CHK_HOTKEY_ENABLE) == BST_CHECKED;
            EnableWindow(GetDlgItem(hDlg, IDC_CHK_MOD_CTRL), enabled);
            EnableWindow(GetDlgItem(hDlg, IDC_CHK_MOD_ALT), enabled);
            EnableWindow(GetDlgItem(hDlg, IDC_CHK_MOD_SHIFT), enabled);
            EnableWindow(GetDlgItem(hDlg, IDC_CHK_MOD_WIN), enabled);
            EnableWindow(GetDlgItem(hDlg, IDC_EDT_HOTKEY_KEY), enabled);
            EnableWindow(GetDlgItem(hDlg, IDC_BTN_HOTKEY_RESET), enabled);
            return TRUE;
        }
        case IDC_BTN_HOTKEY_RESET:
            // Reset to default Ctrl+Alt+S
            CheckDlgButton(hDlg, IDC_CHK_MOD_CTRL, BST_CHECKED);
            CheckDlgButton(hDlg, IDC_CHK_MOD_ALT, BST_CHECKED);
            CheckDlgButton(hDlg, IDC_CHK_MOD_SHIFT, BST_UNCHECKED);
            CheckDlgButton(hDlg, IDC_CHK_MOD_WIN, BST_UNCHECKED);
            SetDlgItemTextW(hDlg, IDC_EDT_HOTKEY_KEY, L"S");
            SetPropW(hDlg, L"CapturedVK", reinterpret_cast<HANDLE>(static_cast<UINT_PTR>('S')));
            return TRUE;
        case IDOK: {
            if (!self) { DestroyWindow(hDlg); return TRUE; }
            // Save settings
            self->m_hotkeyConfig.enabled = IsDlgButtonChecked(hDlg, IDC_CHK_HOTKEY_ENABLE) == BST_CHECKED;
            self->m_hotkeyConfig.modifiers = 0;
            if (IsDlgButtonChecked(hDlg, IDC_CHK_MOD_CTRL)) self->m_hotkeyConfig.modifiers |= MOD_CONTROL;
            if (IsDlgButtonChecked(hDlg, IDC_CHK_MOD_ALT)) self->m_hotkeyConfig.modifiers |= MOD_ALT;
            if (IsDlgButtonChecked(hDlg, IDC_CHK_MOD_SHIFT)) self->m_hotkeyConfig.modifiers |= MOD_SHIFT;
            if (IsDlgButtonChecked(hDlg, IDC_CHK_MOD_WIN)) self->m_hotkeyConfig.modifiers |= MOD_WIN;

            // Read captured VK from prop
            HANDLE capturedVK = GetPropW(hDlg, L"CapturedVK");
            if (capturedVK) {
                self->m_hotkeyConfig.vk = static_cast<UINT>(reinterpret_cast<UINT_PTR>(capturedVK));
            }

            // Validate: must have at least one modifier and a key
            if (self->m_hotkeyConfig.enabled && self->m_hotkeyConfig.modifiers == 0) {
                MessageBoxW(hDlg,
                    L"\u8BF7\u81F3\u5C11\u9009\u62E9\u4E00\u4E2A\u4FEE\u9970\u952E\uFF08Ctrl/Alt/Shift/Win\uFF09",
                    L"\u63D0\u793A", MB_OK | MB_ICONWARNING);
                return TRUE;
            }

            // Remove subclass before closing
            HWND hEdtKey = GetDlgItem(hDlg, IDC_EDT_HOTKEY_KEY);
            if (hEdtKey) RemoveWindowSubclass(hEdtKey, HotkeyEditSubclassProc, 0);

            // Clean up props
            RemovePropW(hDlg, L"CapturedVK");
            RemovePropW(hDlg, L"MainWnd");
            HFONT hFont = (HFONT)GetPropW(hDlg, L"DlgFont");
            if (hFont) { DeleteObject(hFont); RemovePropW(hDlg, L"DlgFont"); }

            self->SaveHotkeyConfig();
            self->RegisterGlobalHotkey();
            DestroyWindow(hDlg);
            return TRUE;
        }
        case IDCANCEL: {
            // Remove subclass before closing
            HWND hEdtKey = GetDlgItem(hDlg, IDC_EDT_HOTKEY_KEY);
            if (hEdtKey) RemoveWindowSubclass(hEdtKey, HotkeyEditSubclassProc, 0);

            // Clean up props
            RemovePropW(hDlg, L"CapturedVK");
            RemovePropW(hDlg, L"MainWnd");
            HFONT hFont = (HFONT)GetPropW(hDlg, L"DlgFont");
            if (hFont) { DeleteObject(hFont); RemovePropW(hDlg, L"DlgFont"); }

            DestroyWindow(hDlg);
            return TRUE;
        }
        }
        break;

    case WM_CLOSE: {
        // Handle X button / Alt+F4
        HWND hEdtKey = GetDlgItem(hDlg, IDC_EDT_HOTKEY_KEY);
        if (hEdtKey) RemoveWindowSubclass(hEdtKey, HotkeyEditSubclassProc, 0);
        RemovePropW(hDlg, L"CapturedVK");
        RemovePropW(hDlg, L"MainWnd");
        HFONT hFont = (HFONT)GetPropW(hDlg, L"DlgFont");
        if (hFont) { DeleteObject(hFont); RemovePropW(hDlg, L"DlgFont"); }
        DestroyWindow(hDlg);
        return TRUE;
    }
    }

    return FALSE;
}

void MainWindow::ShowSettingsDialog()
{
    // Create a modal popup window directly instead of using DialogBoxIndirectParamW
    // which has tricky memory alignment requirements

    const wchar_t* SETTINGS_CLASS = L"QRSettingsDlgClass";
    static bool registered = false;
    if (!registered) {
        WNDCLASSEXW wc = {};
        wc.cbSize = sizeof(wc);
        wc.lpfnWndProc = DefWindowProcW;
        wc.hInstance = m_hInstance;
        wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
        wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
        wc.lpszClassName = SETTINGS_CLASS;
        RegisterClassExW(&wc);
        registered = true;
    }

    // Calculate centered position
    int dlgW = 320, dlgH = 230;
    RECT parentRc;
    GetWindowRect(m_hWnd, &parentRc);
    int dlgX = parentRc.left + (parentRc.right - parentRc.left - dlgW) / 2;
    int dlgY = parentRc.top + (parentRc.bottom - parentRc.top - dlgH) / 2;

    HWND hDlg = CreateWindowExW(
        WS_EX_DLGMODALFRAME,
        SETTINGS_CLASS,
        L"\u8BBE\u7F6E",
        WS_POPUP | WS_CAPTION | WS_SYSMENU,
        dlgX, dlgY, dlgW, dlgH,
        m_hWnd, nullptr, m_hInstance, nullptr
    );

    if (!hDlg) return;

    // Disable parent window (modal behavior)
    EnableWindow(m_hWnd, FALSE);

    HFONT hDlgFont = CreateFontW(-13, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
        CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Microsoft YaHei");

    // Create all controls
    HWND hChkEnable = CreateWindowExW(0, L"BUTTON",
        L"\u542F\u7528\u5168\u5C40\u5FEB\u6377\u952E",
        WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX,
        16, 12, 200, 20,
        hDlg, reinterpret_cast<HMENU>(IDC_CHK_HOTKEY_ENABLE), nullptr, nullptr);
    SendMessageW(hChkEnable, WM_SETFONT, reinterpret_cast<WPARAM>(hDlgFont), TRUE);

    HWND hLblMod = CreateWindowExW(0, L"STATIC",
        L"\u4FEE\u9970\u952E\uFF1A",
        WS_CHILD | WS_VISIBLE,
        16, 42, 70, 18,
        hDlg, reinterpret_cast<HMENU>(IDC_LBL_HOTKEY_MOD), nullptr, nullptr);
    SendMessageW(hLblMod, WM_SETFONT, reinterpret_cast<WPARAM>(hDlgFont), TRUE);

    HWND hChkCtrl = CreateWindowExW(0, L"BUTTON", L"Ctrl",
        WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX,
        90, 40, 50, 20,
        hDlg, reinterpret_cast<HMENU>(IDC_CHK_MOD_CTRL), nullptr, nullptr);
    SendMessageW(hChkCtrl, WM_SETFONT, reinterpret_cast<WPARAM>(hDlgFont), TRUE);
    HWND hChkAlt = CreateWindowExW(0, L"BUTTON", L"Alt",
        WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX,
        146, 40, 42, 20,
        hDlg, reinterpret_cast<HMENU>(IDC_CHK_MOD_ALT), nullptr, nullptr);
    SendMessageW(hChkAlt, WM_SETFONT, reinterpret_cast<WPARAM>(hDlgFont), TRUE);
    HWND hChkShift = CreateWindowExW(0, L"BUTTON", L"Shift",
        WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX,
        194, 40, 52, 20,
        hDlg, reinterpret_cast<HMENU>(IDC_CHK_MOD_SHIFT), nullptr, nullptr);
    SendMessageW(hChkShift, WM_SETFONT, reinterpret_cast<WPARAM>(hDlgFont), TRUE);
    HWND hChkWin = CreateWindowExW(0, L"BUTTON", L"Win",
        WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX,
        252, 40, 46, 20,
        hDlg, reinterpret_cast<HMENU>(IDC_CHK_MOD_WIN), nullptr, nullptr);
    SendMessageW(hChkWin, WM_SETFONT, reinterpret_cast<WPARAM>(hDlgFont), TRUE);

    HWND hLblKey = CreateWindowExW(0, L"STATIC",
        L"\u6309\u952E\uFF1A",
        WS_CHILD | WS_VISIBLE,
        16, 70, 70, 18,
        hDlg, reinterpret_cast<HMENU>(IDC_LBL_HOTKEY_KEY), nullptr, nullptr);
    SendMessageW(hLblKey, WM_SETFONT, reinterpret_cast<WPARAM>(hDlgFont), TRUE);

    HWND hEdtKey = CreateWindowExW(0, L"EDIT",
        L"",
        WS_CHILD | WS_VISIBLE | WS_BORDER | ES_READONLY,
        90, 68, 60, 22,
        hDlg, reinterpret_cast<HMENU>(IDC_EDT_HOTKEY_KEY), nullptr, nullptr);
    SendMessageW(hEdtKey, WM_SETFONT, reinterpret_cast<WPARAM>(hDlgFont), TRUE);

    // Subclass the edit control to capture key presses
    SetWindowSubclass(hEdtKey, HotkeyEditSubclassProc, 0, 0);

    HWND hBtnReset = CreateWindowExW(0, L"BUTTON",
        L"\u91CD\u7F6E",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        160, 68, 50, 22,
        hDlg, reinterpret_cast<HMENU>(IDC_BTN_HOTKEY_RESET), nullptr, nullptr);
    SendMessageW(hBtnReset, WM_SETFONT, reinterpret_cast<WPARAM>(hDlgFont), TRUE);

    HWND hLblHint = CreateWindowExW(0, L"STATIC",
        L"\u70B9\u51FB\u201C\u6309\u952E\u201D\u6846\u540E\u6309\u4E0B\u65B0\u7684\u5FEB\u6377\u952E",
        WS_CHILD | WS_VISIBLE,
        16, 98, 280, 18,
        hDlg, nullptr, nullptr, nullptr);
    SendMessageW(hLblHint, WM_SETFONT, reinterpret_cast<WPARAM>(hDlgFont), TRUE);

    HWND hBtnOK = CreateWindowExW(0, L"BUTTON",
        L"\u786E\u5B9A",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON | BS_DEFPUSHBUTTON,
        100, 160, 70, 26,
        hDlg, reinterpret_cast<HMENU>(IDOK), nullptr, nullptr);
    SendMessageW(hBtnOK, WM_SETFONT, reinterpret_cast<WPARAM>(hDlgFont), TRUE);
    HWND hBtnCancel = CreateWindowExW(0, L"BUTTON",
        L"\u53D6\u6D88",
        WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
        180, 160, 70, 26,
        hDlg, reinterpret_cast<HMENU>(IDCANCEL), nullptr, nullptr);
    SendMessageW(hBtnCancel, WM_SETFONT, reinterpret_cast<WPARAM>(hDlgFont), TRUE);

    // Initialize control states
    CheckDlgButton(hDlg, IDC_CHK_HOTKEY_ENABLE, m_hotkeyConfig.enabled ? BST_CHECKED : BST_UNCHECKED);
    CheckDlgButton(hDlg, IDC_CHK_MOD_CTRL, (m_hotkeyConfig.modifiers & MOD_CONTROL) ? BST_CHECKED : BST_UNCHECKED);
    CheckDlgButton(hDlg, IDC_CHK_MOD_ALT, (m_hotkeyConfig.modifiers & MOD_ALT) ? BST_CHECKED : BST_UNCHECKED);
    CheckDlgButton(hDlg, IDC_CHK_MOD_SHIFT, (m_hotkeyConfig.modifiers & MOD_SHIFT) ? BST_CHECKED : BST_UNCHECKED);
    CheckDlgButton(hDlg, IDC_CHK_MOD_WIN, (m_hotkeyConfig.modifiers & MOD_WIN) ? BST_CHECKED : BST_UNCHECKED);
    SetDlgItemTextW(hDlg, IDC_EDT_HOTKEY_KEY, VkToDisplayName(m_hotkeyConfig.vk).c_str());

    // Store state in dialog props
    SetPropW(hDlg, L"MainWnd", reinterpret_cast<HANDLE>(this));
    SetPropW(hDlg, L"CapturedVK", reinterpret_cast<HANDLE>(static_cast<UINT_PTR>(m_hotkeyConfig.vk)));
    SetPropW(hDlg, L"DlgFont", hDlgFont);

    // Enable/disable based on enabled state
    BOOL enabled = m_hotkeyConfig.enabled;
    EnableWindow(GetDlgItem(hDlg, IDC_CHK_MOD_CTRL), enabled);
    EnableWindow(GetDlgItem(hDlg, IDC_CHK_MOD_ALT), enabled);
    EnableWindow(GetDlgItem(hDlg, IDC_CHK_MOD_SHIFT), enabled);
    EnableWindow(GetDlgItem(hDlg, IDC_CHK_MOD_WIN), enabled);
    EnableWindow(GetDlgItem(hDlg, IDC_EDT_HOTKEY_KEY), enabled);
    EnableWindow(GetDlgItem(hDlg, IDC_BTN_HOTKEY_RESET), enabled);

    ShowWindow(hDlg, SW_SHOW);
    UpdateWindow(hDlg);

    // Modal message loop
    MSG msg;
    while (IsWindow(hDlg) && GetMessageW(&msg, nullptr, 0, 0)) {
        if (msg.message == WM_KEYDOWN || msg.message == WM_SYSKEYDOWN) {
            // Forward key messages to the dialog for hotkey capture
            if (GetFocus() == GetDlgItem(hDlg, IDC_EDT_HOTKEY_KEY)) {
                SendMessageW(GetDlgItem(hDlg, IDC_EDT_HOTKEY_KEY), msg.message, msg.wParam, msg.lParam);
                continue;
            }
        }
        if (!IsWindow(hDlg) || !IsDialogMessageW(hDlg, &msg)) {
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }
    }

    // Re-enable parent window
    EnableWindow(m_hWnd, TRUE);
    SetFocus(m_hWnd);
}

} // namespace qr
