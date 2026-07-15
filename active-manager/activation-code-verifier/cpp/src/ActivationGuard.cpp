#include "Jones/ActivationGuard.h"
#include "Jones/ActivationVerifier.h"
#include "Jones/ActivationDeviceInfo.h"
#include "Jones/ActivationSecureStorage.h"
#include "Jones/ActivationAntiDebug.h"
#include "version.h"
#include <windows.h>
#include <commctrl.h>
#include <shellapi.h>
#include <algorithm>
#include <fstream>
#include <gdiplus.h>
extern "C" {
#include <qrcodegen.h>
}

// Debug log helper for ActivationGuard
static void DebugLogGuard(const std::string& msg) {
    char path[MAX_PATH];
    GetModuleFileNameA(NULL, path, MAX_PATH);
    std::string dir(path);
    size_t pos = dir.find_last_of("\\/");
    std::string logPath = (pos != std::string::npos) ? dir.substr(0, pos) : ".";
    logPath += "\\activation_debug.log";

    std::ofstream ofs(logPath, std::ios::app);
    if (ofs) {
        SYSTEMTIME st;
        GetLocalTime(&st);
        char timeBuf[32];
        sprintf_s(timeBuf, "%02d:%02d:%02d", st.wHour, st.wMinute, st.wSecond);
        ofs << "[" << timeBuf << "] " << msg << std::endl;
    }
}

// Static member initialization
ActivationVerifier* ActivationGuard::s_verifier = nullptr;
std::string ActivationGuard::s_lastActivationCode;
std::string ActivationGuard::s_lastDeviceId;
int ActivationGuard::s_checkIntervalMs = 60000;
std::function<void(const std::string&)> ActivationGuard::s_onExpiredCallback = nullptr;
HANDLE ActivationGuard::s_timer = nullptr;
HINSTANCE ActivationGuard::s_hInstance = nullptr;

