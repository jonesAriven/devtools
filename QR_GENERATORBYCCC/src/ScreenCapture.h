#pragma once
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>

namespace qr {

// Capture screen with overlay selection
// Shows a semi-transparent overlay, user drags to select area
// Returns HBITMAP of the selected area, or NULL if cancelled
// This function blocks until selection is complete
HBITMAP captureScreenSelection(HWND parentWindow);

} // namespace qr
