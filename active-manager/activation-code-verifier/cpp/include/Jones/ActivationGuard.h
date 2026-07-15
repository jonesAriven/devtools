#pragma once
#include "ActivationVerifyResult.h"
#include <string>
#include <functional>
#include <windows.h>

// Forward declaration
class ActivationVerifier;

// Facade: verification entry, periodic check, public key decryption, activation dialog
class ActivationGuard {
public:
    // One-stop entry: load saved code -> verify -> show dialog if needed -> start periodic check
    // Returns true if activated, false if user chose to exit
    // appVersion: caller-supplied app version (e.g. "V202607152347"), embedded into serial number
    static bool LaunchWithProtection(const std::string& initialSerial,
                                      const std::string& appVersion,
                                      int checkIntervalMs = 60000);

    // Verify with auto device ID
    static ActivationVerifyResult CheckWithAutoDevice(const std::string& activationCode);

    // Start periodic check with auto device ID
    static void StartPeriodicCheckWithAutoDevice(const std::string& activationCode,
                                                   int checkIntervalMs = 60000,
                                                   std::function<void(const std::string&)> onExpired = nullptr);

    // Stop periodic check
    static void StopPeriodicCheck();

private:
    static ActivationVerifier* CreateVerifier();
    static std::string DecryptPublicKey();
    static void ClearEncryptedKey();
    static std::string ShowActivationDialog(const std::string& initialSerial, const std::string& licPath);
    static void ShowExpiredDialog(const std::string& msg);
    static void CALLBACK PeriodicCheckCallback(PVOID lpParam, BOOLEAN TimerOrWaitFired);

    static ActivationVerifier* s_verifier;
    static std::string s_lastActivationCode;
    static std::string s_lastDeviceId;
    static std::string s_appVersion;
    static int s_checkIntervalMs;
    static std::function<void(const std::string&)> s_onExpiredCallback;
    static HANDLE s_timer;
    static HINSTANCE s_hInstance;
};
