#pragma once
#include <string>
#include <vector>
#include <cstdint>
#include <map>

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
// Handles "M5:" prefix (multi-page Brotli+Base45, single page)
// Handles "B5:" prefix (Brotli+Base45)
// Handles "GZ:" prefix (GZip+Base64)
// No prefix: return as-is
std::string decompressText(const std::string& text);

// Multi-page assembly state
struct MultiPageAssembler {
    int totalPages = 0;                          // 0 = unknown (first page not seen yet)
    std::map<int, std::string> pages;            // page index (0-based) -> chunk data
    bool isMultiPage = false;                     // true if M5: prefix detected

    // Add a scanned page, returns true if this is a new page
    // text: raw QR code text (with M5: prefix)
    bool addPage(const std::string& text);

    // Check if all pages have been collected
    bool isComplete() const;

    // Assemble and decompress all collected pages
    // Returns empty string if not complete or decompression fails
    std::string assemble() const;

    // Reset state
    void reset();

    // Get missing page indices
    std::vector<int> getMissingPages() const;

    // Parse a single M5: page without adding it
    // Returns true if text has M5: prefix, fills out page(1-based) and total
    static bool parseM5Header(const std::string& text, int& outPage, int& outTotal, std::string& outChunk);
};

} // namespace qr
