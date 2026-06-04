#pragma once
#include <windows.h>
#include <string>
#include <vector>
#include "QrGenerator.h"
#include "Compressor.h"

namespace qr {

// Hotkey configuration
struct HotkeyConfig {
    UINT modifiers;  // MOD_ALT, MOD_CONTROL, MOD_SHIFT, MOD_WIN combination
    UINT vk;         // Virtual key code
    bool enabled;
    bool IsDefault() const {
        return modifiers == (MOD_ALT | MOD_CONTROL) && vk == 'S' && enabled;
    }
};

class MainWindow {
public:
    MainWindow(HINSTANCE hInstance);
    ~MainWindow();

    bool Create();
    void Show(int nCmdShow);

    // Called when global hotkey is triggered
    void OnGlobalHotkey();

    // Public access for float window callback
    void OnCaptureFromFloat();

private:
    static LRESULT CALLBACK WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam);
    static LRESULT CALLBACK EditSubclassProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam, UINT_PTR uIdSubclass, DWORD_PTR dwRefData);
    static LRESULT CALLBACK FloatWndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam);
    LRESULT HandleMessage(UINT message, WPARAM wParam, LPARAM lParam);

    void BuildUI();
    void ResizeControls();
    void GenerateQr();
    void OnCapture();
    void OnUpload();
    void OnTextChanged();
    void OnEclChanged();
    void OnPrevPage();
    void OnNextPage();
    void UpdateQrImage(HBITMAP hBmp);
    void UpdatePageInfo();
    void SetText(const std::string& text);
    std::string GetText() const;
    std::wstring Utf8ToWide(const std::string& str) const;
    std::string WideToUtf8(const std::wstring& wstr) const;
    void PaintQrCode(HDC hdc, const RECT& clientRect);

    // Floating progress window
    void ShowFloatProgress(const std::wstring& msg);
    void CloseFloatProgress();

    // Global hotkey
    void RegisterGlobalHotkey();
    void UnregisterGlobalHotkey();
    void LoadHotkeyConfig();
    void SaveHotkeyConfig();
    std::wstring GetHotkeyDisplayText() const;

    // Settings dialog
    void ShowSettingsDialog();
    static INT_PTR CALLBACK SettingsDlgProc(HWND hDlg, UINT message, WPARAM wParam, LPARAM lParam);
    static LRESULT CALLBACK HotkeyEditSubclassProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam, UINT_PTR uIdSubclass, DWORD_PTR dwRefData);

    HINSTANCE m_hInstance;
    HWND m_hWnd;
    HWND m_hLblCompress;
    HWND m_hBtnCapture;
    HWND m_hBtnUpload;
    HWND m_hTxtContent;
    HWND m_hCmbEcl;
    HWND m_hBtnPrev;
    HWND m_hBtnNext;
    HWND m_hLblPage;
    HWND m_hBtnSettings;  // Settings button in toolbar

    HBITMAP m_hQrBitmap;
    HFONT m_hFont;
    HFONT m_hFontBold;
    HBRUSH m_hCompressBrush;
    bool m_compress;
    bool m_lastCompressed;
    int m_eclLevel;  // 0=L, 1=M, 2=Q, 3=H
    RECT m_qrRect;  // QR code display area

    // Multi-page support
    std::vector<QrPage> m_qrPages;   // all generated QR pages
    int m_currentPage;                 // 0-based current page index
    MultiPageAssembler m_assembler;    // for scanning multi-page QR codes

    // Floating progress window
    HWND m_hFloatWnd;
    static const wchar_t FLOAT_CLASS_NAME[];

    // Global hotkey
    static const int HOTKEY_ID = 100;
    HotkeyConfig m_hotkeyConfig;
    bool m_hotkeyRegistered;
};

} // namespace qr
