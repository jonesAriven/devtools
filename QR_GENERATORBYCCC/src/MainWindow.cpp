#include "MainWindow.h"
#include "Resource.h"
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

// Control IDs
#define IDC_CHK_COMPRESS  1001
#define IDC_BTN_CAPTURE   1002
#define IDC_BTN_UPLOAD    1003
#define IDC_TXT_CONTENT   1005
#define IDC_CMB_ECL       1006
#define IDC_BTN_PREV      1007
#define IDC_BTN_NEXT      1008
#define IDC_LBL_PAGE      1009
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
    MoveWindow(m_hCmbEcl, width - 60, 2, 55, 200, TRUE);
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

            RECT textRc = rc;
            textRc.left += 16;
            textRc.right -= 16;
            textRc.top += 10;
            textRc.bottom -= 10;

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
                orangeRc.bottom = rc.bottom - 10;
                DrawTextW(hdc, orangeText.c_str(), -1, &orangeRc, DT_LEFT | DT_WORDBREAK);
            }

            SelectObject(hdc, hOldFont);
            DeleteObject(hFont);
        }

        EndPaint(hWnd, &ps);
        return 0;
    }

    case WM_LBUTTONDOWN:
        // Allow dragging the float window
        SendMessage(hWnd, WM_NCLBUTTONDOWN, HTCAPTION, 0);
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

    // Add padding
    int floatW = totalWidth + 32;  // 16px padding each side
    int floatH = totalHeight + 20; // 10px padding top/bottom
    floatW = (std::max)(floatW, 200);   // minimum width
    floatH = (std::max)(floatH, 50);    // minimum height
    floatW = (std::min)(floatW, 500);   // maximum width
    floatH = (std::min)(floatH, 300);   // maximum height

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

        MoveWindow(m_hFloatWnd, floatX, floatY, floatW, floatH, TRUE);
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

} // namespace qr
