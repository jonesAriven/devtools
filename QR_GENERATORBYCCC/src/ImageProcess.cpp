#include "ImageProcess.h"
#include <algorithm>
#include <cmath>
#include <gdiplus.h>

#pragma comment(lib, "gdiplus.lib")

namespace qr {

static HBITMAP createBitmapFromGrayscale(const std::vector<uint8_t>& gray, int width, int height) {
    BITMAPINFO bmi = {};
    bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bmi.bmiHeader.biWidth = width;
    bmi.bmiHeader.biHeight = -height; // top-down
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = 24;
    bmi.bmiHeader.biCompression = BI_RGB;

    void* bits = nullptr;
    HBITMAP hBmp = CreateDIBSection(NULL, &bmi, DIB_RGB_COLORS, &bits, NULL, 0);
    if (!hBmp) return NULL;

    // Row stride must be aligned to 4 bytes
    int stride = ((width * 3 + 3) & ~3);
    uint8_t* dst = (uint8_t*)bits;
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            uint8_t g = gray[y * width + x];
            dst[x * 3 + 0] = g; // B
            dst[x * 3 + 1] = g; // G
            dst[x * 3 + 2] = g; // R
        }
        dst += stride;
    }
    return hBmp;
}

std::vector<uint8_t> toGrayscale(HBITMAP hBitmap, int& outWidth, int& outHeight) {
    std::vector<uint8_t> result;
    if (!hBitmap) return result;

    Gdiplus::Bitmap* bitmap = Gdiplus::Bitmap::FromHBITMAP(hBitmap, NULL);
    if (!bitmap) return result;

    outWidth = bitmap->GetWidth();
    outHeight = bitmap->GetHeight();
    result.resize(outWidth * outHeight, 255);

    Gdiplus::Rect rect(0, 0, outWidth, outHeight);
    Gdiplus::BitmapData bd = {};

    // Always convert to 32bpp ARGB for consistent processing
    // This handles all source formats: 24bpp, 32bpp, 32bppPARGB, 16bpp, etc.
    if (bitmap->LockBits(&rect, Gdiplus::ImageLockModeRead, PixelFormat32bppARGB, &bd) == Gdiplus::Ok) {
        int stride = bd.Stride;
        uint8_t* src = (uint8_t*)bd.Scan0;
        for (int y = 0; y < outHeight; y++) {
            for (int x = 0; x < outWidth; x++) {
                uint8_t b = src[y * stride + x * 4 + 0];
                uint8_t g = src[y * stride + x * 4 + 1];
                uint8_t r = src[y * stride + x * 4 + 2];
                // BT.601 grayscale
                result[y * outWidth + x] = static_cast<uint8_t>(
                    0.299f * r + 0.587f * g + 0.114f * b + 0.5f);
            }
        }
        bitmap->UnlockBits(&bd);
    }

    delete bitmap;
    return result;
}

HBITMAP scaleImage(HBITMAP hSrc, float factor) {
    if (!hSrc || factor <= 0.0f) return NULL;

    Gdiplus::Bitmap* srcBitmap = Gdiplus::Bitmap::FromHBITMAP(hSrc, NULL);
    if (!srcBitmap) return NULL;

    int srcW = static_cast<int>(srcBitmap->GetWidth());
    int srcH = static_cast<int>(srcBitmap->GetHeight());
    int newW = static_cast<int>(srcW * factor + 0.5f);
    int newH = static_cast<int>(srcH * factor + 0.5f);
    if (newW < 1 || newH < 1) {
        delete srcBitmap;
        return NULL;
    }

    Gdiplus::Bitmap* dstBitmap = new Gdiplus::Bitmap(newW, newH, PixelFormat24bppRGB);
    if (!dstBitmap) {
        delete srcBitmap;
        return NULL;
    }

    Gdiplus::Graphics graphics(dstBitmap);
    graphics.SetInterpolationMode(Gdiplus::InterpolationModeHighQualityBicubic);
    graphics.DrawImage(srcBitmap, 0, 0, newW, newH);

    HBITMAP hResult = NULL;
    dstBitmap->GetHBITMAP(Gdiplus::Color(255, 255, 255), &hResult);

    delete dstBitmap;
    delete srcBitmap;
    return hResult;
}

