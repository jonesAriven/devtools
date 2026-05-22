#include <windows.h>
#include <gdiplus.h>
#include "MainWindow.h"

#pragma comment(lib, "gdiplus.lib")

// Declare DPI awareness API for compatibility
extern "C" {
    typedef BOOL(WINAPI* SetProcessDPIAwareFunc)(void);
}

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nCmdShow) {
    // Enable DPI awareness for multi-monitor support
    HMODULE hUser32 = GetModuleHandleW(L"user32.dll");
    if (hUser32) {
        auto pSetProcessDPIAware = reinterpret_cast<SetProcessDPIAwareFunc>(
            GetProcAddress(hUser32, "SetProcessDPIAware"));
        if (pSetProcessDPIAware) {
            pSetProcessDPIAware();
        }
    }
    // Initialize GDI+
    Gdiplus::GdiplusStartupInput gdiplusStartupInput;
    ULONG_PTR gdiplusToken;
    Gdiplus::GdiplusStartup(&gdiplusToken, &gdiplusStartupInput, NULL);

    // Create and run main window
    qr::MainWindow mainWindow(hInstance);
    if (!mainWindow.Create()) {
        Gdiplus::GdiplusShutdown(gdiplusToken);
        return 1;
    }

    mainWindow.Show(nCmdShow);

    // Message loop
    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    // Cleanup GDI+
    Gdiplus::GdiplusShutdown(gdiplusToken);

    return (int)msg.wParam;
}
