#include "Jones/ActivationCrypto.h"
#include <bcrypt.h>
#include <algorithm>
#include <stdexcept>

#pragma comment(lib, "bcrypt.lib")

namespace ActivationCrypto {

// --- SHA256 ---
std::vector<BYTE> SHA256Hash(const BYTE* data, size_t len) {
    BCRYPT_ALG_HANDLE hAlg = NULL;
    BCRYPT_HASH_HANDLE hHash = NULL;
    NTSTATUS status;

    status = BCryptOpenAlgorithmProvider(&hAlg, BCRYPT_SHA256_ALGORITHM, NULL, 0);
    if (status != 0) return {};

    DWORD hashObjLen = 0, resultLen = 0;
    BCryptGetProperty(hAlg, BCRYPT_OBJECT_LENGTH, (PUCHAR)&hashObjLen, sizeof(DWORD), &resultLen, 0);

    std::vector<BYTE> hashObj(hashObjLen);
    status = BCryptCreateHash(hAlg, &hHash, hashObj.data(), hashObjLen, NULL, 0, 0);
    if (status != 0) {
        BCryptCloseAlgorithmProvider(hAlg, 0);
        return {};
    }

    BCryptHashData(hHash, (PUCHAR)data, (ULONG)len, 0);

    std::vector<BYTE> hash(32);
    BCryptFinishHash(hHash, hash.data(), 32, 0);

    BCryptDestroyHash(hHash);
    BCryptCloseAlgorithmProvider(hAlg, 0);
    return hash;
}

std::string SHA256Hex(const BYTE* data, size_t len) {
    auto hash = SHA256Hash(data, len);
    if (hash.empty()) return "";

    std::string hex;
    hex.reserve(hash.size() * 2);
    const char* digits = "0123456789ABCDEF";
    for (BYTE b : hash) {
        hex += digits[b >> 4];
        hex += digits[b & 0x0F];
    }
    return hex;
}

// --- Base64 ---
static const char kBase64Chars[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

std::string Base64Encode(const BYTE* data, size_t len) {
    std::string result;
    result.reserve((len + 2) / 3 * 4);

    for (size_t i = 0; i < len; i += 3) {
        unsigned int n = (unsigned int)data[i] << 16;
        if (i + 1 < len) n |= (unsigned int)data[i + 1] << 8;
        if (i + 2 < len) n |= data[i + 2];

        result += kBase64Chars[(n >> 18) & 0x3F];
        result += kBase64Chars[(n >> 12) & 0x3F];
        result += (i + 1 < len) ? kBase64Chars[(n >> 6) & 0x3F] : '=';
        result += (i + 2 < len) ? kBase64Chars[n & 0x3F] : '=';
    }
    return result;
}

static int base64Val(char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return -1;
}

std::vector<BYTE> Base64Decode(const std::string& str) {
    std::vector<BYTE> result;
    if (str.empty()) return result;

    int val = 0, valb = -8;
    for (char c : str) {
        if (c == '=') break;
        int v = base64Val(c);
        if (v < 0) continue;
        val = (val << 6) | v;
        valb += 6;
        if (valb >= 0) {
            result.push_back((BYTE)((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    return result;
}

// --- URL-safe Base64 ---
static const char kBase64UrlChars[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

std::string Base64UrlEncode(const BYTE* data, size_t len) {
    std::string result;
    result.reserve((len + 2) / 3 * 4);

    for (size_t i = 0; i < len; i += 3) {
        unsigned int n = (unsigned int)data[i] << 16;
        if (i + 1 < len) n |= (unsigned int)data[i + 1] << 8;
        if (i + 2 < len) n |= data[i + 2];

        result += kBase64UrlChars[(n >> 18) & 0x3F];
        result += kBase64UrlChars[(n >> 12) & 0x3F];
        if (i + 1 < len) result += kBase64UrlChars[(n >> 6) & 0x3F];
        if (i + 2 < len) result += kBase64UrlChars[n & 0x3F];
    }
    return result;
}

std::vector<BYTE> Base64UrlDecode(const std::string& str) {
    // Add padding
    std::string padded = str;
    int pad = padded.length() % 4;
    if (pad > 0) padded += std::string(4 - pad, '=');

    // Replace URL-safe chars
    for (char& c : padded) {
        if (c == '-') c = '+';
        else if (c == '_') c = '/';
    }

    return Base64Decode(padded);
}

// --- HMAC-SHA256 ---
std::vector<BYTE> HMAC_SHA256(const BYTE* key, size_t keyLen, const BYTE* data, size_t dataLen) {
    BCRYPT_ALG_HANDLE hAlg = NULL;
    BCRYPT_HASH_HANDLE hHash = NULL;
    NTSTATUS status;

    status = BCryptOpenAlgorithmProvider(&hAlg, BCRYPT_SHA256_ALGORITHM, NULL, BCRYPT_ALG_HANDLE_HMAC_FLAG);
    if (status != 0) return {};

    DWORD hashObjLen = 0, resultLen = 0;
    BCryptGetProperty(hAlg, BCRYPT_OBJECT_LENGTH, (PUCHAR)&hashObjLen, sizeof(DWORD), &resultLen, 0);

    std::vector<BYTE> hashObj(hashObjLen);
    status = BCryptCreateHash(hAlg, &hHash, hashObj.data(), hashObjLen, (PUCHAR)key, (ULONG)keyLen, 0);
    if (status != 0) {
        BCryptCloseAlgorithmProvider(hAlg, 0);
        return {};
    }

    BCryptHashData(hHash, (PUCHAR)data, (ULONG)dataLen, 0);

    std::vector<BYTE> hash(32);
    BCryptFinishHash(hHash, hash.data(), 32, 0);

    BCryptDestroyHash(hHash);
    BCryptCloseAlgorithmProvider(hAlg, 0);
    return hash;
}

// --- PBKDF2 ---
std::vector<BYTE> PBKDF2(const BYTE* password, size_t passwordLen,
                          const BYTE* salt, size_t saltLen,
                          int iterations, size_t dkLen) {
    std::vector<BYTE> derivedKey(dkLen, 0);
    const size_t hLen = 32;
    int blocks = (int)((dkLen + hLen - 1) / hLen);

    for (int block = 1; block <= blocks; block++) {
        // U1 = HMAC(password, salt || INT_32_BE(block))
        std::vector<BYTE> saltBlock(salt, salt + saltLen);
        saltBlock.push_back((BYTE)((block >> 24) & 0xFF));
        saltBlock.push_back((BYTE)((block >> 16) & 0xFF));
        saltBlock.push_back((BYTE)((block >> 8) & 0xFF));
        saltBlock.push_back((BYTE)(block & 0xFF));

        auto U = HMAC_SHA256(password, passwordLen, saltBlock.data(), saltBlock.size());
        if (U.empty()) return {};
        auto T = U;

        for (int i = 1; i < iterations; i++) {
            U = HMAC_SHA256(password, passwordLen, U.data(), U.size());
            if (U.empty()) return {};
            for (size_t j = 0; j < hLen; j++) T[j] ^= U[j];
        }

        size_t offset = (block - 1) * hLen;
        size_t copyLen = (std::min)(hLen, dkLen - offset);
        memcpy(derivedKey.data() + offset, T.data(), copyLen);
    }

    return derivedKey;
}

// --- AES-256-CBC ---
std::vector<BYTE> AES256CBCEncrypt(const BYTE* key, size_t keyLen,
                                    const BYTE* iv, size_t ivLen,
                                    const BYTE* plaintext, size_t plaintextLen) {
    BCRYPT_ALG_HANDLE hAlg = NULL;
    BCRYPT_KEY_HANDLE hKey = NULL;
    NTSTATUS status;

    status = BCryptOpenAlgorithmProvider(&hAlg, BCRYPT_AES_ALGORITHM, NULL, 0);
    if (status != 0) return {};

    // Set CBC chaining mode
    DWORD chainModeLen = (DWORD)sizeof(BCRYPT_CHAIN_MODE_CBC);
    status = BCryptSetProperty(hAlg, BCRYPT_CHAINING_MODE, (PUCHAR)BCRYPT_CHAIN_MODE_CBC, chainModeLen, 0);
    if (status != 0) {
        BCryptCloseAlgorithmProvider(hAlg, 0);
        return {};
    }

    // Import key
    BCRYPT_KEY_DATA_BLOB_HEADER keyBlob = { BCRYPT_KEY_DATA_BLOB_MAGIC, BCRYPT_KEY_DATA_BLOB_VERSION1, (ULONG)keyLen };
    std::vector<BYTE> fullKeyBlob(sizeof(BCRYPT_KEY_DATA_BLOB_HEADER) + keyLen);
    memcpy(fullKeyBlob.data(), &keyBlob, sizeof(keyBlob));
    memcpy(fullKeyBlob.data() + sizeof(keyBlob), key, keyLen);

    status = BCryptImportKey(hAlg, NULL, BCRYPT_KEY_DATA_BLOB, &hKey, NULL, 0,
                             fullKeyBlob.data(), (ULONG)fullKeyBlob.size(), 0);
    if (status != 0) {
        BCryptCloseAlgorithmProvider(hAlg, 0);
        return {};
    }

    // Copy IV since BCryptEncrypt modifies it
    BYTE ivCopy[16] = {};
    memcpy(ivCopy, iv, (std::min)(ivLen, (size_t)16));

    DWORD ciphertextLen = 0;
    status = BCryptEncrypt(hKey, (PUCHAR)plaintext, (ULONG)plaintextLen,
                           NULL, ivCopy, 16, NULL, 0, &ciphertextLen, BCRYPT_BLOCK_PADDING);
    if (status != 0) {
        BCryptDestroyKey(hKey);
        BCryptCloseAlgorithmProvider(hAlg, 0);
        return {};
    }

    std::vector<BYTE> ciphertext(ciphertextLen);
    memcpy(ivCopy, iv, (std::min)(ivLen, (size_t)16));
    status = BCryptEncrypt(hKey, (PUCHAR)plaintext, (ULONG)plaintextLen,
                           NULL, ivCopy, 16, ciphertext.data(), ciphertextLen, &ciphertextLen, BCRYPT_BLOCK_PADDING);

    BCryptDestroyKey(hKey);
    BCryptCloseAlgorithmProvider(hAlg, 0);

    if (status != 0) return {};
    return ciphertext;
}

std::vector<BYTE> AES256CBCDecrypt(const BYTE* key, size_t keyLen,
                                    const BYTE* iv, size_t ivLen,
                                    const BYTE* ciphertext, size_t ciphertextLen) {
    BCRYPT_ALG_HANDLE hAlg = NULL;
    BCRYPT_KEY_HANDLE hKey = NULL;
    NTSTATUS status;

    status = BCryptOpenAlgorithmProvider(&hAlg, BCRYPT_AES_ALGORITHM, NULL, 0);
    if (status != 0) return {};

    DWORD chainModeLen = (DWORD)sizeof(BCRYPT_CHAIN_MODE_CBC);
    BCryptSetProperty(hAlg, BCRYPT_CHAINING_MODE, (PUCHAR)BCRYPT_CHAIN_MODE_CBC, chainModeLen, 0);

    BCRYPT_KEY_DATA_BLOB_HEADER keyBlob = { BCRYPT_KEY_DATA_BLOB_MAGIC, BCRYPT_KEY_DATA_BLOB_VERSION1, (ULONG)keyLen };
    std::vector<BYTE> fullKeyBlob(sizeof(BCRYPT_KEY_DATA_BLOB_HEADER) + keyLen);
    memcpy(fullKeyBlob.data(), &keyBlob, sizeof(keyBlob));
    memcpy(fullKeyBlob.data() + sizeof(keyBlob), key, keyLen);

    status = BCryptImportKey(hAlg, NULL, BCRYPT_KEY_DATA_BLOB, &hKey, NULL, 0,
                             fullKeyBlob.data(), (ULONG)fullKeyBlob.size(), 0);
    if (status != 0) {
        BCryptCloseAlgorithmProvider(hAlg, 0);
        return {};
    }

    BYTE ivCopy[16] = {};
    memcpy(ivCopy, iv, (std::min)(ivLen, (size_t)16));

    DWORD plaintextLen = 0;
    status = BCryptDecrypt(hKey, (PUCHAR)ciphertext, (ULONG)ciphertextLen,
                           NULL, ivCopy, 16, NULL, 0, &plaintextLen, BCRYPT_BLOCK_PADDING);
    if (status != 0) {
        BCryptDestroyKey(hKey);
        BCryptCloseAlgorithmProvider(hAlg, 0);
        return {};
    }

    std::vector<BYTE> plaintext(plaintextLen);
    memcpy(ivCopy, iv, (std::min)(ivLen, (size_t)16));
    status = BCryptDecrypt(hKey, (PUCHAR)ciphertext, (ULONG)ciphertextLen,
                           NULL, ivCopy, 16, plaintext.data(), plaintextLen, &plaintextLen, BCRYPT_BLOCK_PADDING);

    BCryptDestroyKey(hKey);
    BCryptCloseAlgorithmProvider(hAlg, 0);

    if (status != 0) return {};
    plaintext.resize(plaintextLen);
    return plaintext;
}

} // namespace ActivationCrypto
