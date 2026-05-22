#pragma once
#include <string>
#include <windows.h>

namespace qr {

// Generate QR code bitmap from text
// allowCompress: whether to allow Brotli+Base45 compression when text is too long
// pixelSize: output bitmap size in pixels (default 600)
// outCompressed: set to true if compression was actually used
// Returns HBITMAP, caller must DeleteObject when done
HBITMAP generateQrBitmap(const std::string& text, bool allowCompress, int pixelSize, bool& outCompressed);

// Convenience overload
HBITMAP generateQrBitmap(const std::string& text, bool allowCompress, int pixelSize = 600);

} // namespace qr
