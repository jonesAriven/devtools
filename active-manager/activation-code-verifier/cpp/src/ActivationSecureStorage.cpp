#include "Jones/ActivationSecureStorage.h"
#include "Jones/ActivationCrypto.h"
#include "Jones/ActivationDeviceInfo.h"
#include <fstream>
#include <algorithm>
#include <sstream>

namespace ActivationSecureStorage {

// Same salt and IV as C# version
static const BYTE SALT[] = {
    0x4A, 0x6F, 0x6E, 0x65, 0x73, 0x41, 0x63, 0x74,
    0x69, 0x76, 0x61, 0x74, 0x69, 0x6F, 0x6E, 0x4B
};
static const size_t SALT_LEN = 16;

static const BYTE IV[] = {
    0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38,
    0x39, 0x30, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46
};
static const size_t IV_LEN = 16;

// Debug log
static void DebugLogSS(const std::string& msg) {
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
        ofs << "[" << timeBuf << "] [SS] " << msg << std::endl;
    }
}

static std::vector<BYTE> DeriveKey(const std::string& password) {
    std::vector<BYTE> pwdBytes(password.begin(), password.end());
    return ActivationCrypto::PBKDF2(pwdBytes.data(), pwdBytes.size(),
                                     SALT, SALT_LEN, 10000, 32);
}

bool Save(const std::string& filePath, const std::string& activationCode) {
    try {
        DebugLogSS("Save: path=" + filePath + " codeLen=" + std::to_string(activationCode.length()));

        std::string deviceId = ActivationDeviceInfo::GetDeviceId();
        DebugLogSS("Save: deviceId=" + deviceId);

        auto key = DeriveKey(deviceId);
        if (key.empty()) {
            DebugLogSS("Save: DeriveKey failed");
            return false;
        }

        std::vector<BYTE> plainBytes(activationCode.begin(), activationCode.end());
        auto encrypted = ActivationCrypto::AES256CBCEncrypt(key.data(), key.size(),
                                                             IV, IV_LEN,
                                                             plainBytes.data(), plainBytes.size());
        if (encrypted.empty()) {
            DebugLogSS("Save: AES encrypt failed");
            return false;
        }

        // File format: SALT + ciphertext
        std::vector<BYTE> fileData(SALT, SALT + SALT_LEN);
        fileData.insert(fileData.end(), encrypted.begin(), encrypted.end());

        std::ofstream ofs(filePath, std::ios::binary | std::ios::trunc);
        if (!ofs) {
            DebugLogSS("Save: Cannot open file for writing");
            return false;
        }
        ofs.write((const char*)fileData.data(), fileData.size());
        ofs.close();

        DebugLogSS("Save: OK, fileSize=" + std::to_string(fileData.size()));

        // Clear sensitive data
        std::fill(key.begin(), key.end(), 0);
        std::fill(plainBytes.begin(), plainBytes.end(), 0);
        return true;
    } catch (const std::exception& e) {
        DebugLogSS(std::string("Save EXCEPTION: ") + e.what());
        return false;
    } catch (...) {
        DebugLogSS("Save EXCEPTION: unknown");
        return false;
    }
}

std::string Load(const std::string& filePath) {
    try {
        DebugLogSS("Load: path=" + filePath);

        std::ifstream ifs(filePath, std::ios::binary | std::ios::ate);
        if (!ifs) {
            DebugLogSS("Load: File not found");
            return "";
        }

        auto fileSize = ifs.tellg();
        if (fileSize <= (std::streamoff)SALT_LEN) {
            DebugLogSS("Load: File too small, size=" + std::to_string((long long)fileSize));
            return "";
        }

        ifs.seekg(0, std::ios::beg);
        std::vector<BYTE> fileData((size_t)fileSize);
        if (!ifs.read((char*)fileData.data(), fileSize)) {
            DebugLogSS("Load: Read failed");
            return "";
        }
        ifs.close();

        DebugLogSS("Load: File read OK, size=" + std::to_string((long long)fileSize));

        std::string deviceId = ActivationDeviceInfo::GetDeviceId();
        DebugLogSS("Load: deviceId=" + deviceId);

        auto key = DeriveKey(deviceId);
        if (key.empty()) {
            DebugLogSS("Load: DeriveKey failed");
            return "";
        }

        // Extract ciphertext (skip salt)
        std::vector<BYTE> ciphertext(fileData.begin() + SALT_LEN, fileData.end());

        auto decrypted = ActivationCrypto::AES256CBCDecrypt(key.data(), key.size(),
                                                              IV, IV_LEN,
                                                              ciphertext.data(), ciphertext.size());

        std::fill(key.begin(), key.end(), 0);

        if (decrypted.empty()) {
            DebugLogSS("Load: AES decrypt failed");
            return "";
        }

        std::string result(decrypted.begin(), decrypted.end());
        DebugLogSS("Load: OK, codeLen=" + std::to_string(result.length()));
        return result;
    } catch (const std::exception& e) {
        DebugLogSS(std::string("Load EXCEPTION: ") + e.what());
        return "";
    } catch (...) {
        DebugLogSS("Load EXCEPTION: unknown");
        return "";
    }
}

void Delete(const std::string& filePath) {
    try {
        DebugLogSS("Delete: path=" + filePath);
        std::ifstream ifs(filePath, std::ios::binary | std::ios::ate);
        if (!ifs) return;
        auto fileSize = ifs.tellg();
        ifs.close();

        // Overwrite with zeros
        std::vector<BYTE> zeros((size_t)fileSize, 0);
        std::ofstream ofs(filePath, std::ios::binary | std::ios::in | std::ios::out);
        if (ofs) {
            ofs.write((const char*)zeros.data(), zeros.size());
            ofs.close();
        }

        // Delete file
        DeleteFileA(filePath.c_str());
    } catch (...) {}
}

} // namespace ActivationSecureStorage
