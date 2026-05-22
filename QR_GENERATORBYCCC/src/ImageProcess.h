#pragma once
#include <windows.h>
#include <vector>
#include <cstdint>

namespace qr {

// Convert HBITMAP to grayscale byte array (0=black, 255=white)
std::vector<uint8_t> toGrayscale(HBITMAP hBitmap, int& outWidth, int& outHeight);

// Scale image by factor (e.g., 2.0 = 2x)
HBITMAP scaleImage(HBITMAP hSrc, float factor);

// Apply binary threshold (pixels below threshold become black, above become white)
HBITMAP applyThreshold(HBITMAP hSrc, int threshold);

// Apply Otsu automatic threshold
HBITMAP applyOtsuThreshold(HBITMAP hSrc);

// Enhance contrast (stretch around center 128)
HBITMAP enhanceContrast(HBITMAP hSrc);

// Invert colors
HBITMAP invertColors(HBITMAP hSrc);

// Remove noise (3-zone: <80 black, >180 white, else gray)
HBITMAP removeNoise(HBITMAP hSrc);

} // namespace qr
