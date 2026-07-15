#pragma once
#include <string>
#include <windows.h>

namespace Jones {

// 版本信息
static const char* APP_VERSION = "2.0.0";
static const char* APP_NAME = "QRCodeTool";
static const char* UPDATE_URL = "https://tools.marschat.online/activecode/tool.html";

// 版本检测和提示
class VersionCheck {
public:
    // 检查激活验证失败时的版本兼容性
    static bool CheckActivationCompatibility(const std::string& errorDetails);
    
    // 显示友好的更新提示对话框
    static void ShowUpdateDialog(HWND hwndParent, const std::string& message);
    
    // 显示版本不匹配提示
    static void ShowVersionMismatchDialog(HWND hwndParent);
    
    // 获取当前版本字符串
    static const char* GetCurrentVersion() { return APP_VERSION; }
    
    // 检查是否需要更新（根据激活失败模式判断）
    static bool IsUpdateRecommended(DWORD cryptoErrorCode);
};

} // namespace Jones
