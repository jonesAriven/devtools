#pragma once
#include <string>

// Device fingerprint collection and serial number generation/parsing
namespace ActivationDeviceInfo {

struct SerialNumberInfo {
    std::string initialSerial;
    std::string deviceId;
    std::string machineCode;
};

// Get device ID: SHA256(CPU+Board+Disk+MAC) first 32 hex chars
std::string GetDeviceId();

// Get machine code: formatted MAC address (XX-XX-XX-XX-XX-XX)
std::string GetMachineCode();

// Generate encrypted serial number: plainText="initialSerial|deviceId|machineCode|version" -> XOR 0x5A -> Base64
// version 可选，如果提供则作为第4段嵌入序列号
std::string GetSerialNumber(const std::string& initialSerial, const std::string& version = "");

// Parse encrypted serial number: Base64 -> XOR 0x5A -> split by '|'
SerialNumberInfo ParseSerialNumber(const std::string& encryptedSerialNumber);

} // namespace ActivationDeviceInfo