// Encrypted RSA public key (same as C# version)
// XOR key: { 0x3A, 0x7C, 0xE5, 0x91 }
static BYTE s_encryptedKey[] = {
    0x17, 0x51, 0xC8, 0xBC, 0x17, 0x3E, 0xA0, 0xD6, 0x73, 0x32,
    0xC5, 0xC1, 0x6F, 0x3E, 0xA9, 0xD8, 0x79, 0x5C, 0xAE, 0xD4,
    0x63, 0x51, 0xC8, 0xBC, 0x17, 0x51, 0xEF, 0xDC, 0x73, 0x35,
    0xA7, 0xD8, 0x50, 0x3D, 0xAB, 0xD3, 0x5D, 0x17, 0x94, 0xF9,
    0x51, 0x15, 0xA2, 0xA8, 0x4D, 0x4C, 0xA7, 0xD0, 0x6B, 0x39,
    0xA3, 0xD0, 0x7B, 0x33, 0xA6, 0xD0, 0x6B, 0x44, 0xA4, 0xDC,
    0x73, 0x35, 0xA7, 0xD2, 0x5D, 0x37, 0xA6, 0xD0, 0x6B, 0x39,
    0xA4, 0xE7, 0x0F, 0x18, 0xAD, 0xDE, 0x54, 0x4B, 0x82, 0xF9,
    0x78, 0x3B, 0x95, 0xE4, 0x7B, 0x13, 0xBC, 0xBA, 0x03, 0x2E,
    0x9D, 0x9B, 0x78, 0x0F, 0x93, 0xC8, 0x73, 0x4E, 0xA4, 0xC4,
    0x54, 0x4A, 0xB1, 0xE3, 0x72, 0x4A, 0xA7, 0xE3, 0x03, 0x1B,
    0x84, 0xE9, 0x4C, 0x4E, 0xA9, 0xE2, 0x7D, 0x26, 0x8E, 0xE3,
    0x4D, 0x29, 0xA2, 0xDB, 0x6C, 0x2D, 0xBF, 0xA9, 0x49, 0x26,
    0x9D, 0xFE, 0x53, 0x14, 0xA8, 0xA3, 0x59, 0x25, 0x92, 0xA6,
    0x6C, 0x2F, 0x95, 0xC1, 0x62, 0x29, 0x80, 0xFA, 0x60, 0x0F,
    0x97, 0xC4, 0x40, 0x37, 0xA7, 0xF4, 0x30, 0x4E, 0x84, 0xE9,
    0x54, 0x3E, 0x93, 0xA6, 0x11, 0x34, 0xD0, 0xC3, 0x63, 0x24,
    0xBC, 0xF5, 0x68, 0x38, 0xB5, 0xD6, 0x4C, 0x08, 0xA8, 0xC0,
    0x71, 0x3F, 0x81, 0xCB, 0x6B, 0x4C, 0xB2, 0xA3, 0x68, 0x34,
    0x88, 0xF9, 0x15, 0x29, 0xB0, 0xD3, 0x4D, 0x29, 0xB6, 0xD8,
    0x5F, 0x16, 0x95, 0xC8, 0x4F, 0x13, 0x89, 0xDA, 0x5D, 0x38,
    0x8A, 0xF7, 0x77, 0x1D, 0xD5, 0xDC, 0x71, 0x18, 0xB7, 0xC3,
    0x75, 0x76, 0xD1, 0xBA, 0x71, 0x4B, 0x92, 0xD0, 0x50, 0x4D,
    0xA2, 0xD6, 0x43, 0x16, 0xA9, 0xC4, 0x76, 0x4F, 0xA2, 0xEB,
    0x59, 0x31, 0x82, 0xDA, 0x4D, 0x17, 0xD7, 0xBE, 0x58, 0x0E,
    0x82, 0xE3, 0x54, 0x26, 0xB4, 0xE8, 0x5F, 0x36, 0x95, 0xA5,
    0x52, 0x3D, 0x87, 0xFA, 0x76, 0x49, 0x8D, 0xC9, 0x4D, 0x19,
    0xD2, 0xDD, 0x03, 0x11, 0xB7, 0xF5, 0x08, 0x0F, 0x9C, 0xD9,
    0x02, 0x4E, 0x87, 0xC1, 0x7E, 0x32, 0xEF, 0xDE, 0x02, 0x57,
    0xA2, 0xE9, 0x0F, 0x31, 0xA1, 0xC9, 0x0E, 0x1E, 0xAD, 0xE9,
    0x50, 0x14, 0x93, 0xDE, 0x4F, 0x04, 0x8E, 0xD2, 0x5E, 0x04,
    0xD5, 0xD2, 0x43, 0x49, 0x86, 0xEB, 0x0D, 0x39, 0x80, 0xF4,
    0x0E, 0x3D, 0xBF, 0xA2, 0x5C, 0x0A, 0xA4, 0xC1, 0x08, 0x0E,
    0xD2, 0xBA, 0x49, 0x13, 0x84, 0xA2, 0x79, 0x24, 0xB6, 0xE8,
    0x6D, 0x53, 0xD0, 0xE5, 0x4A, 0x11, 0x9F, 0xC7, 0x4F, 0x44,
    0xB3, 0x9B, 0x51, 0x29, 0x95, 0xE3, 0x09, 0x4F, 0xDC, 0xFA,
    0x4A, 0x3E, 0xD2, 0xBA, 0x0E, 0x3D, 0x83, 0xBE, 0x0A, 0x4B,
    0x87, 0xC7, 0x57, 0x26, 0x91, 0xDB, 0x0B, 0x57, 0xAA, 0xE2,
    0x4E, 0x10, 0x95, 0xD5, 0x57, 0x04, 0xB3, 0xC6, 0x7D, 0x39,
    0x92, 0xE4, 0x5D, 0x2F, 0xBF, 0xFE, 0x55, 0x15, 0xA6, 0xE0,
    0x53, 0x2B, 0xA4, 0xFE, 0x40, 0x36, 0xA8, 0xA1, 0x77, 0x1F,
    0xAD, 0xE0, 0x0A, 0x18, 0x97, 0xA7, 0x30, 0x1F, 0xB4, 0xD8,
    0x7E, 0x3D, 0xB4, 0xD0, 0x78, 0x76, 0xC8, 0xBC, 0x17, 0x51,
    0xC8, 0xD4, 0x74, 0x38, 0xC5, 0xC1, 0x6F, 0x3E, 0xA9, 0xD8,
    0x79, 0x5C, 0xAE, 0xD4, 0x63, 0x51, 0xC8, 0xBC, 0x17, 0x51
};

static const BYTE XOR_KEYS[] = { 0x3A, 0x7C, 0xE5, 0x91 };
static const size_t XOR_KEYS_LEN = 4;

static std::string GetExeDir() {
    char path[MAX_PATH];
    GetModuleFileNameA(NULL, path, MAX_PATH);
    std::string s(path);
    size_t pos = s.find_last_of("\\/");
    return (pos != std::string::npos) ? s.substr(0, pos) : ".";
}

