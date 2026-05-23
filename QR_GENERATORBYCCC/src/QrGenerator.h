#pragma once
#include <string>
#include <vector>
#include <windows.h>

namespace qr {

// Error correction level
enum class QrEcl {
    Low = 0,      // 7% recovery
    Medium = 1,   // 15% recovery
    Quartile = 2, // 25% recovery
    High = 3      // 30% recovery
};

// Single QR code page info
struct QrPage {
    HBITMAP bitmap;
    int pageIndex;   // 0-based
    int totalPages;
};

// Generate a single QR code bitmap from text
// allowCompress: whether to allow Brotli+Base45 compression when text is too long
// pixelSize: output bitmap size in pixels (default 600)
// outCompressed: set to true if compression was actually used
// ecl: error correction level (default Low for max capacity)
// Returns HBITMAP, caller must DeleteObject when done
HBITMAP generateQrBitmap(const std::string& text, bool allowCompress, int pixelSize, bool& outCompressed, QrEcl ecl = QrEcl::Low);

// Convenience overload
HBITMAP generateQrBitmap(const std::string& text, bool allowCompress, int pixelSize = 600);

// Generate multiple QR code bitmaps from text (auto-pagination)
// If text fits in one QR code, returns a single page
// If text is too long, splits into multiple pages with M5: prefix
// Returns vector of QrPage, caller must DeleteObject each bitmap when done
std::vector<QrPage> generateQrPages(const std::string& text, bool allowCompress, int pixelSize, bool& outCompressed, QrEcl ecl = QrEcl::Low);

// Get the maximum data capacity (in characters) for a given ECL at QR version 40
// using alphanumeric mode (which Base45 uses)
int getMaxQrCapacity(QrEcl ecl);

} // namespace qr
