#include "ScreenCapture.h"
#include <windowsx.h>
#include <algorithm>

namespace qr {

struct CaptureState {
    HBITMAP screenBitmap;
    POINT startPoint;
    POINT endPoint;
    bool dragging;
    bool completed;
    bool cancelled;
    RECT selectedRect;
    bool showHint;  // show initial hint text
};

static CaptureState g_captureState;
static HWND g_overlayWnd = NULL;

static LRESULT CALLBACK OverlayWndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_CREATE: {
        SetWindowLong(hWnd, GWL_EXSTYLE, GetWindowLong(hWnd, GWL_EXSTYLE) | WS_EX_LAYERED);
        SetLayeredWindowAttributes(hWnd, 0, 255, LWA_ALPHA);
        return 0;
    }

    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hWnd, &ps);

        RECT clientRect;
        GetClientRect(hWnd, &clientRect);
        int cx = clientRect.right;
        int cy = clientRect.bottom;

        // Draw the screenshot as background
        HDC memDC = CreateCompatibleDC(hdc);
        HBITMAP hOld = (HBITMAP)SelectObject(memDC, g_captureState.screenBitmap);

        BITMAP bm;
        GetObject(g_captureState.screenBitmap, sizeof(bm), &bm);
        BitBlt(hdc, 0, 0, cx, cy, memDC, 0, 0, SRCCOPY);

        // Draw semi-transparent dark overlay
        HDC overlayDC = CreateCompatibleDC(hdc);
        HBITMAP overlayBmp = CreateCompatibleBitmap(hdc, cx, cy);
        HBITMAP hOldOverlay = (HBITMAP)SelectObject(overlayDC, overlayBmp);

        // Fill with dark color
        HBRUSH darkBrush = CreateSolidBrush(RGB(0, 0, 0));
        RECT fillRect = {0, 0, cx, cy};
        FillRect(overlayDC, &fillRect, darkBrush);
        DeleteObject(darkBrush);

        // If dragging, clear the selected area in the overlay (make it transparent)
        if (g_captureState.dragging) {
            int x1 = std::min(g_captureState.startPoint.x, g_captureState.endPoint.x);
            int y1 = std::min(g_captureState.startPoint.y, g_captureState.endPoint.y);
            int x2 = std::max(g_captureState.startPoint.x, g_captureState.endPoint.x);
            int y2 = std::max(g_captureState.startPoint.y, g_captureState.endPoint.y);

            // Clear the selection area in overlay (white = transparent after blending)
            RECT selRect = {x1, y1, x2, y2};
            HBRUSH whiteBrush = CreateSolidBrush(RGB(255, 255, 255));
            FillRect(overlayDC, &selRect, whiteBrush);
            DeleteObject(whiteBrush);
        }

        // Blend overlay with 30% opacity onto the screenshot
        BLENDFUNCTION bf = {};
        bf.BlendOp = AC_SRC_OVER;
        bf.SourceConstantAlpha = 77; // ~30% of 255
        bf.AlphaFormat = 0;
        AlphaBlend(hdc, 0, 0, cx, cy, overlayDC, 0, 0, cx, cy, bf);

        SelectObject(overlayDC, hOldOverlay);
        DeleteObject(overlayBmp);
        DeleteDC(overlayDC);

        // If dragging, draw the original screenshot in the selected area and green border
        if (g_captureState.dragging) {
            int x1 = std::min(g_captureState.startPoint.x, g_captureState.endPoint.x);
            int y1 = std::min(g_captureState.startPoint.y, g_captureState.endPoint.y);
            int x2 = std::max(g_captureState.startPoint.x, g_captureState.endPoint.x);
            int y2 = std::max(g_captureState.startPoint.y, g_captureState.endPoint.y);

            // Redraw original screenshot in selected area
            BitBlt(hdc, x1, y1, x2 - x1, y2 - y1, memDC, x1, y1, SRCCOPY);

            // Draw green border
            HPEN greenPen = CreatePen(PS_SOLID, 2, RGB(0, 255, 0));
            HBRUSH nullBrush = (HBRUSH)GetStockObject(NULL_BRUSH);
            HPEN oldPen = (HPEN)SelectObject(hdc, greenPen);
            HBRUSH oldBrush = (HBRUSH)SelectObject(hdc, nullBrush);
            Rectangle(hdc, x1, y1, x2, y2);
            SelectObject(hdc, oldBrush);
            SelectObject(hdc, oldPen);
            DeleteObject(greenPen);
        }

        SelectObject(memDC, hOld);
        DeleteDC(memDC);

        // Draw hint text when not dragging (initial state)
        if (g_captureState.showHint && !g_captureState.dragging) {
            const wchar_t* hintLine1 = L"\u8BF7\u62D6\u62FD\u9009\u62E9\u622A\u56FE\u533A\u57DF";
            const wchar_t* hintLine2 = L"ESC \u53D6\u6D88";

            HFONT hHintFont = CreateFontW(-20, 0, 0, 0, FW_BOLD, FALSE, FALSE, FALSE,
                DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Microsoft YaHei");
            HFONT hOldFont = (HFONT)SelectObject(hdc, hHintFont);

            SetBkMode(hdc, TRANSPARENT);

            // Calculate text sizes
            SIZE sz1, sz2;
            GetTextExtentPoint32W(hdc, hintLine1, (int)wcslen(hintLine1), &sz1);
            GetTextExtentPoint32W(hdc, hintLine2, (int)wcslen(hintLine2), &sz2);

            int textW = (sz1.cx > sz2.cx ? sz1.cx : sz2.cx) + 40;
            int textH = sz1.cy + sz2.cy + 30;
            int textX = (cx - textW) / 2;
            int textY = (cy - textH) / 2;

            // Draw semi-transparent background box
            HDC boxDC = CreateCompatibleDC(hdc);
            HBITMAP boxBmp = CreateCompatibleBitmap(hdc, textW, textH);
            HBITMAP hOldBox = (HBITMAP)SelectObject(boxDC, boxBmp);
            HBRUSH bgBrush = CreateSolidBrush(RGB(0, 0, 0));
            RECT boxRc = {0, 0, textW, textH};
            FillRect(boxDC, &boxRc, bgBrush);
            DeleteObject(bgBrush);
            BLENDFUNCTION bf2 = {};
            bf2.BlendOp = AC_SRC_OVER;
            bf2.SourceConstantAlpha = 180;
            bf2.AlphaFormat = 0;
            AlphaBlend(hdc, textX, textY, textW, textH, boxDC, 0, 0, textW, textH, bf2);
            SelectObject(boxDC, hOldBox);
            DeleteObject(boxBmp);
            DeleteDC(boxDC);

            // Draw hint text
            SetTextColor(hdc, RGB(255, 255, 255));
            int line1X = textX + (textW - sz1.cx) / 2;
            int line1Y = textY + 12;
            TextOutW(hdc, line1X, line1Y, hintLine1, (int)wcslen(hintLine1));

            HFONT hSmallFont = CreateFontW(-14, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Microsoft YaHei");
            SelectObject(hdc, hSmallFont);
            SetTextColor(hdc, RGB(200, 200, 200));
            SIZE sz2b;
            GetTextExtentPoint32W(hdc, hintLine2, (int)wcslen(hintLine2), &sz2b);
            int line2X = textX + (textW - sz2b.cx) / 2;
            int line2Y = line1Y + sz1.cy + 6;
            TextOutW(hdc, line2X, line2Y, hintLine2, (int)wcslen(hintLine2));

            SelectObject(hdc, hOldFont);
            DeleteObject(hHintFont);
            DeleteObject(hSmallFont);
        }

        EndPaint(hWnd, &ps);
        return 0;
    }

    case WM_LBUTTONDOWN: {
        g_captureState.showHint = false;  // hide hint on first click
        g_captureState.startPoint = {GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam)};
        g_captureState.endPoint = g_captureState.startPoint;
        g_captureState.dragging = true;
        return 0;
    }

    case WM_MOUSEMOVE: {
        if (g_captureState.dragging) {
            g_captureState.endPoint = {GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam)};
            InvalidateRect(hWnd, NULL, FALSE);
        }
        return 0;
    }

    case WM_LBUTTONUP: {
        if (g_captureState.dragging) {
            g_captureState.dragging = false;
            g_captureState.endPoint = {GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam)};

            int x1 = std::min(g_captureState.startPoint.x, g_captureState.endPoint.x);
            int y1 = std::min(g_captureState.startPoint.y, g_captureState.endPoint.y);
            int x2 = std::max(g_captureState.startPoint.x, g_captureState.endPoint.x);
            int y2 = std::max(g_captureState.startPoint.y, g_captureState.endPoint.y);

            if (x2 - x1 < 40 || y2 - y1 < 40) {
                g_captureState.cancelled = true;
            } else {
                g_captureState.selectedRect = {x1, y1, x2, y2};
                g_captureState.completed = true;
            }
            PostMessage(hWnd, WM_CLOSE, 0, 0);
        }
        return 0;
    }

    case WM_KEYDOWN: {
        if (wParam == VK_ESCAPE) {
            g_captureState.cancelled = true;
            PostMessage(hWnd, WM_CLOSE, 0, 0);
        }
        return 0;
    }

    case WM_ERASEBKGND:
        return 1;
    }

    return DefWindowProc(hWnd, msg, wParam, lParam);
}

