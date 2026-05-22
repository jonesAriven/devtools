#include "QrDecoder.h"
#include "ImageProcess.h"
#include "Compressor.h"

#include "ReadBarcode.h"
#include <gdiplus.h>
#include <algorithm>
#include <cstring>
#include <fstream>
#include <ctime>
#include <sstream>

#pragma comment(lib, "gdiplus.lib")

namespace qr {

static void logDec(const std::string& msg) {
    std::ofstream f("qr_debug.log", std::ios::app);
    if (f.is_open()) {
        time_t now = time(nullptr);
        struct tm t;
        localtime_s(&t, &now);
        char ts[32];
        strftime(ts, sizeof(ts), "%H:%M:%S", &t);
        f << "[" << ts << "][DEC] " << msg << std::endl;
    }
}

// Decode using ZXing with grayscale data
static DecodeResult decodeWithZXing(const std::vector<uint8_t>& gray, int width, int height, const std::string& strategyName) {
    DecodeResult result = {false, "", strategyName};

    if (gray.empty() || width <= 0 || height <= 0) return result;

    try {
        ZXing::ImageView image(gray.data(), width, height, ZXing::ImageFormat::Lum);
        ZXing::ReaderOptions options;
        options.setTryHarder(true);
        options.setTryRotate(true);
        options.setFormats(ZXing::BarcodeFormat::QRCode);

        auto barcodes = ZXing::ReadBarcodes(image, options);

        if (!barcodes.empty()) {
            result.success = true;
            // ZXing::Result::text() returns std::string in zxing-cpp v2.x
            result.text = barcodes[0].text();
            logDec("ZXing decode SUCCESS [" + strategyName + "], text_len=" + std::to_string(result.text.size()));
        } else {
            logDec("ZXing decode no barcode found [" + strategyName + "]");
        }
    } catch (const std::exception& e) {
        logDec("ZXing exception [" + strategyName + "]: " + std::string(e.what()));
    } catch (...) {
        logDec("ZXing unknown exception [" + strategyName + "]");
    }

    return result;
}

// Nearest-neighbor scale on raw grayscale data (preserves sharp edges for QR codes)
static std::vector<uint8_t> scaleGrayscale(const std::vector<uint8_t>& gray, int width, int height, float factor) {
    int newW = static_cast<int>(width * factor + 0.5f);
    int newH = static_cast<int>(height * factor + 0.5f);
    if (newW < 1 || newH < 1) return {};

    std::vector<uint8_t> out(newW * newH);
    for (int y = 0; y < newH; y++) {
        int srcY = (std::min)(static_cast<int>(y / factor), height - 1);
        for (int x = 0; x < newW; x++) {
            int srcX = (std::min)(static_cast<int>(x / factor), width - 1);
            out[y * newW + x] = gray[srcY * width + srcX];
        }
    }
    return out;
}

// Binarize grayscale: anything below threshold becomes 0, else 255
static std::vector<uint8_t> binarize(const std::vector<uint8_t>& gray, int threshold) {
    std::vector<uint8_t> out(gray.size());
    for (size_t i = 0; i < gray.size(); i++) {
        out[i] = (gray[i] < threshold) ? 0 : 255;
    }
    return out;
}

DecodeResult decodeFromBitmap(HBITMAP hBitmap) {
    if (!hBitmap) return {false, "", ""};

    logDec("=== decodeFromBitmap START (ZXing) ===");

    // Get grayscale once
    int w = 0, h = 0;
    std::vector<uint8_t> gray = toGrayscale(hBitmap, w, h);
    if (gray.empty() || w <= 0 || h <= 0) {
        logDec("toGrayscale returned empty or zero size!");
        return {false, "", ""};
    }

    {
        int nonWhite = 0;
        for (size_t i = 0; i < gray.size(); i++) {
            if (gray[i] < 250) nonWhite++;
        }
        logDec("Grayscale: " + std::to_string(w) + "x" + std::to_string(h) + ", nonWhite=" + std::to_string(nonWhite) + "/" + std::to_string(gray.size()));
    }

    // Strategy 0: Direct grayscale (ZXing has HybridBinarizer built-in)
    {
        DecodeResult r = decodeWithZXing(gray, w, h, "Direct");
        if (r.success) return r;
    }

    // Strategy 1: Binarize + nearest-neighbor scale
    {
        int thresholds[] = {128, 100, 150, 80, 170};
        float scales[] = {2.0f, 3.0f, 4.0f, 5.0f};
        for (int t : thresholds) {
            std::vector<uint8_t> binary = binarize(gray, t);
            DecodeResult r = decodeWithZXing(binary, w, h, "Binary t=" + std::to_string(t));
            if (r.success) return r;
            for (float s : scales) {
                std::vector<uint8_t> scaled = scaleGrayscale(binary, w, h, s);
                if (!scaled.empty()) {
                    int sw = static_cast<int>(w * s + 0.5f);
                    int sh = static_cast<int>(h * s + 0.5f);
                    r = decodeWithZXing(scaled, sw, sh, "Binary t=" + std::to_string(t) + " Scale " + std::to_string((int)s) + "x");
                    if (r.success) return r;
                }
            }
        }
    }

    // Strategy 2: Nearest-neighbor scale (no binarization)
    {
        float scales[] = {2.0f, 3.0f, 4.0f, 5.0f};
        for (float s : scales) {
            std::vector<uint8_t> scaled = scaleGrayscale(gray, w, h, s);
            if (!scaled.empty()) {
                int sw = static_cast<int>(w * s + 0.5f);
                int sh = static_cast<int>(h * s + 0.5f);
                DecodeResult r = decodeWithZXing(scaled, sw, sh, "NNScale " + std::to_string((int)s) + "x");
                if (r.success) return r;
            }
        }
    }

    // Strategy 3: Invert + binarize + scale (for white-on-black QR codes)
    {
        std::vector<uint8_t> inverted(gray.size());
        for (size_t i = 0; i < gray.size(); i++) inverted[i] = 255 - gray[i];
        std::vector<uint8_t> binary = binarize(inverted, 128);
        DecodeResult r = decodeWithZXing(binary, w, h, "Invert+Binary");
        if (r.success) return r;
        for (float s : {3.0f, 4.0f, 5.0f}) {
            std::vector<uint8_t> scaled = scaleGrayscale(binary, w, h, s);
            if (!scaled.empty()) {
                int sw = static_cast<int>(w * s + 0.5f);
                int sh = static_cast<int>(h * s + 0.5f);
                r = decodeWithZXing(scaled, sw, sh, "Invert+Binary+Scale " + std::to_string((int)s) + "x");
                if (r.success) return r;
            }
        }
    }

    logDec("All strategies FAILED");
    return {false, "", ""};
}

DecodeResult decodeFromFile(const wchar_t* filePath) {
    if (!filePath) return {false, "", ""};

    Gdiplus::Bitmap* bitmap = Gdiplus::Bitmap::FromFile(filePath);
    if (!bitmap) return {false, "", ""};

    HBITMAP hBmp = NULL;
    Gdiplus::Color bgColor(255, 255, 255);
    if (bitmap->GetHBITMAP(bgColor, &hBmp) != Gdiplus::Ok) {
        delete bitmap;
        return {false, "", ""};
    }

    DecodeResult result = decodeFromBitmap(hBmp);
    DeleteObject(hBmp);
    delete bitmap;
    return result;
}

} // namespace qr
