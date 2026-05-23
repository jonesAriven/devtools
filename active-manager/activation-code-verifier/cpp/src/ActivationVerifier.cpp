#include "Jones/ActivationVerifier.h"
#include "Jones/ActivationAntiDebug.h"
#include "Jones/ActivationTimeGuard.h"
#include "Jones/ActivationCrypto.h"
#include <wincrypt.h>
#include <algorithm>
#include <cstring>
#include <sstream>
#include <fstream>

#pragma comment(lib, "crypt32.lib")
#pragma comment(lib, "advapi32.lib")

// Debug log helper - writes to activation_debug.log in exe directory
static void DebugLog(const std::string& msg) {
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

ActivationVerifier::ActivationVerifier(const std::string& publicKeyPem)
    : m_publicKeyPem(publicKeyPem) {
    DebugLog("Verifier created, PEM length: " + std::to_string(publicKeyPem.length()));
    DebugLog("PEM first 80 chars: " + publicKeyPem.substr(0, 80));
}

ActivationVerifier::~ActivationVerifier() {
}

ActivationVerifyResult ActivationVerifier::Verify(const std::string& activationCode) {
    return Verify(activationCode, "");
}

ActivationVerifyResult ActivationVerifier::Verify(const std::string& activationCode, const std::string& expectedDeviceId) {
    DebugLog("=== Verify started ===");
    DebugLog("Activation code length: " + std::to_string(activationCode.length()));
    DebugLog("Expected device ID: " + expectedDeviceId);

    // Anti-debug check - DISABLED for debugging
    // if (ActivationAntiDebug::IsBeingDebugged()) {
    //     DebugLog("FAIL: Anti-debug detected");
    //     return ActivationVerifyResult::Fail();
    // }

    std::vector<BYTE> payloadBytes;
    std::vector<BYTE> signatureBytes;

    try {
        if (activationCode.empty()) {
            DebugLog("FAIL: empty activation code");
            return ActivationVerifyResult::Fail();
        }

        // Split by '.'
        size_t dotPos = activationCode.find('.');
        if (dotPos == std::string::npos) {
            DebugLog("FAIL: no '.' separator found");
            return ActivationVerifyResult::Fail();
        }

        std::string payloadB64 = activationCode.substr(0, dotPos);
        std::string sigB64 = activationCode.substr(dotPos + 1);
        DebugLog("Payload B64 length: " + std::to_string(payloadB64.length()));
        DebugLog("Signature B64 length: " + std::to_string(sigB64.length()));

        payloadBytes = ActivationCrypto::Base64UrlDecode(payloadB64);
        signatureBytes = ActivationCrypto::Base64UrlDecode(sigB64);

        DebugLog("Payload decoded length: " + std::to_string(payloadBytes.size()));
        DebugLog("Signature decoded length: " + std::to_string(signatureBytes.size()));

        if (payloadBytes.empty() || signatureBytes.empty()) {
            DebugLog("FAIL: decoded payload or signature is empty");
            return ActivationVerifyResult::Fail();
        }

        // Parse payload
        std::string payload(payloadBytes.begin(), payloadBytes.end());
        DebugLog("Payload text: " + payload);

        std::vector<std::string> parts;
        std::istringstream iss(payload);
        std::string part;
        while (std::getline(iss, part, '|')) {
            parts.push_back(part);
        }

        DebugLog("Payload parts count: " + std::to_string(parts.size()));

        std::string serialNumber;
        std::string deviceId;
        int64_t expireTimestamp = 0;

        if (parts.size() == 2) {
            serialNumber = parts[0];
            deviceId = "";
            expireTimestamp = _atoi64(parts[1].c_str());
        } else if (parts.size() == 3) {
            serialNumber = parts[0];
            deviceId = parts[1];
            expireTimestamp = _atoi64(parts[2].c_str());
        } else {
            DebugLog("FAIL: unexpected payload parts count");
            return ActivationVerifyResult::Fail();
        }

        DebugLog("SerialNumber: " + serialNumber);
        DebugLog("DeviceId: " + deviceId);
        DebugLog("ExpireTimestamp: " + std::to_string(expireTimestamp));

        if (expireTimestamp == 0) {
            DebugLog("FAIL: expireTimestamp is 0");
            return ActivationVerifyResult::Fail();
        }

        // RSA signature verification using CryptoAPI
        bool verified = false;

        // 1. Acquire crypto context
        HCRYPTPROV hProv = 0;
        if (!CryptAcquireContext(&hProv, NULL, MS_ENH_RSA_AES_PROV, PROV_RSA_AES, CRYPT_VERIFYCONTEXT)) {
            DWORD err = GetLastError();
            DebugLog("FAIL: CryptAcquireContext error=" + std::to_string(err));
            return ActivationVerifyResult::Fail();
        }
        DebugLog("CryptAcquireContext OK");

        // 2. Decode PEM to DER
        std::string pem = m_publicKeyPem;
        std::string base64Content;
        std::istringstream pemStream(pem);
        std::string line;
        while (std::getline(pemStream, line)) {
            if (line.find("-----") != std::string::npos) continue;
            base64Content += line;
        }
        DebugLog("PEM base64 content length: " + std::to_string(base64Content.length()));

        DWORD derLen = 0;
        if (!CryptStringToBinaryA(base64Content.c_str(), (DWORD)base64Content.length(),
                             CRYPT_STRING_BASE64, NULL, &derLen, NULL, NULL)) {
            DWORD err = GetLastError();
            DebugLog("FAIL: CryptStringToBinary (size query) error=" + std::to_string(err));
            CryptReleaseContext(hProv, 0);
            return ActivationVerifyResult::Fail();
        }
        DebugLog("DER length: " + std::to_string(derLen));

        std::vector<BYTE> derBuf(derLen);
        if (!CryptStringToBinaryA(base64Content.c_str(), (DWORD)base64Content.length(),
                             CRYPT_STRING_BASE64, derBuf.data(), &derLen, NULL, NULL)) {
            DWORD err = GetLastError();
            DebugLog("FAIL: CryptStringToBinary (decode) error=" + std::to_string(err));
            CryptReleaseContext(hProv, 0);
            return ActivationVerifyResult::Fail();
        }
        DebugLog("PEM->DER decode OK");

        // 3. Decode DER to CERT_PUBLIC_KEY_INFO
        DWORD keyInfoLen = 0;
        if (!CryptDecodeObjectEx(X509_ASN_ENCODING, X509_PUBLIC_KEY_INFO,
                            derBuf.data(), derLen, 0, NULL, NULL, &keyInfoLen)) {
            DWORD err = GetLastError();
            DebugLog("FAIL: CryptDecodeObjectEx (size query) error=" + std::to_string(err));
            CryptReleaseContext(hProv, 0);
            return ActivationVerifyResult::Fail();
        }

        std::vector<BYTE> keyInfoBuf(keyInfoLen);
        CERT_PUBLIC_KEY_INFO* pKeyInfo = (CERT_PUBLIC_KEY_INFO*)keyInfoBuf.data();
        if (!CryptDecodeObjectEx(X509_ASN_ENCODING, X509_PUBLIC_KEY_INFO,
                            derBuf.data(), derLen, 0, NULL, keyInfoBuf.data(), &keyInfoLen)) {
            DWORD err = GetLastError();
            DebugLog("FAIL: CryptDecodeObjectEx (decode) error=" + std::to_string(err));
            CryptReleaseContext(hProv, 0);
            return ActivationVerifyResult::Fail();
        }
        DebugLog("DER->CERT_PUBLIC_KEY_INFO OK, size=" + std::to_string(keyInfoLen));

        // 4. Import public key
        HCRYPTKEY hKey = 0;
        if (!CryptImportPublicKeyInfo(hProv, X509_ASN_ENCODING, pKeyInfo, &hKey)) {
            DWORD err = GetLastError();
            DebugLog("FAIL: CryptImportPublicKeyInfo error=" + std::to_string(err));
            CryptReleaseContext(hProv, 0);
            return ActivationVerifyResult::Fail();
        }
        DebugLog("Public key imported OK");

        // 5. Hash the payload
        HCRYPTHASH hHash = 0;
        if (!CryptCreateHash(hProv, CALG_SHA_256, 0, 0, &hHash)) {
            DWORD err = GetLastError();
            DebugLog("FAIL: CryptCreateHash error=" + std::to_string(err));
            CryptDestroyKey(hKey);
            CryptReleaseContext(hProv, 0);
            return ActivationVerifyResult::Fail();
        }

        CryptHashData(hHash, payloadBytes.data(), (DWORD)payloadBytes.size(), 0);
        DebugLog("Hash created OK, payload size=" + std::to_string(payloadBytes.size()));

        // 6. Verify signature (CryptoAPI expects little-endian, Java produces big-endian)
        std::vector<BYTE> sigReversed(signatureBytes);
        std::reverse(sigReversed.begin(), sigReversed.end());

        BOOL verifyResult = CryptVerifySignature(hHash, sigReversed.data(), (DWORD)sigReversed.size(),
                                          hKey, NULL, 0);
        DWORD verifyErr = GetLastError();

        DebugLog("CryptVerifySignature result=" + std::to_string(verifyResult) + " error=" + std::to_string(verifyErr));
        DebugLog("Signature size=" + std::to_string(signatureBytes.size()));

        verified = (verifyResult == TRUE);

        CryptDestroyHash(hHash);
        CryptDestroyKey(hKey);
        CryptReleaseContext(hProv, 0);

        if (!verified) {
            DebugLog("FAIL: RSA signature verification failed");
            return ActivationVerifyResult::Fail();
        }

        DebugLog("RSA signature verified OK");

        // Device ID mismatch check
        if (!expectedDeviceId.empty() && !deviceId.empty() && deviceId != expectedDeviceId) {
            DebugLog("FAIL: Device mismatch. Expected=" + expectedDeviceId + " Got=" + deviceId);
            return ActivationVerifyResult::FailDeviceMismatch(serialNumber, deviceId, expireTimestamp);
        }

        // Time check with tampering detection
        int64_t currentTimestamp = ActivationTimeGuard::GetTrustedTimestamp(serialNumber, expireTimestamp);
        DebugLog("Current trusted timestamp: " + std::to_string(currentTimestamp));
        DebugLog("Expire timestamp: " + std::to_string(expireTimestamp));

        if (expireTimestamp < currentTimestamp) {
            DebugLog("FAIL: Expired");
            return ActivationVerifyResult::FailExpired(serialNumber, deviceId, expireTimestamp);
        }

        ActivationTimeGuard::RecordActivation(serialNumber, expireTimestamp);

        DebugLog("=== Verify SUCCESS ===");
        return ActivationVerifyResult::Ok(serialNumber, deviceId, expireTimestamp);

    } catch (const std::exception& e) {
        DebugLog(std::string("EXCEPTION: ") + e.what());
        return ActivationVerifyResult::Fail();
    } catch (...) {
        DebugLog("EXCEPTION: unknown");
        return ActivationVerifyResult::Fail();
    }
}
