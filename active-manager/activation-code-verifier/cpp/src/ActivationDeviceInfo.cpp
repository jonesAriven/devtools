#include "Jones/ActivationDeviceInfo.h"
#include "Jones/ActivationCrypto.h"
#include <comdef.h>
#include <wbemidl.h>
#include <iphlpapi.h>
#include <algorithm>
#include <sstream>

#pragma comment(lib, "wbemuuid.lib")
#pragma comment(lib, "iphlpapi.lib")
#pragma comment(lib, "oleaut32.lib")

namespace ActivationDeviceInfo {

// --- WMI helper ---
static std::string WmiQuerySingle(const wchar_t* query, const wchar_t* property) {
    std::string result;
    IWbemLocator* pLoc = NULL;
    IWbemServices* pSvc = NULL;
    IEnumWbemClassObject* pEnumerator = NULL;
    IWbemClassObject* pClsObj = NULL;

    HRESULT hr = CoInitializeEx(NULL, COINIT_MULTITHREADED);
    bool needUninitialize = (hr == S_OK);

    hr = CoCreateInstance(CLSID_WbemLocator, NULL, CLSCTX_INPROC_SERVER,
                          IID_IWbemLocator, (LPVOID*)&pLoc);
    if (FAILED(hr)) goto cleanup;

    hr = pLoc->ConnectServer(L"ROOT\\CIMV2", NULL, NULL, NULL, 0, NULL, NULL, &pSvc);
    if (FAILED(hr)) goto cleanup;

    hr = CoSetProxyBlanket(pSvc, RPC_C_AUTHN_WINNT, RPC_C_AUTHZ_NONE, NULL,
                           RPC_C_AUTHN_LEVEL_CALL, RPC_C_IMP_LEVEL_IMPERSONATE,
                           NULL, EOAC_NONE);
    if (FAILED(hr)) goto cleanup;

    hr = pSvc->ExecQuery(L"WQL", (BSTR)query,
                         WBEM_FLAG_FORWARD_ONLY | WBEM_FLAG_RETURN_IMMEDIATELY,
                         NULL, &pEnumerator);
    if (FAILED(hr)) goto cleanup;

    {
        ULONG uReturn = 0;
        while (pEnumerator) {
            hr = pEnumerator->Next(WBEM_INFINITE, 1, &pClsObj, &uReturn);
            if (uReturn == 0) break;

            VARIANT vtProp;
            hr = pClsObj->Get(property, 0, &vtProp, 0, 0);
            if (SUCCEEDED(hr) && vtProp.vt == VT_BSTR) {
                _bstr_t bstrVal(vtProp.bstrVal);
                result = (const char*)bstrVal;
                VariantClear(&vtProp);
                pClsObj->Release();
                pClsObj = NULL;
                break;
            }
            VariantClear(&vtProp);
            pClsObj->Release();
            pClsObj = NULL;
        }
    }

cleanup:
    if (pEnumerator) pEnumerator->Release();
    if (pSvc) pSvc->Release();
    if (pLoc) pLoc->Release();
    if (needUninitialize) CoUninitialize();
    return result;
}

// --- Cached hardware info (avoid repeated WMI queries) ---
static std::string s_cachedCPUId;
static std::string s_cachedBoardId;
static std::string s_cachedDiskId;
static std::string s_cachedMacRaw;
static std::string s_cachedDeviceId;
static std::string s_cachedMachineCode;

// --- Hardware info ---
static std::string GetCPUId() {
    if (!s_cachedCPUId.empty()) return s_cachedCPUId;
    std::string val = WmiQuerySingle(L"SELECT ProcessorId FROM Win32_Processor", L"ProcessorId");
    s_cachedCPUId = val.empty() ? "CPU_UNKNOWN" : val;
    return s_cachedCPUId;
}

static std::string GetMotherboardId() {
    if (!s_cachedBoardId.empty()) return s_cachedBoardId;
    std::string val = WmiQuerySingle(L"SELECT SerialNumber FROM Win32_BaseBoard", L"SerialNumber");
    s_cachedBoardId = val.empty() ? "BOARD_UNKNOWN" : val;
    return s_cachedBoardId;
}

static std::string GetDiskId() {
    if (!s_cachedDiskId.empty()) return s_cachedDiskId;
    std::string val = WmiQuerySingle(L"SELECT VolumeSerialNumber FROM Win32_LogicalDisk WHERE DriveType=3", L"VolumeSerialNumber");
    s_cachedDiskId = val.empty() ? "DISK_UNKNOWN" : val;
    return s_cachedDiskId;
}

static std::string GetMacAddressRaw() {
    if (!s_cachedMacRaw.empty()) return s_cachedMacRaw;
    ULONG bufLen = 0;
    GetAdaptersInfo(NULL, &bufLen);
    if (bufLen == 0) { s_cachedMacRaw = ""; return s_cachedMacRaw; }

    std::vector<BYTE> buf(bufLen);
    PIP_ADAPTER_INFO pAdapterInfo = (PIP_ADAPTER_INFO)buf.data();
    if (GetAdaptersInfo(pAdapterInfo, &bufLen) != ERROR_SUCCESS) { s_cachedMacRaw = ""; return s_cachedMacRaw; }

    for (PIP_ADAPTER_INFO pAdapter = pAdapterInfo; pAdapter; pAdapter = pAdapter->Next) {
        if (pAdapter->Type == MIB_IF_TYPE_ETHERNET || pAdapter->Type == IF_TYPE_IEEE80211) {
            if (pAdapter->AddressLength > 0) {
                std::string mac;
                for (UINT i = 0; i < pAdapter->AddressLength; i++) {
                    char hex[3];
                    sprintf_s(hex, "%02X", pAdapter->Address[i]);
                    mac += hex;
                }
                s_cachedMacRaw = mac;
                return s_cachedMacRaw;
            }
        }
    }

    // Fallback to WMI
    std::string val = WmiQuerySingle(L"SELECT MACAddress FROM Win32_NetworkAdapter WHERE NetConnectionStatus=2", L"MACAddress");
    if (!val.empty()) {
        // Remove colons/dashes
        val.erase(std::remove(val.begin(), val.end(), ':'), val.end());
        val.erase(std::remove(val.begin(), val.end(), '-'), val.end());
    }
    s_cachedMacRaw = val.empty() ? "MAC_UNKNOWN" : val;
    return s_cachedMacRaw;
}

std::string GetDeviceId() {
    if (!s_cachedDeviceId.empty()) return s_cachedDeviceId;
    std::string cpuId = GetCPUId();
    std::string boardId = GetMotherboardId();
    std::string diskId = GetDiskId();
    std::string macAddr = GetMacAddressRaw();

    std::string combined = cpuId + boardId + diskId + macAddr;
    std::string hex = ActivationCrypto::SHA256Hex((const BYTE*)combined.data(), combined.size());
    s_cachedDeviceId = hex.substr(0, 32);
    return s_cachedDeviceId;
}

std::string GetMachineCode() {
    if (!s_cachedMachineCode.empty()) return s_cachedMachineCode;
    std::string mac = GetMacAddressRaw();
    if (mac.empty() || mac == "MAC_UNKNOWN") {
        s_cachedMachineCode = mac;
        return s_cachedMachineCode;
    }

    if (mac.length() >= 12) {
        std::string formatted;
        for (int i = 0; i < 6; i++) {
            if (i > 0) formatted += "-";
            formatted += mac.substr(i * 2, 2);
        }
        s_cachedMachineCode = formatted;
    } else {
        s_cachedMachineCode = mac;
    }
    return s_cachedMachineCode;
}

// --- Serial number ---
static const BYTE SERIAL_XOR_KEY = 0x5A;

std::string GetSerialNumber(const std::string& initialSerial) {
    std::string deviceId = GetDeviceId();
    std::string machineCode = GetMachineCode();

    std::string plainText = initialSerial + "|" + deviceId + "|" + machineCode;
    std::vector<BYTE> plainBytes(plainText.begin(), plainText.end());

    std::vector<BYTE> encrypted(plainBytes.size());
    for (size_t i = 0; i < plainBytes.size(); i++) {
        encrypted[i] = plainBytes[i] ^ SERIAL_XOR_KEY;
    }

    return ActivationCrypto::Base64Encode(encrypted.data(), encrypted.size());
}

SerialNumberInfo ParseSerialNumber(const std::string& encryptedSerialNumber) {
    SerialNumberInfo info;
    try {
        auto encrypted = ActivationCrypto::Base64Decode(encryptedSerialNumber);
        std::vector<BYTE> decrypted(encrypted.size());
        for (size_t i = 0; i < encrypted.size(); i++) {
            decrypted[i] = encrypted[i] ^ SERIAL_XOR_KEY;
        }

        std::string plainText(decrypted.begin(), decrypted.end());

        // Split by '|'
        std::vector<std::string> parts;
        std::istringstream iss(plainText);
        std::string part;
        while (std::getline(iss, part, '|')) {
            parts.push_back(part);
        }

        if (parts.size() >= 3) {
            info.initialSerial = parts[0];
            info.deviceId = parts[1];
            info.machineCode = parts[2];
        }
    } catch (...) {}

    return info;
}

} // namespace ActivationDeviceInfo
