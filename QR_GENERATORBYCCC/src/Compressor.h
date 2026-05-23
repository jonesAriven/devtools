#pragma once
#include <string>
#include <vector>
#include <cstdint>

namespace qr {

// Brotli compress
std::vector<uint8_t> brotliCompress(const std::string& data);

// Brotli decompress
std::string brotliDecompress(const uint8_t* data, size_t len);

// GZip decompress
std::string gzipDecompress(const uint8_t* data, size_t len);

// Compress text for QR code
// If compress=true: UTF-8 encode -> Brotli compress -> Base45 encode -> "B5:" prefix
// If compress=false: return original text
std::string compressText(const std::string& text, bool compress);

// Decompress text from QR code
// Handles "B5:" prefix (Brotli+Base45) and "GZ:" prefix (GZip+Base64)
// No prefix: return as-is
std::string decompressText(const std::string& text);

} // namespace qr