// --- Public methods ---

bool ActivationGuard::LaunchWithProtection(const std::string& initialSerial, int checkIntervalMs) {
    s_hInstance = GetModuleHandle(NULL);
    std::string licPath = GetExeDir() + "\\activation.dat";

    // Try to load saved activation code
    std::string savedCode = ActivationSecureStorage::Load(licPath);

    if (!savedCode.empty()) {
        ActivationVerifyResult result = CheckWithAutoDevice(savedCode);

        if (result.success && !result.expired && !result.deviceMismatch) {
            StartPeriodicCheckWithAutoDevice(savedCode, checkIntervalMs, [licPath](const std::string&) {
                ActivationSecureStorage::Delete(licPath);
                ShowExpiredDialog("");
            });
            return true;
        }

        if (result.expired || result.deviceMismatch) {
            ActivationSecureStorage::Delete(licPath);
        }
    }

    // Show activation dialog
    std::string activationCode = ShowActivationDialog(initialSerial, licPath);

    if (activationCode.empty()) {
        return false;
    }

    StartPeriodicCheckWithAutoDevice(activationCode, checkIntervalMs, [licPath](const std::string&) {
        ActivationSecureStorage::Delete(licPath);
        ShowExpiredDialog("");
    });

    return true;
}

ActivationVerifyResult ActivationGuard::CheckWithAutoDevice(const std::string& activationCode) {
    std::string deviceId = ActivationDeviceInfo::GetDeviceId();

    if (!s_verifier) {
        s_verifier = CreateVerifier();
    }
    if (!s_verifier) return ActivationVerifyResult::Fail();

    return s_verifier->Verify(activationCode, deviceId);
}

void ActivationGuard::StartPeriodicCheckWithAutoDevice(const std::string& activationCode,
                                                         int checkIntervalMs,
                                                         std::function<void(const std::string&)> onExpired) {
    s_lastActivationCode = activationCode;
    s_lastDeviceId = ActivationDeviceInfo::GetDeviceId();
    s_checkIntervalMs = (checkIntervalMs > 0) ? checkIntervalMs : 60000;
    s_onExpiredCallback = onExpired;

    StopPeriodicCheck();

    CreateTimerQueueTimer(&s_timer, NULL, PeriodicCheckCallback, NULL,
                          s_checkIntervalMs, s_checkIntervalMs, 0);
}

void ActivationGuard::StopPeriodicCheck() {
    if (s_timer) {
        DeleteTimerQueueTimer(NULL, s_timer, NULL);
        s_timer = nullptr;
    }
}

// --- Private methods ---

ActivationVerifier* ActivationGuard::CreateVerifier() {
    std::string publicKeyPem = DecryptPublicKey();
    if (publicKeyPem.empty()) return nullptr;

    ClearEncryptedKey();

    return new ActivationVerifier(publicKeyPem);
}

std::string ActivationGuard::DecryptPublicKey() {
    size_t len = sizeof(s_encryptedKey);
    std::vector<char> decrypted(len + 1);
    for (size_t i = 0; i < len; i++) {
        decrypted[i] = (char)(s_encryptedKey[i] ^ XOR_KEYS[i % XOR_KEYS_LEN]);
    }
    decrypted[len] = '\0';
    std::string result(decrypted.data(), len);
    
    // Debug log
    std::ofstream debugLog("activation_debug.log", std::ios::app);
    if (debugLog) {
        SYSTEMTIME st;
        GetLocalTime(&st);
        debugLog << "[" << st.wHour << ":" << st.wMinute << ":" << st.wSecond << "] "
                 << "DecryptPublicKey: len=" << len 
                 << ", result_len=" << result.length()
                 << ", first80=" << result.substr(0, 80) << std::endl;
    }
    
    return result;
}

void ActivationGuard::ClearEncryptedKey() {
    for (size_t i = 0; i < sizeof(s_encryptedKey); i++) {
        s_encryptedKey[i] = 0;
    }
}

void CALLBACK ActivationGuard::PeriodicCheckCallback(PVOID lpParam, BOOLEAN TimerOrWaitFired) {
    if (s_lastActivationCode.empty()) return;

    try {
        if (!s_verifier) return;

        ActivationVerifyResult result = s_verifier->Verify(s_lastActivationCode, s_lastDeviceId);

        if (!result.success || result.expired || result.deviceMismatch) {
            StopPeriodicCheck();

            if (s_onExpiredCallback) {
                s_onExpiredCallback("");
            } else {
                ExitProcess(1002);
            }
        }
    } catch (...) {}
}

