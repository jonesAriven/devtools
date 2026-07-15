#pragma once
#include <string>
#include <vector>
#include <cstdint>
#include <windows.h>

// Crypto utilities: SHA256, Base64, HMAC-SHA256, PBKDF2, AES-256-CBC
namespace ActivationCrypto {

// SHA256 hash, returns 32 bytes
std::vector<BYTE> SHA256Hash(const BYTE* data, size_t len);

// SHA256 hash to uppercase hex string
std::string SHA256Hex(const BYTE* data, size_t len);

// Standard Base64 encode/decode
std::string Base64Encode(const BYTE* data, size_t len);
std::vector<BYTE> Base64Decode(const std::string& str);

// URL-safe Base64 encode (no padding) / decode
std::string Base64UrlEncode(const BYTE* data, size_t len);
std::vector<BYTE> Base64UrlDecode(const std::string& str);

// HMAC-SHA256
std::vector<BYTE> HMAC_SHA256(const BYTE* key, size_t keyLen, const BYTE* data, size_t dataLen);

// PBKDF2 with HMAC-SHA256
std::vector<BYTE> PBKDF2(const BYTE* password, size_t passwordLen,
                          const BYTE* salt, size_t saltLen,
                          int iterations, size_t dkLen);

// AES-256-CBC encrypt (PKCS7 padding)
std::vector<BYTE> AES256CBCEncrypt(const BYTE* key, size_t keyLen,
                                    const BYTE* iv, size_t ivLen,
                                    const BYTE* plaintext, size_t plaintextLen);

// AES-256-CBC decrypt (PKCS7 padding), returns empty on error
std::vector<BYTE> AES256CBCDecrypt(const BYTE* key, size_t keyLen,
                                    const BYTE* iv, size_t ivLen,
                                    const BYTE* ciphertext, size_t ciphertextLen);

} // namespace ActivationCrypto
