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

// --- Hardware info ---
static std::string GetCPUId() {
    std::string val = WmiQuerySingle(L"SELECT ProcessorId FROM Win32_Processor", L"ProcessorId");
    return val.empty() ? "CPU_UNKNOWN" : val;
}

static std::string GetMotherboardId() {
    std::string val = WmiQuerySingle(L"SELECT SerialNumber FROM Win32_BaseBoard", L"SerialNumber");
    return val.empty() ? "BOARD_UNKNOWN" : val;
}

static std::string GetDiskId() {
    std::string val = WmiQuerySingle(L"SELECT VolumeSerialNumber FROM Win32_LogicalDisk WHERE DriveType=3", L"VolumeSerialNumber");
    return val.empty() ? "DISK_UNKNOWN" : val;
}

static std::string GetMacAddressRaw() {
    // Try GetAdaptersInfo first (simpler than WMI)
    ULONG bufLen = 0;
    GetAdaptersInfo(NULL, &bufLen);
    if (bufLen == 0) return "";

    std::vector<BYTE> buf(bufLen);
    PIP_ADAPTER_INFO pAdapterInfo = (PIP_ADAPTER_INFO)buf.data();
    if (GetAdaptersInfo(pAdapterInfo, &bufLen) != ERROR_SUCCESS) return "";

    for (PIP_ADAPTER_INFO pAdapter = pAdapterInfo; pAdapter; pAdapter = pAdapter->Next) {
        if (pAdapter->Type == MIB_IF_TYPE_ETHERNET || pAdapter->Type == IF_TYPE_IEEE80211) {
            if (pAdapter->AddressLength > 0) {
                std::string mac;
                for (UINT i = 0; i < pAdapter->AddressLength; i++) {
                    char hex[3];
                    sprintf_s(hex, "%02X", pAdapter->Address[i]);
                    mac += hex;
                }
                return mac;
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
    return val.empty() ? "MAC_UNKNOWN" : val;
}

std::string GetDeviceId() {
    std::string cpuId = GetCPUId();
    std::string boardId = GetMotherboardId();
    std::string diskId = GetDiskId();
    std::string macAddr = GetMacAddressRaw();

    std::string combined = cpuId + boardId + diskId + macAddr;
    std::string hex = ActivationCrypto::SHA256Hex((const BYTE*)combined.data(), combined.size());
    return hex.substr(0, 32);
}

std::string GetMachineCode() {
    std::string mac = GetMacAddressRaw();
    if (mac.empty() || mac == "MAC_UNKNOWN") return mac;

    if (mac.length() >= 12) {
        std::string formatted;
        for (int i = 0; i < 6; i++) {
            if (i > 0) formatted += "-";
            formatted += mac.substr(i * 2, 2);
        }
        return formatted;
    }
    return mac;
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