void ActivationGuard::ShowExpiredDialog(const std::string& msg) {
    MessageBoxW(NULL, L"授权已失效，程序即将退出。", L"授权验证", MB_OK | MB_ICONWARNING);
    ExitProcess(1002);
}

// --- Activation Dialog ---

#define ID_TXT_SERIAL   2001
#define ID_BTN_COPY_SN  2002
#define ID_TXT_CODE     2003
#define ID_BTN_ACTIVATE 2004
#define ID_BTN_EXIT     2005
#define ID_BTN_COPY_URL 2006
#define ID_LNK_URL      2007
#define ID_STATIC_QR    2008

// URL-encode a string for use in query parameters
static std::string UrlEncode(const std::string& value) {
    std::string encoded;
    for (unsigned char c : value) {
        if (isalnum(c) || c == '-' || c == '_' || c == '.' || c == '~') {
            encoded += c;
        } else {
            char buf[4];
            sprintf_s(buf, "%%%02X", c);
            encoded += buf;
        }
    }
    return encoded;
}

// Generate QR code HBITMAP from text string (using C API)
static HBITMAP GenerateQrBitmap(const std::string& text, int pixelSize = 4, int border = 4) {
    // Encode text to QR code using C API
    uint8_t qrcode[qrcodegen_BUFFER_LEN_MAX];
    uint8_t tempBuffer[qrcodegen_BUFFER_LEN_MAX];
    if (!qrcodegen_encodeText(text.c_str(), tempBuffer, qrcode, qrcodegen_Ecc_LOW,
                               qrcodegen_VERSION_MIN, qrcodegen_VERSION_MAX, qrcodegen_Mask_AUTO, true)) {
        return NULL;
    }

    int size = qrcodegen_getSize(qrcode);
    int imgSize = (size + border * 2) * pixelSize;

    // Create DIB section
    BITMAPINFO bmi = {};
    bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bmi.bmiHeader.biWidth = imgSize;
    bmi.bmiHeader.biHeight = -imgSize; // top-down
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = 32;
    bmi.bmiHeader.biCompression = BI_RGB;

    HDC hdcScreen = GetDC(NULL);
    HDC hdcMem = CreateCompatibleDC(hdcScreen);

    VOID* pBits = nullptr;
    HBITMAP hBmp = CreateDIBSection(hdcScreen, &bmi, DIB_RGB_COLORS, &pBits, NULL, 0);

    if (hBmp && pBits) {
        DWORD* pixels = (DWORD*)pBits;
        // Fill white background
        for (int i = 0; i < imgSize * imgSize; i++) {
            pixels[i] = 0x00FFFFFF;
        }
        // Draw QR modules (black)
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (qrcodegen_getModule(qrcode, x, y)) {
                    for (int dy = 0; dy < pixelSize; dy++) {
                        for (int dx = 0; dx < pixelSize; dx++) {
                            int px = (x + border) * pixelSize + dx;
                            int py = (y + border) * pixelSize + dy;
                            pixels[py * imgSize + px] = 0x00000000;
                        }
                    }
                }
            }
        }
    }

    DeleteDC(hdcMem);
    ReleaseDC(NULL, hdcScreen);
    return hBmp;
}

static std::string g_serialNumber;
static std::string g_activatedCode;
static bool g_activated = false;
static bool g_exitApp = false;

// Subclass proc for Edit controls to support Ctrl+A select all
static LRESULT CALLBACK EditSubclassProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam,
                                          UINT_PTR uIdSubclass, DWORD_PTR dwRefData) {
    if (msg == WM_CHAR && wParam == 1) { // Ctrl+A
        SendMessage(hWnd, EM_SETSEL, 0, -1);
        return 0;
    }
    return DefSubclassProc(hWnd, msg, wParam, lParam);
}