HBITMAP applyThreshold(HBITMAP hSrc, int threshold) {
    if (!hSrc) return NULL;

    int w = 0, h = 0;
    std::vector<uint8_t> gray = toGrayscale(hSrc, w, h);
    if (gray.empty()) return NULL;

    for (size_t i = 0; i < gray.size(); i++) {
        gray[i] = (gray[i] < threshold) ? 0 : 255;
    }

    return createBitmapFromGrayscale(gray, w, h);
}

HBITMAP applyOtsuThreshold(HBITMAP hSrc) {
    if (!hSrc) return NULL;

    int w = 0, h = 0;
    std::vector<uint8_t> gray = toGrayscale(hSrc, w, h);
    if (gray.empty()) return NULL;

    // Compute histogram
    int histogram[256] = {};
    for (size_t i = 0; i < gray.size(); i++) {
        histogram[gray[i]]++;
    }

    int total = w * h;

    // Otsu's method
    float bestVariance = 0.0f;
    int bestThreshold = 0;

    float sum = 0.0f;
    for (int i = 0; i < 256; i++) {
        sum += i * histogram[i];
    }

    float sumB = 0.0f;
    int wB = 0;

    for (int t = 0; t < 256; t++) {
        wB += histogram[t];
        if (wB == 0) continue;

        int wF = total - wB;
        if (wF == 0) break;

        sumB += t * (float)histogram[t];

        float mB = sumB / wB;
        float mF = (sum - sumB) / wF;

        float betweenVariance = (float)wB * (float)wF * (mB - mF) * (mB - mF);

        if (betweenVariance > bestVariance) {
            bestVariance = betweenVariance;
            bestThreshold = t;
        }
    }

    // Apply the threshold
    for (size_t i = 0; i < gray.size(); i++) {
        gray[i] = (gray[i] < bestThreshold) ? 0 : 255;
    }

    return createBitmapFromGrayscale(gray, w, h);
}

HBITMAP enhanceContrast(HBITMAP hSrc) {
    if (!hSrc) return NULL;

    int w = 0, h = 0;
    std::vector<uint8_t> gray = toGrayscale(hSrc, w, h);
    if (gray.empty()) return NULL;

    // Double the contrast around center 128
    for (size_t i = 0; i < gray.size(); i++) {
        int val = 128 + (static_cast<int>(gray[i]) - 128) * 2;
        if (val < 0) val = 0;
        if (val > 255) val = 255;
        gray[i] = static_cast<uint8_t>(val);
    }

    return createBitmapFromGrayscale(gray, w, h);
}

HBITMAP invertColors(HBITMAP hSrc) {
    if (!hSrc) return NULL;

    int w = 0, h = 0;
    std::vector<uint8_t> gray = toGrayscale(hSrc, w, h);
    if (gray.empty()) return NULL;

    for (size_t i = 0; i < gray.size(); i++) {
        gray[i] = 255 - gray[i];
    }

    return createBitmapFromGrayscale(gray, w, h);
}

HBITMAP removeNoise(HBITMAP hSrc) {
    if (!hSrc) return NULL;

    int w = 0, h = 0;
    std::vector<uint8_t> gray = toGrayscale(hSrc, w, h);
    if (gray.empty()) return NULL;

    for (size_t i = 0; i < gray.size(); i++) {
        if (gray[i] < 80) {
            gray[i] = 0;
        } else if (gray[i] > 180) {
            gray[i] = 255;
        }
        // else keep as-is
    }

    return createBitmapFromGrayscale(gray, w, h);
}

} // namespace qr
