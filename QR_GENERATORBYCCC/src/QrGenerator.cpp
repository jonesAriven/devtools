#include "QrGenerator.h"
#include "Compressor.h"
#include "Base45.h"

extern "C" {
#include <qrcodegen.h>
}

#include <cstring>
#include <stdexcept>
#include <sstream>

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

// Create HBITMAP from a QR code buffer
static HBITMAP createQrBitmap(const uint8_t* qrcode, int pixelSize) {
    int qrSize = qrcodegen_getSize(qrcode);

    BITMAPINFO bmi = {};
    bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bmi.bmiHeader.biWidth = pixelSize;
    bmi.bmiHeader.biHeight = -pixelSize; // top-down
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = 24;
    bmi.bmiHeader.biCompression = BI_RGB;

    void* bits = nullptr;
    HBITMAP hBmp = CreateDIBSection(NULL, &bmi, DIB_RGB_COLORS, &bits, NULL, 0);
    if (!hBmp || !bits) return NULL;

    int rowBytes = ((pixelSize * 3 + 3) & ~3);
    uint8_t* pixels = static_cast<uint8_t*>(bits);
    memset(pixels, 0xFF, rowBytes * pixelSize);

    int quietZone = 4;
    int totalModules = qrSize + 2 * quietZone;
    int moduleSize = pixelSize / totalModules;
    if (moduleSize < 1) moduleSize = 1;

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

    return hBmp;
}

HBITMAP generateQrBitmap(const std::string& text, bool allowCompress, int pixelSize, bool& outCompressed, QrEcl ecl) {
    if (text.empty()) { outCompressed = false; return NULL; }

    uint8_t* qrcode = new uint8_t[qrcodegen_BUFFER_LEN_MAX];
    uint8_t* tempBuffer = new uint8_t[qrcodegen_BUFFER_LEN_MAX];

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

    if (!ok) {
        delete[] qrcode;
        delete[] tempBuffer;
        return NULL;
    }

    HBITMAP hBmp = createQrBitmap(qrcode, pixelSize);
    delete[] qrcode;
    delete[] tempBuffer;
    return hBmp;
}

HBITMAP generateQrBitmap(const std::string& text, bool allowCompress, int pixelSize) {
    bool dummy;
    return generateQrBitmap(text, allowCompress, pixelSize, dummy);
}

// Maximum alphanumeric character capacity for QR version 40 at each ECL
// These are the standard QR code capacity limits for alphanumeric mode
int getMaxQrCapacity(QrEcl ecl) {
    // QR Version 40 alphanumeric capacity:
    // L=4296, M=3391, Q=2420, H=1852
    // We use a conservative estimate (90%) to account for encoding overhead
    switch (ecl) {
    case QrEcl::Low:      return 3866;   // 4296 * 0.9
    case QrEcl::Medium:   return 3052;   // 3391 * 0.9
    case QrEcl::Quartile: return 2178;   // 2420 * 0.9
    case QrEcl::High:     return 1667;   // 1852 * 0.9
    default:              return 3866;
    }
}

std::vector<QrPage> generateQrPages(const std::string& text, bool allowCompress, int pixelSize, bool& outCompressed, QrEcl ecl) {
    std::vector<QrPage> pages;

    if (text.empty()) {
        outCompressed = false;
        return pages;
    }

    // Step 1: Try single QR code first (plain text)
    {
        uint8_t* qrcode = new uint8_t[qrcodegen_BUFFER_LEN_MAX];
        uint8_t* tempBuffer = new uint8_t[qrcodegen_BUFFER_LEN_MAX];
        bool ok = tryEncode(text, qrcode, tempBuffer, ecl);
        if (ok) {
            HBITMAP hBmp = createQrBitmap(qrcode, pixelSize);
            delete[] qrcode;
            delete[] tempBuffer;
            if (hBmp) {
                outCompressed = false;
                pages.push_back({hBmp, 0, 1});
                return pages;
            }
        }
        delete[] qrcode;
        delete[] tempBuffer;
    }

    // Step 2: Try single QR code with compression
    if (allowCompress) {
        try {
            std::string compressed = compressText(text, true);
            uint8_t* qrcode = new uint8_t[qrcodegen_BUFFER_LEN_MAX];
            uint8_t* tempBuffer = new uint8_t[qrcodegen_BUFFER_LEN_MAX];
            bool ok = tryEncode(compressed, qrcode, tempBuffer, ecl);
            if (ok) {
                HBITMAP hBmp = createQrBitmap(qrcode, pixelSize);
                delete[] qrcode;
                delete[] tempBuffer;
                if (hBmp) {
                    outCompressed = true;
                    pages.push_back({hBmp, 0, 1});
                    return pages;
                }
            }
            delete[] qrcode;
            delete[] tempBuffer;
        } catch (...) {
            // Compression failed, continue to multi-page
        }
    }

    // Step 3: Multi-page mode - compress first, then split into chunks
    if (!allowCompress) {
        outCompressed = false;
        return pages;  // Can't split without compression
    }

    // Compress the entire text first
    std::vector<uint8_t> compressedData;
    try {
        compressedData = brotliCompress(text);
    } catch (...) {
        outCompressed = false;
        return pages;
    }

    if (compressedData.empty()) {
        outCompressed = false;
        return pages;
    }

    // Base45 encode the compressed data
    std::string base45Data = base45Encode(compressedData);

    // Calculate chunk size for each QR page
    // Format: "M5:<page>/<total>/<chunk_data>"
    // Header overhead: "M5:" (3) + page digits (max 3) + "/" (1) + total digits (max 3) + "/" (1) = ~11 chars
    int headerOverhead = 12;  // conservative
    int maxCapacity = getMaxQrCapacity(ecl);
    int chunkSize = maxCapacity - headerOverhead;
    if (chunkSize < 100) chunkSize = 100;  // minimum useful chunk

    // Calculate total pages needed
    int totalPages = static_cast<int>((base45Data.size() + chunkSize - 1) / chunkSize);
    if (totalPages > 999) totalPages = 999;  // limit to 999 pages

    // Recalculate chunk size with actual total page digits
    {
        std::ostringstream headerTest;
        headerTest << "M5:" << totalPages << "/" << totalPages << "/";
        headerOverhead = static_cast<int>(headerTest.str().size());
        chunkSize = maxCapacity - headerOverhead;
        if (chunkSize < 100) chunkSize = 100;

        // Recalculate total pages with adjusted chunk size
        totalPages = static_cast<int>((base45Data.size() + chunkSize - 1) / chunkSize);
        if (totalPages > 999) totalPages = 999;
    }

    outCompressed = true;

    // Generate each page
    for (int page = 0; page < totalPages; page++) {
        int start = page * chunkSize;
        int end = (std::min)(start + chunkSize, static_cast<int>(base45Data.size()));
        std::string chunk = base45Data.substr(start, end - start);

        // Build page content: M5:<page+1>/<total>/<chunk>
        std::ostringstream content;
        content << "M5:" << (page + 1) << "/" << totalPages << "/" << chunk;

        uint8_t* qrcode = new uint8_t[qrcodegen_BUFFER_LEN_MAX];
        uint8_t* tempBuffer = new uint8_t[qrcodegen_BUFFER_LEN_MAX];
        bool ok = tryEncode(content.str(), qrcode, tempBuffer, ecl);
        if (ok) {
            HBITMAP hBmp = createQrBitmap(qrcode, pixelSize);
            if (hBmp) {
                pages.push_back({hBmp, page, totalPages});
            }
        }
        delete[] qrcode;
        delete[] tempBuffer;
    }

    return pages;
}

} // namespace qr