static LRESULT CALLBACK ActivationDlgProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_CREATE: {
        HFONT hFont = CreateFontW(14, 0, 0, 0, FW_NORMAL, 0, 0, 0,
                                   DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"微软雅黑");
        HFONT hFontBold = CreateFontW(16, 0, 0, 0, FW_BOLD, 0, 0, 0,
                                        DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"微软雅黑");
        HFONT hFontMono = CreateFontW(13, 0, 0, 0, FW_NORMAL, 0, 0, 0,
                                        DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"Consolas");
        HFONT hFontSmall = CreateFontW(12, 0, 0, 0, FW_NORMAL, 0, 0, 0,
                                         DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, 0, L"微软雅黑");

        // --- Left side: form controls ---
        const int leftW = 420;
        const int mx = 20; // left margin

        // Title
        HWND hTitle = CreateWindowW(L"STATIC", L"请输入激活码", WS_CHILD | WS_VISIBLE,
                      mx, 12, leftW, 25, hWnd, NULL, NULL, NULL);
        SendMessage(hTitle, WM_SETFONT, (WPARAM)hFontBold, TRUE);

        // Serial number label
        HWND hLblSN = CreateWindowW(L"STATIC", L"唯一序列号:", WS_CHILD | WS_VISIBLE,
                                     mx, 45, 100, 20, hWnd, NULL, NULL, NULL);
        SendMessage(hLblSN, WM_SETFONT, (WPARAM)hFont, TRUE);

        // Serial number text (read-only)
        std::wstring wSerial(g_serialNumber.begin(), g_serialNumber.end());
        HWND hTxtSN = CreateWindowW(L"EDIT", wSerial.c_str(),
                                     WS_CHILD | WS_VISIBLE | WS_BORDER | ES_AUTOHSCROLL | ES_READONLY,
                                     mx, 67, leftW, 25, hWnd, (HMENU)ID_TXT_SERIAL, NULL, NULL);
        SendMessage(hTxtSN, WM_SETFONT, (WPARAM)hFontMono, TRUE);
        SendMessage(hTxtSN, EM_SETREADONLY, TRUE, 0);
        SetWindowSubclass(hTxtSN, EditSubclassProc, 0, 0);

        // Copy serial button
        HWND hBtnCopySN = CreateWindowW(L"BUTTON", L"复制序列号",
                                         WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
                                         mx, 96, 100, 24, hWnd, (HMENU)ID_BTN_COPY_SN, NULL, NULL);
        SendMessage(hBtnCopySN, WM_SETFONT, (WPARAM)hFont, TRUE);

        // Activation code label
        HWND hLblCode = CreateWindowW(L"STATIC", L"激活码:", WS_CHILD | WS_VISIBLE,
                                       mx, 128, 100, 20, hWnd, NULL, NULL, NULL);
        SendMessage(hLblCode, WM_SETFONT, (WPARAM)hFont, TRUE);

        // Activation code input (multi-line)
        HWND hTxtCode = CreateWindowW(L"EDIT", L"",
                                       WS_CHILD | WS_VISIBLE | WS_BORDER | ES_MULTILINE |
                                       ES_AUTOVSCROLL | WS_VSCROLL,
                                       mx, 150, leftW, 60, hWnd, (HMENU)ID_TXT_CODE, NULL, NULL);
        SendMessage(hTxtCode, WM_SETFONT, (WPARAM)hFontMono, TRUE);
        SetWindowSubclass(hTxtCode, EditSubclassProc, 0, 0);

        // Hint text
        HWND hLblHint = CreateWindowW(L"STATIC", L"请将上方唯一序列号发给管理员，获取激活码后粘贴到上方输入框",
                                       WS_CHILD | WS_VISIBLE,
                                       mx, 216, leftW, 20, hWnd, NULL, NULL, NULL);
        SendMessage(hLblHint, WM_SETFONT, (WPARAM)hFont, TRUE);

        // URL label
        HWND hLblUrl = CreateWindowW(L"STATIC", L"获取激活码：",
                                      WS_CHILD | WS_VISIBLE,
                                      mx, 240, leftW, 20, hWnd, NULL, NULL, NULL);
        SendMessage(hLblUrl, WM_SETFONT, (WPARAM)hFont, TRUE);

        // URL link
        HWND hLnkUrl = CreateWindowW(L"STATIC", L"https://tools.marschat.online/activecode/index.html",
                                      WS_CHILD | WS_VISIBLE | SS_NOTIFY,
                                      mx, 260, leftW, 20, hWnd, (HMENU)ID_LNK_URL, NULL, NULL);
        SendMessage(hLnkUrl, WM_SETFONT, (WPARAM)hFont, TRUE);

        // Copy URL button
        HWND hBtnCopyUrl = CreateWindowW(L"BUTTON", L"复制地址",
                                          WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
                                          mx, 284, 100, 22, hWnd, (HMENU)ID_BTN_COPY_URL, NULL, NULL);
        SendMessage(hBtnCopyUrl, WM_SETFONT, (WPARAM)hFont, TRUE);

        // Activate button
        HWND hBtnActivate = CreateWindowW(L"BUTTON", L"激活",
                                           WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
                                           mx + leftW - 170, 320, 80, 28, hWnd, (HMENU)ID_BTN_ACTIVATE, NULL, NULL);
        SendMessage(hBtnActivate, WM_SETFONT, (WPARAM)hFont, TRUE);

        // Exit button
        HWND hBtnExit = CreateWindowW(L"BUTTON", L"退出",
                                       WS_CHILD | WS_VISIBLE | BS_PUSHBUTTON,
                                       mx + leftW - 80, 320, 80, 28, hWnd, (HMENU)ID_BTN_EXIT, NULL, NULL);
        SendMessage(hBtnExit, WM_SETFONT, (WPARAM)hFont, TRUE);

        // --- Right side: QR code ---
        const int qrX = mx + leftW + 20;
        const int qrY = 20;
        const int qrDisplaySize = 160;

        // Generate QR code with URL + serial number
        std::string qrUrl = std::string("https://tools.marschat.online/activecode/index.html?sn=") + UrlEncode(g_serialNumber);
        HBITMAP hQrBmp = GenerateQrBitmap(qrUrl, 3, 4);
        // Store bitmap handle for later cleanup
        SetPropW(hWnd, L"QrBitmap", reinterpret_cast<HANDLE>(hQrBmp));

        // QR code static control (uses WM_SETIMAGE with STM_IMAGE)
        HWND hStaticQr = CreateWindowW(L"STATIC", NULL,
                                        WS_CHILD | WS_VISIBLE | SS_BITMAP | SS_CENTERIMAGE,
                                        qrX, qrY, qrDisplaySize, qrDisplaySize,
                                        hWnd, (HMENU)ID_STATIC_QR, NULL, NULL);
        if (hQrBmp) {
            SendMessage(hStaticQr, STM_SETIMAGE, IMAGE_BITMAP, (LPARAM)hQrBmp);
        }

        // QR hint text
        HWND hLblQrHint = CreateWindowW(L"STATIC", L"微信扫码获取激活码",
                                          WS_CHILD | WS_VISIBLE | SS_CENTER,
                                          qrX, qrY + qrDisplaySize + 6, qrDisplaySize, 20,
                                          hWnd, NULL, NULL, NULL);
        SendMessage(hLblQrHint, WM_SETFONT, (WPARAM)hFontSmall, TRUE);

        break;
    }

    case WM_SIZE: {
        // Reposition controls when window is resized
        int cx = LOWORD(lParam);
        int cy = HIWORD(lParam);
        int margin = 20;
        int leftW = 420;

        // Left side controls: fixed width, reposition vertically
        struct LayoutInfo {
            int id;
            int y;
            int height;
            int width;  // 0 = full leftW, -1 = auto, >0 = fixed
        };
        LayoutInfo layout[] = {
            { -1,           12,  25, 0 },   // Title
            { -1,           45,  20, -1 },  // "唯一序列号:" label
            { ID_TXT_SERIAL,67,  25, 0 },   // Serial number text
            { ID_BTN_COPY_SN,96, 24, 100 }, // Copy serial button
            { -1,           128, 20, -1 },  // "激活码:" label
            { ID_TXT_CODE,  150, 60, 0 },   // Activation code input
            { -1,           216, 20, 0 },   // Hint text
            { -1,           240, 20, 0 },   // "获取激活码：" label
            { ID_LNK_URL,   260, 20, 0 },   // URL link
            { ID_BTN_COPY_URL,284,22, 100 },// Copy URL button
            { ID_BTN_ACTIVATE,320,28, 80 }, // Activate button
            { ID_BTN_EXIT,  320, 28, 80 },  // Exit button
        };

        HWND hChild = GetWindow(hWnd, GW_CHILD);
        int idx = 0;
        int totalItems = sizeof(layout) / sizeof(layout[0]);
        while (hChild && idx < totalItems) {
            int id = GetDlgCtrlID(hChild);
            LayoutInfo& li = layout[idx];

            int x = margin;
            int ctrlW, ctrlH = li.height;

            if (li.width == 0) {
                ctrlW = leftW;
            } else if (li.width == -1) {
                RECT rc;
                GetWindowRect(hChild, &rc);
                ctrlW = rc.right - rc.left;
            } else {
                ctrlW = li.width;
            }

            // Special positioning for bottom buttons
            if (id == ID_BTN_ACTIVATE) {
                x = margin + leftW - 170;
            } else if (id == ID_BTN_EXIT) {
                x = margin + leftW - 80;
            }

            // Activation code input stretches vertically
            if (id == ID_TXT_CODE) {
                ctrlH = std::max(60, cy - li.y - 120);
            }

            MoveWindow(hChild, x, li.y, ctrlW, ctrlH, TRUE);
            hChild = GetWindow(hChild, GW_HWNDNEXT);
            idx++;
        }

        // Right side: QR code and hint - reposition based on window size
        HWND hQr = GetDlgItem(hWnd, ID_STATIC_QR);
        if (hQr) {
            int qrX = margin + leftW + 20;
            int qrY = 20;
            int qrSize = 160;
            MoveWindow(hQr, qrX, qrY, qrSize, qrSize, TRUE);
        }
        // QR hint label (last child, no ID)
        // Find it by iterating remaining children
        while (hChild) {
            int id = GetDlgCtrlID(hChild);
            if (id == 0 || id == ID_STATIC_QR) {
                // QR hint or QR static - position it
                if (id == 0) {
                    // This is the QR hint label
                    int qrX = margin + leftW + 20;
                    MoveWindow(hChild, qrX, 20 + 160 + 6, 160, 20, TRUE);
                }
            }
            hChild = GetWindow(hChild, GW_HWNDNEXT);
        }
        break;
    }

    case WM_CTLCOLORSTATIC: {
        HDC hdc = (HDC)wParam;
        HWND hCtrl = (HWND)lParam;

        // Serial number text box gray background
        if (GetDlgCtrlID(hCtrl) == ID_TXT_SERIAL) {
            SetBkColor(hdc, RGB(240, 240, 240));
            return (LRESULT)GetSysColorBrush(COLOR_BTNFACE);
        }

        // URL link blue color
        if (GetDlgCtrlID(hCtrl) == ID_LNK_URL) {
            SetTextColor(hdc, RGB(0, 120, 212));
            SetBkMode(hdc, TRANSPARENT);
            return (LRESULT)GetStockObject(NULL_BRUSH);
        }

        break;
    }

    case WM_COMMAND: {
        int id = LOWORD(wParam);

        if (id == ID_BTN_COPY_SN) {
            // Copy serial number to clipboard
            std::wstring wSerial(g_serialNumber.begin(), g_serialNumber.end());
            if (OpenClipboard(hWnd)) {
                EmptyClipboard();
                HGLOBAL hMem = GlobalAlloc(GMEM_MOVEABLE, (wSerial.size() + 1) * sizeof(wchar_t));
                if (hMem) {
                    wcscpy_s((wchar_t*)GlobalLock(hMem), wSerial.size() + 1, wSerial.c_str());
                    GlobalUnlock(hMem);
                    SetClipboardData(CF_UNICODETEXT, hMem);
                }
                CloseClipboard();
            }
            SetDlgItemTextW(hWnd, ID_BTN_COPY_SN, L"已复制");
        }
        else if (id == ID_BTN_COPY_URL) {
            const char* url = "https://tools.marschat.online/activecode/index.html";
            std::wstring wUrl(url, url + strlen(url));
            if (OpenClipboard(hWnd)) {
                EmptyClipboard();
                HGLOBAL hMem = GlobalAlloc(GMEM_MOVEABLE, (wUrl.size() + 1) * sizeof(wchar_t));
                if (hMem) {
                    wcscpy_s((wchar_t*)GlobalLock(hMem), wUrl.size() + 1, wUrl.c_str());
                    GlobalUnlock(hMem);
                    SetClipboardData(CF_UNICODETEXT, hMem);
                }
                CloseClipboard();
            }
            SetDlgItemTextW(hWnd, ID_BTN_COPY_URL, L"已复制");
        }
        else if (id == ID_LNK_URL) {
            ShellExecuteA(NULL, "open", "https://tools.marschat.online/activecode/index.html", NULL, NULL, SW_SHOWNORMAL);
        }
        else if (id == ID_BTN_ACTIVATE) {
            // Get activation code from text box
            wchar_t wCode[2048] = {};
            GetDlgItemTextW(hWnd, ID_TXT_CODE, wCode, 2048);

            // Trim whitespace
            std::string code;
            for (wchar_t* p = wCode; *p; p++) {
                if (*p != L' ' && *p != L'\r' && *p != L'\n' && *p != L'\t') {
                    code += (char)*p;
                }
            }

            if (code.empty()) {
                MessageBoxW(hWnd, L"请输入激活码", L"提示", MB_OK | MB_ICONWARNING);
                break;
            }

            ActivationVerifyResult result = ActivationGuard::CheckWithAutoDevice(code);
            DebugLogGuard("Dialog: CheckWithAutoDevice result: success=" + std::to_string(result.success) +
                           " expired=" + std::to_string(result.expired) +
                           " mismatch=" + std::to_string(result.deviceMismatch));

            if (result.success && !result.expired && !result.deviceMismatch) {
                std::string licPath = GetExeDir() + "\\activation.dat";
                bool saved = ActivationSecureStorage::Save(licPath, code);
                DebugLogGuard("SecureStorage::Save result: " + std::to_string(saved) + " path=" + licPath);

                // Use NULL parent so MessageBox is not affected by DestroyWindow
                MessageBoxW(NULL, L"激活成功！", L"授权验证", MB_OK | MB_ICONINFORMATION);
                g_activated = true;
                g_activatedCode = code;
                DestroyWindow(hWnd);
            } else {
                std::wstring wMsg;
                if (result.deviceMismatch) {
                    std::string deviceId = ActivationDeviceInfo::GetDeviceId();
                    wMsg = L"设备不匹配，此激活码已绑定其他设备。\n\n设备ID: " +
                           std::wstring(deviceId.begin(), deviceId.end()) +
                           L"\n激活码绑定: " +
                           std::wstring(result.deviceId.begin(), result.deviceId.end());
                } else if (result.expired) {
                    wMsg = L"激活码已过期，请联系管理员续期。";
                } else {
                    std::string deviceId = ActivationDeviceInfo::GetDeviceId();
                    std::string machineCode = ActivationDeviceInfo::GetMachineCode();
                    wMsg = L"激活码无效，请检查是否输入正确。\n\n[调试信息]\n设备ID: " +
                           std::wstring(deviceId.begin(), deviceId.end()) +
                           L"\n机器码: " +
                           std::wstring(machineCode.begin(), machineCode.end()) +
                           L"\n序列号: " +
                           std::wstring(g_serialNumber.begin(), g_serialNumber.end());
                }
                MessageBoxW(hWnd, wMsg.c_str(), L"激活失败", MB_OK | MB_ICONERROR);
            }
        }
        else if (id == ID_BTN_EXIT) {
            g_exitApp = true;
            DestroyWindow(hWnd);
        }
        break;
    }

    case WM_DESTROY: {
        // Clean up QR bitmap
        HBITMAP hQrBmp = (HBITMAP)GetPropW(hWnd, L"QrBitmap");
        if (hQrBmp) {
            DeleteObject(hQrBmp);
            RemovePropW(hWnd, L"QrBitmap");
        }
        break;
    }

    case WM_CLOSE:
        g_exitApp = true;
        DestroyWindow(hWnd);
        break;

    default:
        return DefWindowProcW(hWnd, msg, wParam, lParam);
    }
    return 0;
}

