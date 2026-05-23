#pragma once
#include <string>
#include <windows.h>

namespace qr {

struct DecodeResult {
    bool success;
    std::string text;
    std::string strategy;
};

// Decode QR code from HBITMAP using multi-strategy approach
DecodeResult decodeFromBitmap(HBITMAP hBitmap);

// Decode QR code from file path (wide string for Unicode support)
DecodeResult decodeFromFile(const wchar_t* filePath);

} // namespace qr
