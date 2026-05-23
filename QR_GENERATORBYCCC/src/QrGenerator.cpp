#include "QrGenerator.h"
#include "Compressor.h"

extern "C" {
#include <qrcodegen.h>
}

#include <cstring>
#include <stdexcept>

namespace qr {

// Try to encode content into QR code, returns true on success
static bool tryEncode(const std::string& content, uint8_t* qrcode, uint8_t* tempBuffer, QrEcl ecl) {
    qrcodegen_Ecc eclMap[] = {
        qrcodegen_Ecc_LOW,      // QrEcl::Low
        qrcodegen_Ecc_MEDIUM,   // QrEcl::Medium
        qrcodegen_Ecc_QUARTILE, // QrEcl::Quartile
        qrcodegen_Ecc_HIGH      // QrEcl::High
    };
    return qrcodegen_encodeText(
        content.c_str(),
        tempBuffer,
        qrcode,
        eclMap[static_cast<int>(ecl)],
        1,                      // minVersion
        40,                     // maxVersion
        qrcodegen_Mask_AUTO,   // auto-select mask
        true                    // boost ECL
    );
}

HBITMAP generateQrBitmap(const std::string& text, bool allowCompress, int pixelSize, bool& outCompressed, QrEcl ecl) {
    if (text.empty()) { outCompressed = false; return NULL; }

    uint8_t* qrcode = new uint8_t[qrcodegen_BUFFER_LEN_MAX];
    uint8_t* tempBuffer = new uint8_t[qrcodegen_BUFFER_LEN_MAX];

    // Strategy: try plain text first, fall back to compression if too long
    // This ensures short text produces standard QR codes readable by any scanner
    bool ok = false;
    outCompressed = false;

    // Step 1: Try without compression (plain text - universally readable)
    ok = tryEncode(text, qrcode, tempBuffer, ecl);

    // Step 2: If plain text fails (too long) and compression allowed, try compressed
    if (!ok && allowCompress) {
        try {
            std::string compressed = compressText(text, true);
            ok = tryEncode(compressed, qrcode, tempBuffer, ecl);
            if (ok) outCompressed = true;
        } catch (...) {
            ok = false;
        }
    }

    delete[] tempBuffer;

    if (!ok) {
        delete[] qrcode;
        return NULL;
    }

    // Get QR code size
    int qrSize = qrcodegen_getSize(qrcode);

    // Create HBITMAP
    BITMAPINFO bmi = {};
    bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bmi.bmiHeader.biWidth = pixelSize;
    bmi.bmiHeader.biHeight = -pixelSize; // top-down
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = 24;
    bmi.bmiHeader.biCompression = BI_RGB;

    void* bits = nullptr;
    HBITMAP hBmp = CreateDIBSection(NULL, &bmi, DIB_RGB_COLORS, &bits, NULL, 0);
    if (!hBmp || !bits) {
        delete[] qrcode;
        return NULL;
    }

    // Fill bitmap with white background
    int rowBytes = ((pixelSize * 3 + 3) & ~3);
    uint8_t* pixels = static_cast<uint8_t*>(bits);
    memset(pixels, 0xFF, rowBytes * pixelSize);

    // Draw QR modules with proper quiet zone (at least 4 modules margin)
    int quietZone = 4;
    int totalModules = qrSize + 2 * quietZone;
    int moduleSize = pixelSize / totalModules;
    if (moduleSize < 1) moduleSize = 1;

    // Center the QR code in the bitmap with quiet zone
    int offset = quietZone * moduleSize + (pixelSize - moduleSize * totalModules) / 2;

    for (int y = 0; y < qrSize; y++) {
        for (int x = 0; x < qrSize; x++) {
            if (qrcodegen_getModule(qrcode, x, y)) {
                int px = offset + x * moduleSize;
                int py = offset + y * moduleSize;
                for (int dy = 0; dy < moduleSize && (py + dy) < pixelSize; dy++) {
                    uint8_t* row = pixels + (py + dy) * rowBytes;
                    for (int dx = 0; dx < moduleSize && (px + dx) < pixelSize; dx++) {
                        int idx = (px + dx) * 3;
                        row[idx] = 0x00;     // B
                        row[idx + 1] = 0x00; // G
                        row[idx + 2] = 0x00; // R
                    }
                }
            }
        }
    }

    delete[] qrcode;
    return hBmp;
}

HBITMAP generateQrBitmap(const std::string& text, bool allowCompress, int pixelSize) {
    bool dummy;
    return generateQrBitmap(text, allowCompress, pixelSize, dummy);
}

} // namespace qr