HBITMAP captureScreenSelection(HWND parentWindow) {
    // Get virtual screen dimensions
    int vx = GetSystemMetrics(SM_XVIRTUALSCREEN);
    int vy = GetSystemMetrics(SM_YVIRTUALSCREEN);
    int vw = GetSystemMetrics(SM_CXVIRTUALSCREEN);
    int vh = GetSystemMetrics(SM_CYVIRTUALSCREEN);

    // Capture the entire screen
    HDC screenDC = GetDC(NULL);
    HDC memDC = CreateCompatibleDC(screenDC);
    HBITMAP hScreenBmp = CreateCompatibleBitmap(screenDC, vw, vh);
    HBITMAP hOld = (HBITMAP)SelectObject(memDC, hScreenBmp);
    BitBlt(memDC, 0, 0, vw, vh, screenDC, vx, vy, SRCCOPY);
    SelectObject(memDC, hOld);
    DeleteDC(memDC);
    ReleaseDC(NULL, screenDC);

    // Initialize capture state
    g_captureState = {};
    g_captureState.screenBitmap = hScreenBmp;
    g_captureState.dragging = false;
    g_captureState.completed = false;
    g_captureState.cancelled = false;
    g_captureState.showHint = true;

    // Register overlay window class
    static bool registered = false;
    const wchar_t* className = L"QrScreenCaptureOverlay";
    if (!registered) {
        WNDCLASSEXW wc = {};
        wc.cbSize = sizeof(wc);
        wc.style = CS_HREDRAW | CS_VREDRAW;
        wc.lpfnWndProc = OverlayWndProc;
        wc.hInstance = GetModuleHandle(NULL);
        wc.hCursor = LoadCursor(NULL, IDC_CROSS);
        wc.hbrBackground = NULL;
        wc.lpszClassName = className;
        RegisterClassExW(&wc);
        registered = true;
    }

    // Create fullscreen topmost popup window
    g_overlayWnd = CreateWindowExW(
        WS_EX_TOPMOST | WS_EX_LAYERED,
        className,
        L"Screen Capture",
        WS_POPUP,
        vx, vy, vw, vh,
        parentWindow,
        NULL,
        GetModuleHandle(NULL),
        NULL
    );

    if (!g_overlayWnd) {
        DeleteObject(hScreenBmp);
        return NULL;
    }

    ShowWindow(g_overlayWnd, SW_SHOW);
    SetForegroundWindow(g_overlayWnd);
    SetCursor(LoadCursor(NULL, IDC_CROSS));

    // Local message loop
    MSG msg;
    while (!g_captureState.completed && !g_captureState.cancelled) {
        if (PeekMessage(&msg, NULL, 0, 0, PM_REMOVE)) {
            if (msg.message == WM_QUIT) break;
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }
    }

    HBITMAP hResult = NULL;

    if (g_captureState.completed) {
        RECT sel = g_captureState.selectedRect;
        int selW = sel.right - sel.left;
        int selH = sel.bottom - sel.top;

        // Crop the selected area from the screen bitmap
        // Use the screen DC to create a compatible bitmap with correct color depth
        HDC screenDC = GetDC(NULL);
        HDC srcDC = CreateCompatibleDC(screenDC);
        HDC dstDC = CreateCompatibleDC(screenDC);
        HBITMAP hCropBmp = CreateCompatibleBitmap(screenDC, selW, selH);
        ReleaseDC(NULL, screenDC);
        HBITMAP hOldSrc = (HBITMAP)SelectObject(srcDC, hScreenBmp);
        HBITMAP hOldDst = (HBITMAP)SelectObject(dstDC, hCropBmp);
        BitBlt(dstDC, 0, 0, selW, selH, srcDC, sel.left, sel.top, SRCCOPY);
        SelectObject(srcDC, hOldSrc);
        SelectObject(dstDC, hOldDst);
        DeleteDC(srcDC);
        DeleteDC(dstDC);

        hResult = hCropBmp;
    }

    // Cleanup
    DestroyWindow(g_overlayWnd);
    g_overlayWnd = NULL;
    DeleteObject(hScreenBmp);

    return hResult;
}

} // namespace qr