std::string ActivationGuard::ShowActivationDialog(const std::string& initialSerial, const std::string& licPath) {
    g_serialNumber = ActivationDeviceInfo::GetSerialNumber(initialSerial, APP_VERSION);
    g_activatedCode.clear();
    g_activated = false;
    g_exitApp = false;

    // Register window class
    static bool registered = false;
    if (!registered) {
        WNDCLASSEXW wc = {};
        wc.cbSize = sizeof(wc);
        wc.style = CS_HREDRAW | CS_VREDRAW;
        wc.lpfnWndProc = ActivationDlgProc;
        wc.hInstance = s_hInstance;
        wc.hCursor = LoadCursor(NULL, IDC_ARROW);
        wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
        wc.lpszClassName = L"ActivationDialog";
        RegisterClassExW(&wc);
        registered = true;
    }

    // Create popup window (resizable with thick frame)
    HWND hDlg = CreateWindowExW(0, L"ActivationDialog", L"软件激活",
                                 WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_THICKFRAME | WS_MINIMIZEBOX | WS_MAXIMIZEBOX,
                                 CW_USEDEFAULT, CW_USEDEFAULT, 660, 380,
                                 NULL, NULL, s_hInstance, NULL);

    ShowWindow(hDlg, SW_SHOW);
    UpdateWindow(hDlg);

    // Modal message loop
    MSG msg;
    while (GetMessageW(&msg, NULL, 0, 0)) {
        if (!IsWindow(hDlg)) break;
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    if (g_activated) {
        return g_activatedCode;
    }

    return "";
}
