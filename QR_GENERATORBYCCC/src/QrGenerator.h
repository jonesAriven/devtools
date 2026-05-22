#pragma once
#include <string>
#include <windows.h>

namespace qr {

// Error correction level
enum class QrEcl {
    Low = 0,      // 7% recovery
    Medium = 1,   // 15% recovery
    Quartile = 2, // 25% recovery
    High = 3      // 30% recovery
};

// Generate QR code bitmap from text
// allowCompress: whether to allow Brotli+Base45 compression when text is too long
// pixelSize: output bitmap size in pixels (default 600)
// outCompressed: set to true if compression was actually used
// ecl: error correction level (default Low for max capacity)
// Returns HBITMAP, caller must DeleteObject when done
HBITMAP generateQrBitmap(const std::string& text, bool allowCompress, int pixelSize, bool& outCompressed, QrEcl ecl = QrEcl::Low);

// Convenience overload
HBITMAP generateQrBitmap(const std::string& text, bool allowCompress, int pixelSize = 600);

} // namespace qr
