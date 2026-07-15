#pragma once
#include <string>

// Secure storage for activation code using AES-256-CBC encryption
namespace ActivationSecureStorage {

// Save activation code encrypted with device-bound key
bool Save(const std::string& filePath, const std::string& activationCode);

// Load and decrypt activation code, returns empty string on failure
std::string Load(const std::string& filePath);

// Securely delete file (overwrite with zeros first)
void Delete(const std::string& filePath);

} // namespace ActivationSecureStorage
